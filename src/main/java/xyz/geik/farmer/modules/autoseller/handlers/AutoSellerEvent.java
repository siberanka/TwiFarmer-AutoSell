package xyz.geik.farmer.modules.autoseller.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.api.handlers.FarmerItemCollectEvent;
import xyz.geik.farmer.api.handlers.FarmerItemSellEvent;
import xyz.geik.farmer.api.handlers.FarmerStorageFullEvent;
import xyz.geik.farmer.model.Farmer;
import xyz.geik.farmer.model.inventory.FarmerItem;
import xyz.geik.farmer.modules.autoseller.AutoSeller;
import xyz.geik.farmer.modules.autoseller.configuration.ConfigFile;
import xyz.geik.glib.shades.xseries.XMaterial;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/** Paper/Folia-safe automatic sale coordinator. */
public final class AutoSellerEvent implements Listener {

    private final ConcurrentHashMap<SaleKey, PendingSale> pendingSales = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OwnerCacheEntry> ownerCache = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onAutoSellerEvent(@NotNull FarmerStorageFullEvent event) {
        AutoSeller module = AutoSeller.getInstance();
        if (!accepting.get() || module == null || !module.isOperational()) {
            return;
        }

        Farmer farmer = event.getFarmer();
        if (farmer == null || !farmer.getAttributeStatus("autoseller") || event.getItemSpawnEvent() == null
                || event.getItem() == null || event.getLeftAmount() <= 0) {
            audit("Rejected malformed storage-full event");
            return;
        }

        XMaterial material;
        FarmerItem farmerItem;
        OfflinePlayer owner;
        try {
            material = XMaterial.matchXMaterial(event.getItem());
            farmerItem = farmer.getInv().getStockedItem(material);
            owner = resolveOwner(farmer, event.getItemSpawnEvent().getLocation());
        } catch (RuntimeException exception) {
            audit("Rejected unresolved farmer/item state: " + exception.getClass().getSimpleName());
            return;
        }

        if (farmerItem == null || owner == null || !Double.isFinite(farmerItem.getPrice()) || farmerItem.getPrice() <= 0
                || !isAllowed(module, farmerItem) || !hasPermission(module, farmer, owner)) {
            return;
        }

        ConfigFile.OptimizeModule optimize = module.getConfigFile().getOptimizeModule();
        if (optimize.isEnable() && enqueue(event, farmer, material, owner, optimize)) {
            // The excess amount has server-side ownership in the pending batch now.
            // Cancel both layers so the same physical entity cannot also enter the world.
            event.setCancelled(true);
            event.getItemSpawnEvent().setCancelled(true);
            return;
        }

        processImmediately(event, farmer, farmerItem, owner);
    }

    private void processImmediately(FarmerStorageFullEvent source, Farmer farmer, FarmerItem item, OfflinePlayer owner) {
        ReentrantLock lock = FarmerOperationLocks.forFarmer(farmer.getId());
        if (!lock.tryLock()) {
            audit("Rejected concurrent sale for farmer " + farmer.getId());
            return;
        }
        try {
            if (!farmer.getAttributeStatus("autoseller") || item.getAmount() <= 0 || item.getPrice() <= 0) {
                return;
            }
            Bukkit.getPluginManager().callEvent(new FarmerItemSellEvent(farmer, item, owner));
            if (item.getAmount() != 0) {
                audit("Sale result verification failed for farmer " + farmer.getId());
                return;
            }
            Bukkit.getPluginManager().callEvent(new FarmerItemCollectEvent(
                    farmer, source.getItem(), source.getLeftAmount(), source.getItemSpawnEvent()));
        } catch (RuntimeException exception) {
            audit("Sale failed closed for farmer " + farmer.getId() + ": " + exception.getClass().getSimpleName());
        } finally {
            lock.unlock();
        }
    }

    private boolean enqueue(FarmerStorageFullEvent event, Farmer farmer, XMaterial material,
                            OfflinePlayer owner, ConfigFile.OptimizeModule optimize) {
        long amount = event.getLeftAmount();
        if (amount <= 0 || amount > optimize.getMaxBatchAmount()) {
            audit("Batch amount outside configured bounds: " + amount);
            return false;
        }

        SaleKey key = new SaleKey(farmer.getId(), material);
        PendingSale existing = pendingSales.get(key);
        if (existing != null) {
            return existing.add(amount, optimize.getMaxBatchAmount());
        }
        if (pendingSales.size() >= optimize.getMaxPendingBatches()) {
            audit("Pending batch limit reached; using immediate safe path");
            return false;
        }

        PendingSale created = new PendingSale(key, farmer, material, owner,
                event.getItemSpawnEvent().getLocation().clone(), amount);
        PendingSale raced = pendingSales.putIfAbsent(key, created);
        if (raced != null) {
            return raced.add(amount, optimize.getMaxBatchAmount());
        }

        try {
            Bukkit.getRegionScheduler().runDelayed(Main.getInstance(), created.location,
                    task -> drain(created), optimize.getProcessingDelayTicks());
            return true;
        } catch (RuntimeException exception) {
            pendingSales.remove(key, created);
            audit("Could not schedule region-owned sale batch: " + exception.getClass().getSimpleName());
            return false;
        }
    }

