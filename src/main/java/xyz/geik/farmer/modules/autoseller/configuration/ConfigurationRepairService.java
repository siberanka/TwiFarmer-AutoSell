package xyz.geik.farmer.modules.autoseller.configuration;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import xyz.geik.farmer.modules.autoseller.AutoSeller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Repairs module YAML before Okaeri/GLib reads it. Invalid user files are always
 * backed up first; missing keys are merged without discarding valid custom values.
 */
public final class ConfigurationRepairService {

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final List<String> LANGUAGES = Arrays.asList("en", "tr", "de");

    private final Plugin plugin;
    private final File moduleDirectory;

    public ConfigurationRepairService(Plugin plugin, File moduleDirectory) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.moduleDirectory = Objects.requireNonNull(moduleDirectory, "moduleDirectory");
    }

    public void repairAll() {
        ensureDirectory(moduleDirectory);
        repairConfig(new File(moduleDirectory, "config.yml"));
        File langDirectory = new File(moduleDirectory, "lang");
        ensureDirectory(langDirectory);
        for (String language : LANGUAGES) {
            repairLanguage(language, new File(langDirectory, language + ".yml"));
        }
    }

    private void repairConfig(File file) {
        if (!file.exists()) {
            return; // Okaeri writes the commented default after this preflight.
        }

        YamlConfiguration yaml = loadOrReset(file, null);
        boolean invalid = false;
        invalid |= repair(yaml, "status", false, value -> value instanceof Boolean);
        invalid |= repair(yaml, "defaultStatus", false, value -> value instanceof Boolean);
        invalid |= repair(yaml, "customPerm", "farmer.autoseller", value -> validPermission(value));
        invalid |= repair(yaml, "items", List.of(), ConfigurationRepairService::validItemList);
        invalid |= repair(yaml, "update-checker.enable", true, value -> value instanceof Boolean);
        invalid |= repairNumber(yaml, "update-checker.check-interval-hours", 6, 1, 168);
        invalid |= repairNumber(yaml, "update-checker.connect-timeout-seconds", 5, 2, 30);
        invalid |= repairNumber(yaml, "update-checker.request-timeout-seconds", 8, 3, 60);
        invalid |= repair(yaml, "optimize-module.enable", false, value -> value instanceof Boolean);
        invalid |= repairNumber(yaml, "optimize-module.processingDelayTicks", 2, 1, 1200);
        invalid |= repairNumber(yaml, "optimize-module.maxPendingBatches", 4096, 16, 100000);
        invalid |= repairNumber(yaml, "optimize-module.maxBatchAmount", 1000000000L, 1, Long.MAX_VALUE);
        invalid |= repairNumber(yaml, "optimize-module.ownerCacheSeconds", 60, 1, 3600);
        invalid |= repairNumber(yaml, "optimize-module.guiClickCooldownMillis", 250, 0, 10000);
        invalid |= repairNumber(yaml, "optimize-module.cleanupIntervalSeconds", 60, 5, 3600);
        invalid |= repair(yaml, "optimize-module.auditRejectedOperations", true, value -> value instanceof Boolean);
        saveIfChanged(file, yaml, invalid);
    }

    private void repairLanguage(String language, File file) {
        YamlConfiguration defaults = loadResource("autoseller/lang/" + language + ".yml");
        if (!file.exists()) {
            copyResource("autoseller/lang/" + language + ".yml", file);
            return;
        }

        YamlConfiguration yaml = loadOrReset(file, defaults);
        boolean invalid = false;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) {
                continue;
            }
            Object expected = defaults.get(path);
            Object actual = yaml.get(path);
            boolean valid = sameShape(expected, actual) && !containsBrokenEncoding(actual);
            if (expected instanceof String && !((String) expected).isEmpty()) {
                valid &= actual instanceof String && !((String) actual).trim().isEmpty();
            }
            if ("moduleGui.icon.guiInterface".equals(path)) {
                valid &= actual instanceof String && ((String) actual).codePointCount(0, ((String) actual).length()) == 1;
            }
            if ("moduleGui.icon.lore".equals(path)) {
                valid &= validLore(actual);
            }
            if ("update.available".equals(path)) {
                valid &= validUpdateMessage(actual);
            }
            if (!valid) {
                yaml.set(path, expected);
                invalid = true;
            }
        }
        saveIfChanged(file, yaml, invalid);
    }

    private YamlConfiguration loadOrReset(File file, YamlConfiguration defaults) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            backup(file);
            plugin.getLogger().warning("[AutoSeller audit] Invalid YAML replaced after backup: " + file.getName());
            if (defaults != null) {
                try {
                    defaults.save(file);
                } catch (IOException saveException) {
                    throw new IllegalStateException("Cannot replace malformed YAML " + file, saveException);
                }
                return defaults;
            }
            return new YamlConfiguration();
        }
    }

    private boolean repair(YamlConfiguration yaml, String path, Object fallback, Predicate<Object> validator) {
        Object value = yaml.get(path);
        if (validator.test(value)) {
            return false;
        }
        yaml.set(path, fallback);
        return true;
    }

    private boolean repairNumber(YamlConfiguration yaml, String path, Number fallback, long minimum, long maximum) {
        Object value = yaml.get(path);
        if (value instanceof Number) {
            long number = ((Number) value).longValue();
            if (number >= minimum && number <= maximum) {
                return false;
            }
        }
        yaml.set(path, fallback);
        return true;
    }

    private void saveIfChanged(File file, YamlConfiguration yaml, boolean changed) {
        if (!changed) {
            return;
        }
        backup(file);
        try {
            yaml.save(file);
            plugin.getLogger().warning("[AutoSeller audit] Repaired invalid or missing YAML entries: " + file.getName());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save repaired YAML " + file, exception);
        }
    }

    private void backup(File file) {
        if (!file.exists()) {
            return;
        }
        File backup = new File(file.getParentFile(), file.getName() + ".bak-" + BACKUP_TIME.format(LocalDateTime.now()));
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot back up invalid YAML " + file, exception);
        }
    }

    private YamlConfiguration loadResource(String path) {
        try (InputStream stream = AutoSeller.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled resource " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load bundled resource " + path, exception);
        }
    }

    private void copyResource(String path, File target) {
        try (InputStream stream = AutoSeller.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled resource " + path);
            }
            Files.copy(stream, target.toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot install language file " + target, exception);
        }
    }

    private static boolean validPermission(Object value) {
        return value instanceof String && ((String) value).matches("[a-z0-9_.-]{1,128}");
    }

    private static boolean validItemList(Object value) {
        if (!(value instanceof List<?>)) {
            return false;
        }
        for (Object entry : (List<?>) value) {
            if (!(entry instanceof String) || !((String) entry).matches("[A-Za-z0-9_:-]{1,128}")) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameShape(Object expected, Object actual) {
        if (expected instanceof List<?>) {
            return actual instanceof List<?>;
        }
        return expected != null && actual != null && expected.getClass().isInstance(actual);
    }

    private static boolean validLore(Object value) {
        if (!(value instanceof List<?>)) {
            return false;
        }
        boolean hasStatus = false;
        for (Object line : (List<?>) value) {
            if (!(line instanceof String)) {
                return false;
            }
            hasStatus |= ((String) line).contains("{status}");
        }
        return hasStatus;
    }

    private static boolean validUpdateMessage(Object value) {
        if (!(value instanceof String) || ((String) value).length() > 1024) {
            return false;
        }
        String message = (String) value;
        return !message.trim().isEmpty()
                && message.contains("{module}")
                && message.contains("{current}")
                && message.contains("{latest}")
                && message.contains("{url}");
    }

    private static boolean containsBrokenEncoding(Object value) {
        if (value instanceof String) {
            String text = (String) value;
            return text.contains("Ã") || text.contains("Ä") || text.contains("Å") || text.contains("â€");
        }
        if (value instanceof List<?>) {
            return ((List<?>) value).stream().anyMatch(ConfigurationRepairService::containsBrokenEncoding);
        }
        return false;
    }

    private static void ensureDirectory(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create directory " + directory);
        }
    }
}
