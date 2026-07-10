package xyz.geik.farmer.modules.autoseller;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.modules.FarmerModule;
import xyz.geik.farmer.modules.autoseller.configuration.ConfigFile;
import xyz.geik.farmer.modules.autoseller.configuration.ConfigurationRepairService;
import xyz.geik.farmer.modules.autoseller.handlers.AutoSellerEvent;
import xyz.geik.farmer.modules.autoseller.handlers.AutoSellerGuiCreateEvent;
import xyz.geik.glib.GLib;
import xyz.geik.glib.chat.ChatUtils;
import xyz.geik.glib.shades.okaeri.configs.ConfigManager;
import xyz.geik.glib.shades.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * AutoSeller module main class
 * @author Geik
 * @author siberanka
 */
@Getter
public class AutoSeller extends FarmerModule {

    public AutoSeller() {}

    @Getter
    private static AutoSeller instance;

    private static AutoSellerEvent autoSellerEvent;

    private static AutoSellerGuiCreateEvent autoSellerGuiCreateEvent;

    private volatile Set<String> allowedItems = Collections.emptySet();

    private String customPerm = "farmer.autoseller";

    private boolean defaultStatus = false;

    private ConfigFile configFile;

    private ScheduledTask cleanupTask;

    private volatile boolean operational;

    /**
     * onEnable method of module
     */
    @Override
    public void onEnable() {
        instance = this;
        operational = false;
        this.setHasGui(false);
        if (!isPaperRuntime()) {
            operational = false;
            ChatUtils.sendMessage(Bukkit.getConsoleSender(), "&3[AutoSeller] &cPaper is required; plain Bukkit/Spigot is unsupported.");
            return;
        }

        File moduleDirectory = getModuleDirectory();
        new ConfigurationRepairService(Main.getInstance(), moduleDirectory).repairAll();
        this.setLang(Main.getConfigFile().getSettings().getLang(), this.getClass());
        setupFile();

        if (configFile.isStatus()) {
            operational = true;
            this.setHasGui(true);
            autoSellerEvent = new AutoSellerEvent();
            autoSellerGuiCreateEvent = new AutoSellerGuiCreateEvent();
            Bukkit.getPluginManager().registerEvents(autoSellerEvent, Main.getInstance());
            Bukkit.getPluginManager().registerEvents(autoSellerGuiCreateEvent, Main.getInstance());
            applyConfigSnapshot();
            startMaintenance();
            String messagex = "&3[" + GLib.getInstance().getName() + "] &a" + getName() + " enabled.";
            ChatUtils.sendMessage(Bukkit.getConsoleSender(), messagex);
        }
        else {
            String messagex = "&3[" + GLib.getInstance().getName() + "] &c" + getName() + " is not loaded.";
            ChatUtils.sendMessage(Bukkit.getConsoleSender(), messagex);
        }
    }

    /**
     * onReload method of module
     */
    @Override
    public void onReload() {
        onDisable();
        onEnable();
    }

    /**
     * onDisable method of module
     */
    @Override
    public void onDisable() {
        operational = false;
        this.setHasGui(false);
        stopMaintenance();
        if (autoSellerEvent != null) {
            autoSellerEvent.shutdown();
            HandlerList.unregisterAll(autoSellerEvent);
            autoSellerEvent = null;
        }
        if (autoSellerGuiCreateEvent != null) {
            autoSellerGuiCreateEvent.shutdown();
            HandlerList.unregisterAll(autoSellerGuiCreateEvent);
            autoSellerGuiCreateEvent = null;
        }
        allowedItems = Collections.emptySet();
    }

    public void setupFile() {
        configFile = ConfigManager.create(ConfigFile.class, (it) -> {
            it.withConfigurer(new YamlBukkitConfigurer());
            it.withBindFile(new File(Main.getInstance().getDataFolder(), String.format("/modules/%s/config.yml", getName().toLowerCase())));
            it.saveDefaults();
            it.load(true);
        });
    }

    private void applyConfigSnapshot() {
        Set<String> configuredItems = new HashSet<>();
        for (String item : configFile.getItems()) {
            configuredItems.add(item.toUpperCase(java.util.Locale.ROOT));
        }
        allowedItems = Collections.unmodifiableSet(configuredItems);
        customPerm = configFile.getCustomPerm();
        defaultStatus = configFile.isDefaultStatus();
        setDefaultState(defaultStatus);
    }

    private void startMaintenance() {
        ConfigFile.OptimizeModule optimize = configFile.getOptimizeModule();
        if (!optimize.isEnable()) {
            return;
        }
        cleanupTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                Main.getInstance(),
                task -> {
                    long now = System.nanoTime();
                    AutoSellerEvent event = autoSellerEvent;
                    AutoSellerGuiCreateEvent guiEvent = autoSellerGuiCreateEvent;
                    if (event != null) event.cleanupExpired(now);
                    if (guiEvent != null) guiEvent.cleanupExpired(now);
                },
                optimize.getCleanupIntervalSeconds(),
                optimize.getCleanupIntervalSeconds(),
                TimeUnit.SECONDS);
    }

    private void stopMaintenance() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    private File getModuleDirectory() {
        return new File(Main.getInstance().getDataFolder(), "modules/" + getName().toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean isPaperRuntime() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler", false, AutoSeller.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