    private void drain(PendingSale pending) {
        long amount = pending.closeAndGet();
        if (amount < 0 || !pendingSales.remove(pending.key, pending)) {
            return;
        }
        ReentrantLock lock = FarmerOperationLocks.forFarmer(pending.farmer.getId());
        lock.lock();
        try {
            FarmerItem item = pending.farmer.getInv().getStockedItem(pending.material);
            if (amount <= 0 || !pending.farmer.getAttributeStatus("autoseller") || item.getPrice() <= 0) {
                audit("Pending batch failed validation for farmer " + pending.farmer.getId());
                return;
            }
            pending.farmer.getInv().forceSumItem(pending.material, amount);
            Bukkit.getPluginManager().callEvent(new FarmerItemSellEvent(pending.farmer, item, pending.owner));
            if (item.getAmount() != 0) {
                audit("Batched sale result verification failed for farmer " + pending.farmer.getId());
            }
        } catch (RuntimeException exception) {
            audit("Batched sale failed for farmer " + pending.farmer.getId() + ": " + exception.getClass().getSimpleName());
        } finally {
            lock.unlock();
        }
    }

    private OfflinePlayer resolveOwner(Farmer farmer, Location location) {
        ConfigFile.OptimizeModule optimize = AutoSeller.getInstance().getConfigFile().getOptimizeModule();
        if (!optimize.isEnable()) {
            UUID owner = Main.getIntegration().getOwnerUUID(location);
            return owner == null ? null : Bukkit.getOfflinePlayer(owner);
        }
        String cacheKey = farmer.getRegionID();
        long now = System.nanoTime();
        OwnerCacheEntry cached = ownerCache.get(cacheKey);
        if (cached != null && cached.expiresAt > now) {
            return Bukkit.getOfflinePlayer(cached.owner);
        }
        UUID owner = Main.getIntegration().getOwnerUUID(location);
        if (owner == null) {
            return null;
        }
        ownerCache.put(cacheKey, new OwnerCacheEntry(owner,
                now + optimize.getOwnerCacheSeconds() * 1_000_000_000L));
        return Bukkit.getOfflinePlayer(owner);
    }

    private static boolean hasPermission(AutoSeller module, Farmer farmer, OfflinePlayer owner) {
        if (module.isDefaultStatus() || !owner.isOnline()) {
            return true;
        }
        if (owner.getPlayer() != null && owner.getPlayer().hasPermission(module.getCustomPerm())) {
            return true;
        }
        farmer.changeAttribute("autoseller");
        return false;
    }

    private static boolean isAllowed(AutoSeller module, FarmerItem item) {
        return module.getAllowedItems().isEmpty()
                || module.getAllowedItems().contains(item.getName().toUpperCase(Locale.ROOT));
    }

    public void cleanupExpired(long now) {
        ownerCache.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    public void shutdown() {
        accepting.set(false);
        ownerCache.clear();
        // Already accepted region tasks are intentionally allowed to drain. Dropping
        // them here would destroy items whose spawn event was safely cancelled.
    }

    private static void audit(String message) {
        AutoSeller module = AutoSeller.getInstance();
        if (module != null && module.getConfigFile() != null
                && module.getConfigFile().getOptimizeModule().isEnable()
                && module.getConfigFile().getOptimizeModule().isAuditRejectedOperations()) {
            Main.getInstance().getLogger().warning("[AutoSeller audit] " + message);
        }
    }

    private static final class SaleKey {
        private final int farmerId;
        private final XMaterial material;

        private SaleKey(int farmerId, XMaterial material) {
            this.farmerId = farmerId;
            this.material = material;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SaleKey)) return false;
            SaleKey key = (SaleKey) other;
            return farmerId == key.farmerId && material == key.material;
        }

        @Override public int hashCode() {
            return Objects.hash(farmerId, material);
        }
    }

    private static final class PendingSale {
        private final SaleKey key;
        private final Farmer farmer;
        private final XMaterial material;
        private final OfflinePlayer owner;
        private final Location location;
        private final BoundedBatchCounter counter;

        private PendingSale(SaleKey key, Farmer farmer, XMaterial material, OfflinePlayer owner,
                            Location location, long amount) {
            this.key = key;
            this.farmer = farmer;
            this.material = material;
            this.owner = owner;
            this.location = location;
            this.counter = new BoundedBatchCounter(amount);
        }

        private boolean add(long addition, long maximum) {
            return counter.add(addition, maximum);
        }

        private long closeAndGet() {
            return counter.closeAndGet();
        }
    }

    private static final class OwnerCacheEntry {
        private final UUID owner;
        private final long expiresAt;

        private OwnerCacheEntry(UUID owner, long expiresAt) {
            this.owner = owner;
            this.expiresAt = expiresAt;
        }
    }
}
