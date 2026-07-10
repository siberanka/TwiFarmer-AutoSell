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
        assertEquals("farmer.autoseller", repaired.getString("customPerm"));
        assertEquals(2, repaired.getInt("optimize-module.processingDelayTicks"));
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
        assertTrue(repaired.getStringList("moduleGui.icon.lore").stream().anyMatch(line -> line.contains("{status}")));
        assertTrue(hasBackup(turkish.toFile()));
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
