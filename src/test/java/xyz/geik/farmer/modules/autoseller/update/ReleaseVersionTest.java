package xyz.geik.farmer.modules.autoseller.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseVersionTest {

    @Test
    void comparesStableAndPrereleaseVersions() {
        assertTrue(ReleaseVersion.isNewer("2.0.0", "v2.0.1"));
        assertTrue(ReleaseVersion.isNewer("2.0.1-beta.2", "2.0.1"));
        assertFalse(ReleaseVersion.isNewer("2.0.1", "2.0.1-beta.2"));
        assertFalse(ReleaseVersion.isNewer("2.0.1", "2.0.1"));
    }

    @Test
    void rejectsMalformedAndUnboundedVersions() {
        assertTrue(ReleaseVersion.parse("2..1").isEmpty());
        assertTrue(ReleaseVersion.parse("2.01.1").isEmpty());
        assertTrue(ReleaseVersion.parse("2.0.1-01").isEmpty());
        assertTrue(ReleaseVersion.parse("9".repeat(65)).isEmpty());
    }
}
