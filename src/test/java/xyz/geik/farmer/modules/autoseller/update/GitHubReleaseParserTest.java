package xyz.geik.farmer.modules.autoseller.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubReleaseParserTest {

    @Test
    void selectsValidatedDirectJarAsset() {
        String body = """
                {"tag_name":"v2.0.1","html_url":"https://github.com/siberanka/TwiFarmer-AutoSell/releases/tag/v2.0.1",
                "assets":[{"name":"Farmer-AutoSeller-2.0.1.jar","browser_download_url":"https://github.com/siberanka/TwiFarmer-AutoSell/releases/download/v2.0.1/Farmer-AutoSeller-2.0.1.jar"}]}
                """;
        GitHubReleaseParser.ReleaseInfo release = GitHubReleaseParser.parse(body).orElseThrow();
        assertEquals("v2.0.1", release.tag());
        assertTrue(release.downloadUrl().endsWith("Farmer-AutoSeller-2.0.1.jar"));
    }

    @Test
    void rejectsMalformedOversizedAndForeignResponses() {
        assertTrue(GitHubReleaseParser.parse("not-json").isEmpty());
        assertTrue(GitHubReleaseParser.parse("x".repeat(GitHubReleaseParser.MAX_RESPONSE_LENGTH + 1)).isEmpty());
        assertTrue(GitHubReleaseParser.parse("""
                {"tag_name":"v2.0.1","html_url":"https://example.com/release","assets":[]}
                """).isEmpty());
    }
}
