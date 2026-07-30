package xyz.geik.farmer.modules.autoseller.configuration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationRepairServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void replacesMalformedConfigAfterBackupAndInstallsLanguages() throws Exception {
        Path config = temporaryDirectory.resolve("config.yml");
        Files.writeString(config, "status: [broken\n", StandardCharsets.UTF_8);

        new ConfigurationRepairService(pluginStub(), temporaryDirectory.toFile()).repairAll();

        YamlConfiguration repaired = YamlConfiguration.loadConfiguration(config.toFile());
        assertFalse(repaired.getBoolean("status"));
        assertEquals(1, repaired.getInt("required-farmer-level"));
        assertEquals("farmer.autoseller", repaired.getString("customPerm"));
        assertEquals(2, repaired.getInt("optimize-module.processingDelayTicks"));
        assertTrue(repaired.getBoolean("update-checker.enable"));
        assertEquals(6, repaired.getInt("update-checker.check-interval-hours"));
        assertTrue(hasBackup(config.toFile()));
        assertTrue(temporaryDirectory.resolve("lang/en.yml").toFile().isFile());
        assertTrue(temporaryDirectory.resolve("lang/tr.yml").toFile().isFile());
        assertTrue(temporaryDirectory.resolve("lang/de.yml").toFile().isFile());
    }

    @Test
    void repairsMissingMeaninglessAndBrokenEncodingLanguageEntries() throws Exception {
        Path languageDirectory = Files.createDirectories(temporaryDirectory.resolve("lang"));
        Path turkish = languageDirectory.resolve("tr.yml");
        Files.writeString(turkish,
                "enabled: ''\ndisabled: '&cDevre dÄ±ÅŸÄ±'\nmoduleGui:\n  icon:\n    guiInterface: 'too-long'\n",
                StandardCharsets.UTF_8);

        new ConfigurationRepairService(pluginStub(), temporaryDirectory.toFile()).repairAll();

        YamlConfiguration repaired = YamlConfiguration.loadConfiguration(turkish.toFile());
        assertEquals("&aAktif", repaired.getString("enabled"));
        assertEquals("&cDevre dışı", repaired.getString("disabled"));
        assertEquals("s", repaired.getString("moduleGui.icon.guiInterface"));
        assertEquals("Otomatik Satış", repaired.getString("module-name"));
        assertTrue(repaired.getString("level-required").contains("{required_level}"));
        assertTrue(repaired.getString("level-required").contains("{current_level}"));
        assertTrue(repaired.getString("update.available").contains("{module}"));
        assertTrue(repaired.getString("update.available").contains("{url}"));
        assertTrue(repaired.getStringList("moduleGui.icon.lore").stream().anyMatch(line -> line.contains("{status}")));
        assertTrue(repaired.getStringList("moduleGui.icon.lore").stream()
                .anyMatch(line -> line.contains("{required_level}")));
        assertTrue(repaired.getStringList("moduleGui.icon.lore").stream().anyMatch(line -> line.contains("{action}")));
        assertTrue(hasBackup(turkish.toFile()));
    }

    @Test
    void repairsInvalidRequiredFarmerLevelWithoutDiscardingCustomEntries() throws Exception {
        Path config = temporaryDirectory.resolve("config.yml");
        Files.writeString(config, """
                status: true
                required-farmer-level: 0
                custom-entry: preserved
                """, StandardCharsets.UTF_8);

        new ConfigurationRepairService(pluginStub(), temporaryDirectory.toFile()).repairAll();

        YamlConfiguration repaired = YamlConfiguration.loadConfiguration(config.toFile());
        assertTrue(repaired.getBoolean("status"));
        assertEquals(1, repaired.getInt("required-farmer-level"));
        assertEquals("preserved", repaired.getString("custom-entry"));
        assertTrue(hasBackup(config.toFile()));
    }

    private static boolean hasBackup(File original) {
        File[] files = original.getParentFile().listFiles((directory, name) -> name.startsWith(original.getName() + ".bak-"));
        return files != null && files.length > 0;
    }

    private static Plugin pluginStub() {
        Logger logger = Logger.getLogger("ConfigurationRepairServiceTest");
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getLogger")) return logger;
                    if (method.getName().equals("isEnabled")) return true;
                    if (method.getName().equals("getName")) return "Farmer";
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
    }
}
