package xyz.geik.farmer.modules.autoseller.handlers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.geik.farmer.api.handlers.FarmerModuleGuiCreateEvent;
import xyz.geik.farmer.helpers.gui.GuiHelper;
import xyz.geik.farmer.model.Farmer;
import xyz.geik.farmer.modules.autoseller.AutoSeller;
import xyz.geik.glib.chat.ChatUtils;
import xyz.geik.glib.shades.inventorygui.DynamicGuiElement;
import xyz.geik.glib.shades.inventorygui.StaticGuiElement;

import java.util.stream.Collectors;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Auto Seller Gui listener and events
 */
public class AutoSellerGuiCreateEvent implements Listener {

    private final ConcurrentHashMap<UUID, Long> lastClick = new ConcurrentHashMap<>();

    /**
     * Constructor of class
     */
    public AutoSellerGuiCreateEvent() {}

    /**
     * Creates the GUI element for the module and adds it to the GUI
     *
     * @param e of module gui create event
     */
    @EventHandler
    public void onGuiCreateEvent(@NotNull FarmerModuleGuiCreateEvent e) {
        AutoSeller module = AutoSeller.getInstance();
        if (module == null || !module.isOperational()) return;
        String interfaceValue = module.getLang().getString("moduleGui.icon.guiInterface");
        if (interfaceValue == null || interfaceValue.isEmpty()) return;
        char icon = interfaceValue.charAt(0);
        e.getGui().addElement(
                new DynamicGuiElement(icon, (viewer) ->
                        new StaticGuiElement(
                                icon,
                                // Item here
                                getGuiItem(e.getFarmer()),
                                1,
                                // Event written bottom
                                click -> {
                                    AutoSeller activeModule = AutoSeller.getInstance();
                                    if (activeModule == null || !activeModule.isOperational()) return true;
                                    // If player don't have permission do nothing
                                    if (!e.getPlayer().hasPermission(activeModule.getCustomPerm()))
                                        return true;
                                    if (!allowClick(e.getPlayer().getUniqueId(), activeModule)) return true;
                                    ReentrantLock lock = FarmerOperationLocks.forFarmer(e.getFarmer().getId());
                                    if (!lock.tryLock()) return true;
                                    try {
                                        e.getFarmer().changeAttribute("autoseller");
                                        e.getGui().draw();
                                    } finally {
                                        lock.unlock();
                                    }
                                    return true;
                                })
                )
        );
    }

    /**
     * Get the item for the GUI
     *
     * @param farmer of region
     * @return item stack of module gui
     */
    private @NotNull ItemStack getGuiItem(@NotNull Farmer farmer) {
        ItemStack item = GuiHelper.getItem("moduleGui.icon", AutoSeller.getInstance().getLang());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String status = farmer.getAttributeStatus("autoseller") ?
                AutoSeller.getInstance().getLang().getString("enabled") :
                AutoSeller.getInstance().getLang().getString("disabled");
        List<String> lore = meta.getLore() == null ? Collections.emptyList() : meta.getLore();
        meta.setLore(lore.stream().map(line -> ChatUtils.color(line.replace("{status}", status)))
                .collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    private boolean allowClick(UUID playerId, AutoSeller module) {
        if (!module.getConfigFile().getOptimizeModule().isEnable()) return true;
        long now = System.nanoTime();
        long cooldown = module.getConfigFile().getOptimizeModule().getGuiClickCooldownMillis() * 1_000_000L;
        Long previous = lastClick.put(playerId, now);
        return previous == null || now - previous >= cooldown;
    }

    public void cleanupExpired(long now) {
        AutoSeller module = AutoSeller.getInstance();
        if (module == null || module.getConfigFile() == null) return;
        long expiry = Math.max(1_000_000_000L,
                module.getConfigFile().getOptimizeModule().getGuiClickCooldownMillis() * 4_000_000L);
        lastClick.entrySet().removeIf(entry -> now - entry.getValue() >= expiry);
    }

    public void shutdown() {
        lastClick.clear();
    }
}
