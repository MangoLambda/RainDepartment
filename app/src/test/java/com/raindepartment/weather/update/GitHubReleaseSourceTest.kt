package com.raindepartment.weather.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseSourceTest {
    @Test
    fun normalizesPublicRepositoryUrl() {
        assertEquals(
            "https://api.github.com/repos/MangoLambda/RainDepartment/releases",
            GitHubReleaseSource.releasesApiUrl("https://github.com/MangoLambda/RainDepartment/"),
        )
        assertEquals(
            "https://api.github.com/repos/MangoLambda/RainDepartment/releases/latest",
            GitHubReleaseSource.latestReleaseApiUrl("https://github.com/MangoLambda/RainDepartment"),
        )
        assertNull(GitHubReleaseSource.releasesApiUrl("http://github.com/MangoLambda/RainDepartment"))
    }

    @Test
    fun comparesSemanticVersionsAndPreReleases() {
        assertTrue(GitHubReleaseSource.compareVersions("1.2.0", "1.1.30") > 0)
        assertTrue(GitHubReleaseSource.compareVersions("v1.1.29", "1.1.30") < 0)
        assertTrue(GitHubReleaseSource.compareVersions("0.0.1", "0.0.1-alpha.5") > 0)
        assertTrue(GitHubReleaseSource.compareVersions("0.0.1-alpha.10", "0.0.1-alpha.9") > 0)
        assertEquals(0, GitHubReleaseSource.compareVersions("1.1.30", "v1.1.30"))
        assertEquals(0, GitHubReleaseSource.compareVersions("not-a-version", "1.1.30"))
    }

    @Test
    fun comparesHistoricalAlphaVersionNamesUsingVersionCode() {
        assertTrue(
            GitHubReleaseSource.compareToInstalled("0.0.1-alpha.6", "0.0.1", 5) > 0,
        )
        assertEquals(
            0,
            GitHubReleaseSource.compareToInstalled("0.0.1-alpha.5", "0.0.1", 5),
        )
        assertTrue(
            GitHubReleaseSource.compareToInstalled("0.0.2", "0.0.1", 5) > 0,
        )
    }

    @Test
    fun selectsNewestReleaseWithExactChecksummedAsset() {
        val release = GitHubReleaseSource.parse(
            """
            [
              ${releaseJson("0.0.1-alpha.5")},
              ${releaseJson("0.0.1-alpha.6")}
            ]
            """.trimIndent(),
        )

        assertEquals("v0.0.1-alpha.6", release?.tag)
        assertEquals("RainDepartment-v0.0.1-alpha.6.apk", release?.assetName)
        assertEquals(DIGEST, release?.sha256)
    }

    @Test
    fun ignoresDraftsAndAssetsWithoutDigest() {
        assertNull(GitHubReleaseSource.parse(releaseJson("0.0.1-alpha.7", draft = true)))
        assertNull(
            GitHubReleaseSource.parse(
                releaseJson("0.0.1-alpha.7").replace("sha256:$DIGEST", ""),
            ),
        )
    }

    @Test
    fun rejectsNonStandardAssetNames() {
        val json = releaseJson("0.0.1-alpha.8")
            .replace("RainDepartment-v0.0.1-alpha.8.apk", "app-release.apk")
        assertNull(GitHubReleaseSource.parse(json))
    }

    private fun releaseJson(version: String, draft: Boolean = false): String = """
        {
          "tag_name": "v$version",
          "name": "RainDepartment $version",
          "body": "Changes",
          "draft": $draft,
          "prerelease": true,
          "assets": [
            {
              "name": "RainDepartment-v$version.apk",
              "browser_download_url": "https://example.test/RainDepartment-v$version.apk",
              "size": 123,
              "digest": "sha256:$DIGEST"
            }
          ]
        }
    """.trimIndent()

    private companion object {
        const val DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
