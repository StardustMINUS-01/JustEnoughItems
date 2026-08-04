package mezz.jei.test.gui.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.config.FavoriteRecipeJsonSerializer;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FavoriteRecipeJsonSerializerTest {
	private static final BookmarkIngredientKey QUARTZ_GLASS = key("ae2:quartz_glass");
	private static final BookmarkIngredientKey CERTUS_QUARTZ_DUST = key("ae2:certus_quartz_dust");
	private static final BookmarkIngredientKey WHITE_GLASS = key("minecraft:white_stained_glass");
	private static final BookmarkIngredientKey PLAIN_GLASS = key("minecraft:glass");
	private static final FocusedRecipe QUARTZ_GLASS_RECIPE = recipe("minecraft:crafting", "ae2:decorative/quartz_glass");

	@Test
	public void roundTripsEntryWithFlattenedRecipeAndInputs() {
		FavoriteRecipeStore.Entry entry = new FavoriteRecipeStore.Entry(
			QUARTZ_GLASS,
			QUARTZ_GLASS_RECIPE,
			Map.of(
				0, new FavoriteRecipeStore.FavoriteSlotInput(CERTUS_QUARTZ_DUST, List.of(CERTUS_QUARTZ_DUST)),
				1, new FavoriteRecipeStore.FavoriteSlotInput(WHITE_GLASS, List.of(WHITE_GLASS, PLAIN_GLASS))
			)
		);

		JsonObject json = FavoriteRecipeJsonSerializer.serializeEntry(entry);
		Optional<FavoriteRecipeStore.Entry> decoded = FavoriteRecipeJsonSerializer.deserializeEntry(json);

		Assertions.assertEquals(entry, decoded.orElseThrow());
		Assertions.assertEquals("minecraft:crafting", json.get("recipeType").getAsString());
		Assertions.assertEquals("ae2:decorative/quartz_glass", json.get("recipe").getAsString());
		Assertions.assertFalse(json.has("recipeTypeUid"));
		Assertions.assertTrue(json.get("recipe").isJsonPrimitive());
		Assertions.assertTrue(json.getAsJsonObject("inputs").getAsJsonObject("1").has("permutations"));
	}

	@Test
	public void roundTripsSerializedIngredient() {
		BookmarkIngredientKey nbtKey = new BookmarkIngredientKey("minecraft:item_stack", "minecraft:iron_pickaxe:tag", "{Damage:3}");
		FavoriteRecipeStore.Entry entry = new FavoriteRecipeStore.Entry(
			QUARTZ_GLASS,
			QUARTZ_GLASS_RECIPE,
			Map.of(0, new FavoriteRecipeStore.FavoriteSlotInput(nbtKey, List.of(nbtKey)))
		);

		FavoriteRecipeStore.Entry decoded = FavoriteRecipeJsonSerializer.deserializeEntry(
			FavoriteRecipeJsonSerializer.serializeEntry(entry)
		).orElseThrow();

		Assertions.assertEquals(entry, decoded);
	}

	@Test
	public void missingInputsDefaultsToEmptyMap() {
		JsonObject json = JsonParser.parseString("""
			{
				"type": "favorite",
				"recipeType": "minecraft:crafting",
				"recipe": "ae2:decorative/quartz_glass",
				"target": { "type": "minecraft:item_stack", "uid": "ae2:quartz_glass" }
			}
			""").getAsJsonObject();

		FavoriteRecipeStore.Entry decoded = FavoriteRecipeJsonSerializer.deserializeEntry(json).orElseThrow();

		Assertions.assertEquals(QUARTZ_GLASS_RECIPE, decoded.recipe());
		Assertions.assertEquals(QUARTZ_GLASS, decoded.target());
		Assertions.assertTrue(decoded.inputs().isEmpty());
	}

	@Test
	public void invalidElementIsSkipped() {
		Assertions.assertTrue(FavoriteRecipeJsonSerializer.deserializeEntry(JsonParser.parseString("\"garbage\"")).isEmpty());
		Assertions.assertTrue(FavoriteRecipeJsonSerializer.deserializeEntry(JsonParser.parseString("{}")).isEmpty());
		Assertions.assertTrue(FavoriteRecipeJsonSerializer.deserializeEntry(
			JsonParser.parseString("""
				{
					"type": "favorite",
					"recipeType": "minecraft:crafting",
					"recipe": "not a resource location",
					"target": { "type": "item_stack", "uid": "ae2:quartz_glass" }
				}
				""")
		).isEmpty());
	}

	private static BookmarkIngredientKey key(String uid) {
		return new BookmarkIngredientKey("minecraft:item_stack", uid, null);
	}

	private static FocusedRecipe recipe(String recipeTypeUid, String recipeUid) {
		return new FocusedRecipe(ResourceLocation.parse(recipeTypeUid), ResourceLocation.parse(recipeUid));
	}
}
