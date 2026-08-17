package mezz.jei.gui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.config.file.serializers.BookmarkIngredientKeySerializer;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FavoriteRecipeJsonSerializer {
	private static final String TYPE_FAVORITE = "favorite";

	private FavoriteRecipeJsonSerializer() {
	}

	public static List<JsonObject> serializeStore(FavoriteRecipeStore store) {
		return store.entries()
			.stream()
			.map(FavoriteRecipeJsonSerializer::serializeEntry)
			.toList();
	}

	public static FavoriteRecipeStore deserializeStore(JsonArray jsonArray) {
		List<FavoriteRecipeStore.Entry> entries = new ArrayList<>();
		for (JsonElement element : jsonArray) {
			deserializeEntry(element)
				.ifPresent(entries::add);
		}
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorites(entries);
		return store;
	}

	public static JsonObject serializeEntry(FavoriteRecipeStore.Entry entry) {
		JsonObject json = new JsonObject();
		json.addProperty("type", TYPE_FAVORITE);
		json.addProperty("recipeType", entry.recipe().recipeTypeUid().toString());
		json.addProperty("recipe", entry.recipe().recipeUid().toString());
		json.add("target", BookmarkIngredientKeySerializer.serialize(entry.target()));
		if (!entry.inputs().isEmpty()) {
			JsonObject inputs = new JsonObject();
			entry.inputs().keySet().stream()
				.sorted()
				.forEach(index -> inputs.add(Integer.toString(index), serializeSlotInput(entry.inputs().get(index))));
			json.add("inputs", inputs);
		}
		return json;
	}

	public static Optional<FavoriteRecipeStore.Entry> deserializeEntry(JsonElement element) {
		try {
			if (!element.isJsonObject()) {
				return Optional.empty();
			}
			JsonObject json = element.getAsJsonObject();
			if (!TYPE_FAVORITE.equals(json.get("type").getAsString())) {
				return Optional.empty();
			}
			FocusedRecipe recipe = new FocusedRecipe(
				new ResourceLocation(json.get("recipeType").getAsString()),
				new ResourceLocation(json.get("recipe").getAsString())
			);
			BookmarkIngredientKey target = BookmarkIngredientKeySerializer.deserialize(json.get("target"));
			Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> inputs = deserializeInputs(json);
			return Optional.of(new FavoriteRecipeStore.Entry(target, recipe, inputs));
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private static JsonObject serializeSlotInput(FavoriteRecipeStore.FavoriteSlotInput slotInput) {
		JsonObject json = new JsonObject();
		json.add("selected", BookmarkIngredientKeySerializer.serialize(slotInput.selected()));
		if (!slotInput.permutations().equals(List.of(slotInput.selected()))) {
			JsonArray permutations = new JsonArray();
			slotInput.permutations().stream()
				.map(BookmarkIngredientKeySerializer::serialize)
				.forEach(permutations::add);
			json.add("permutations", permutations);
		}
		return json;
	}

	private static Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> deserializeInputs(JsonObject json) {
		if (!json.has("inputs") || !json.get("inputs").isJsonObject()) {
			return Map.of();
		}
		JsonObject inputsJson = json.getAsJsonObject("inputs");
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> inputs = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : inputsJson.entrySet()) {
			try {
				int index = Integer.parseInt(entry.getKey());
				FavoriteRecipeStore.FavoriteSlotInput slotInput = deserializeSlotInput(entry.getValue());
				if (slotInput != null) {
					inputs.put(index, slotInput);
				}
			} catch (NumberFormatException ignored) {
			}
		}
		return Map.copyOf(inputs);
	}

	private static FavoriteRecipeStore.FavoriteSlotInput deserializeSlotInput(JsonElement element) {
		if (!element.isJsonObject()) {
			return null;
		}
		JsonObject json = element.getAsJsonObject();
		List<BookmarkIngredientKey> permutations = json.has("permutations") ?
			json.getAsJsonArray("permutations").asList().stream()
				.map(BookmarkIngredientKeySerializer::deserialize)
				.toList() :
			List.of();
		BookmarkIngredientKey selected;
		if (json.has("selected")) {
			selected = BookmarkIngredientKeySerializer.deserialize(json.get("selected"));
		} else if (!permutations.isEmpty()) {
			selected = permutations.get(0);
		} else {
			return null;
		}
		return new FavoriteRecipeStore.FavoriteSlotInput(selected, permutations);
	}
}
