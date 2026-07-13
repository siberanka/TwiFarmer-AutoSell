package xyz.geik.farmer.modules.autoseller.configuration;

import lombok.Getter;
import lombok.Setter;
import xyz.geik.glib.shades.okaeri.configs.OkaeriConfig;
import xyz.geik.glib.shades.okaeri.configs.annotation.Comment;
import xyz.geik.glib.shades.okaeri.configs.annotation.CustomKey;
import xyz.geik.glib.shades.okaeri.configs.annotation.NameStrategy;
import xyz.geik.glib.shades.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.List;

/**
 * Modules file
 *
 * @author geik
 * @since 2.0
 */
@Getter
@Setter
@Names(strategy = NameStrategy.IDENTITY)
public class ConfigFile extends OkaeriConfig {

    @Comment({"if you want to enable it, set this to true",
            "this setting will enable the auto-seller",
            "and players with farmer.admin permission can give auto seller.",
            "you can disable buy feature and give farmer with command"})
    private boolean status = false;

    @Comment({"if you want to enable the auto-seller for all players, set this to true",
            "if you want to enable the auto-seller for specific players, set this to false (perm-check)",
            "which replace farmer level same as auto seller level when auto seller level",
            "is higher than farmer level."})
    private boolean defaultStatus = false;

    @Comment({"custom perm only used if defaultStatus is false",
            "only players with this perm will be able to use the auto-seller"})
    private String customPerm = "farmer.autoseller";

    @Comment({"all the items that will be sold by the auto-seller",
            "you can add as many items as you want",
            "the items must be same as the ones in the items.yml of the Farmer",
            "you can also remove this section for enable it to all items"})
    private List<String> items = new ArrayList<>();

    @CustomKey("update-checker")
    @Comment("Asynchronously checks the fixed AutoSeller GitHub repository for stable releases.")
    private UpdateCheckerSettings updateChecker = new UpdateCheckerSettings();

    @CustomKey("optimize-module")
    @Comment({"Production optimization settings.",
            "Every setting in this section is ignored while enable is false.",
            "Paper region/entity schedulers are used for all delayed server access."})
    private OptimizeModule optimizeModule = new OptimizeModule();

    @Getter
    @Setter
    @Names(strategy = NameStrategy.IDENTITY)
    public static class OptimizeModule extends OkaeriConfig {

        @Comment("Master switch. Disabled by default to preserve legacy behavior.")
        private boolean enable = false;

        @Comment({"Delay before a queued sale batch is processed.",
                "Higher values merge more capacity events into fewer economy calls."})
        private int processingDelayTicks = 2;

        @Comment("Maximum number of pending farmer/material batches before fail-closed fallback.")
        private int maxPendingBatches = 4096;

        @Comment("Maximum amount accepted into one batch; protects against malformed upstream values.")
        private long maxBatchAmount = 1000000000L;

        @Comment("How long region owner lookups are cached.")
        private int ownerCacheSeconds = 60;

        @Comment("Minimum interval between module GUI toggles from the same player.")
        private int guiClickCooldownMillis = 250;

        @Comment("Interval for async cleanup of expired cache and rate-limit entries.")
        private int cleanupIntervalSeconds = 60;

        @Comment("Write rejected or malformed operations to the server audit log.")
        private boolean auditRejectedOperations = true;
    }

    @Getter
    @Setter
    @Names(strategy = NameStrategy.IDENTITY)
    public static class UpdateCheckerSettings extends OkaeriConfig {

        private boolean enable = true;

        @CustomKey("check-interval-hours")
        private int checkIntervalHours = 6;

        @CustomKey("connect-timeout-seconds")
        private int connectTimeoutSeconds = 5;

        @CustomKey("request-timeout-seconds")
        private int requestTimeoutSeconds = 8;
    }

}
