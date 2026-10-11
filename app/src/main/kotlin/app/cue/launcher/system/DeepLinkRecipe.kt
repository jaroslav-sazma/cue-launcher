package app.cue.launcher.system

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DeepLinkRecipe(
    @SerialName("package") val packageName: String,
    val uri: String,
    val label: String,
)

@Serializable
private data class RecipeBook(
    val schemaVersion: Int = RECIPES_VERSION,
    val recipes: List<DeepLinkRecipe> = emptyList(),
)

fun parseDeepLinkRecipes(json: String): List<DeepLinkRecipe> = runCatching {
    val book = recipeJson.decodeFromString<RecipeBook>(json)
    if (book.schemaVersion > RECIPES_VERSION) emptyList() else book.recipes
}.getOrDefault(emptyList())
    .filter { it.packageName.isNotBlank() && it.uri.isNotBlank() && it.label.isNotBlank() }

fun recipesFor(recipes: List<DeepLinkRecipe>, packageName: String): List<DeepLinkRecipe> =
    recipes.filter { it.packageName == packageName }

private val recipeJson = Json { ignoreUnknownKeys = true }

private const val RECIPES_VERSION = 1
