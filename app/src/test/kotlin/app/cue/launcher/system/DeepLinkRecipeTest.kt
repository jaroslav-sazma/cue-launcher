package app.cue.launcher.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DeepLinkRecipeTest {

    @Test
    fun `the catalog in the repo is read`() {
        val recipes = parseDeepLinkRecipes(ASSET.readText())

        assertTrue(recipes.isNotEmpty())
        assertTrue(recipes.all { it.uri.isNotBlank() && it.label.isNotBlank() })
    }

    @Test
    fun `recipes are filtered by package`() {
        val recipes = parseDeepLinkRecipes(
            """
            {
              "schemaVersion": 1,
              "recipes": [
                { "package": "com.spotify.music", "uri": "spotify:search", "label": "Hledání" },
                { "package": "com.spotify.music", "uri": "spotify:collection", "label": "Knihovna" },
                { "package": "com.google.android.youtube", "uri": "https://youtube.com", "label": "Domů" }
              ]
            }
            """.trimIndent(),
        )

        val spotify = recipesFor(recipes, "com.spotify.music")

        assertEquals(listOf("Hledání", "Knihovna"), spotify.map { it.label })
        assertTrue(recipesFor(recipes, "com.example.nic").isEmpty())
    }

    @Test
    fun `an incomplete recipe is discarded`() {
        val recipes = parseDeepLinkRecipes(
            """
            {
              "recipes": [
                { "package": "com.spotify.music", "uri": "", "label": "Hledání" },
                { "package": "", "uri": "spotify:search", "label": "Hledání" },
                { "package": "com.spotify.music", "uri": "spotify:search", "label": " " }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(recipes.isEmpty())
    }

    @Test
    fun `a broken file is an empty catalog`() {
        assertTrue(parseDeepLinkRecipes("{ tohle není JSON").isEmpty())
        assertTrue(parseDeepLinkRecipes("").isEmpty())
    }

    @Test
    fun `a newer catalog version is rejected entirely`() {
        val recipes = parseDeepLinkRecipes(
            """
            {
              "schemaVersion": 2,
              "recipes": [
                { "package": "com.spotify.music", "uri": "spotify:search", "label": "Hledání" }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(recipes.isEmpty())
    }

    @Test
    fun `unknown recipe fields do not matter`() {
        val recipes = parseDeepLinkRecipes(
            """
            {
              "_comment": "poznámka kurátora",
              "recipes": [
                { "package": "com.spotify.music", "uri": "spotify:search", "label": "Hledání", "note": "x" }
              ]
            }
            """.trimIndent(),
        )

        assertFalse(recipes.isEmpty())
    }

    private companion object {

        val ASSET: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/assets/deeplink_recipes.json") }
            .first { it.isFile }
    }
}
