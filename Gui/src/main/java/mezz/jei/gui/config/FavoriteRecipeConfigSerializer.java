package mezz.jei.gui.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public final class FavoriteRecipeConfigSerializer {
	private FavoriteRecipeConfigSerializer() {
	}

	public static List<String> serializeStore(FavoriteRecipeStore store) {
		return store.entries()
			.stream()
			.map(FavoriteRecipeConfigSerializer::serializeEntry)
			.toList();
	}

	public static FavoriteRecipeStore deserializeStore(List<String> lines) {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		lines.stream()
			.map(FavoriteRecipeConfigSerializer::deserializeEntry)
			.flatMap(Optional::stream)
			.forEach(entry -> store.setFavorite(entry.target(), entry.recipe()));
		return store;
	}

	public static String serializeEntry(FavoriteRecipeStore.Entry entry) {
		JsonObject json = new JsonObject();
		json.add("target", serializeTarget(entry.target()));
		json.add("recipe", serializeRecipe(entry.recipe()));
		return json.toString();
	}

	public static Optional<FavoriteRecipeStore.Entry> deserializeEntry(String line) {
		if (line == null || line.isBlank() || line.startsWith("#")) {
			return Optional.empty();
		}

		try {
			JsonObject json = JsonParser.parseString(line).getAsJsonObject();
			BookmarkIngredientKey target = deserializeTarget(json.getAsJsonObject("target"));
			FocusedRecipe recipe = deserializeRecipe(json.getAsJsonObject("recipe"));
			return Optional.of(new FavoriteRecipeStore.Entry(target, recipe));
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private static JsonObject serializeTarget(BookmarkIngredientKey target) {
		JsonObject json = new JsonObject();
		json.addProperty("type", target.ingredientTypeUid());
		json.addProperty("uid", target.ingredientUid());
		if (target.serializedIngredient() != null) {
			json.addProperty("ingredient", target.serializedIngredient());
		}
		return json;
	}

	private static BookmarkIngredientKey deserializeTarget(JsonObject json) {
		String type = json.get("type").getAsString();
		String uid = json.get("uid").getAsString();
		String serializedIngredient = json.has("ingredient") ? json.get("ingredient").getAsString() : null;
		return new BookmarkIngredientKey(type, uid, serializedIngredient);
	}

	private static JsonObject serializeRecipe(FocusedRecipe recipe) {
		JsonObject json = new JsonObject();
		json.addProperty("recipeTypeUid", recipe.recipeTypeUid().toString());
		json.addProperty("recipeUid", recipe.recipeUid().toString());
		return json;
	}

	private static FocusedRecipe deserializeRecipe(JsonObject json) {
		ResourceLocation recipeTypeUid = ResourceLocation.parse(json.get("recipeTypeUid").getAsString());
		ResourceLocation recipeUid = ResourceLocation.parse(json.get("recipeUid").getAsString());
		return new FocusedRecipe(recipeTypeUid, recipeUid);
	}
}
