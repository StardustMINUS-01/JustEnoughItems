package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainIterator;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeChainIteratorTest {
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
	private static final ResourceLocation STICK_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "stick");
	private static final ResourceLocation TABLE_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "table");
	private static final ResourceLocation TORCH_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "torch");

	@Test
	public void iteratorWalksRecipeChainOneDependencyLayerAtATime() {
		RecipeChainMath math = RecipeChainMath.of(List.of(
			input(0, result(TABLE_RECIPE, key("table"), 1, 1)),
			input(1, ingredient(TABLE_RECIPE, key("stick"), 4)),
			input(2, result(STICK_RECIPE, key("stick"), 4, 1)),
			input(3, ingredient(STICK_RECIPE, key("plank"), 2))
		), Set.of());
		math.createMasterRoot();

		RecipeChainIterator iterator = new RecipeChainIterator(math, List.of());
		iterator.updateInventory(List.of(input(100, item(key("plank"), 2))));

		Assertions.assertEquals(Map.of(TABLE_RECIPE, 1L), iterator.next());
		Assertions.assertEquals(Map.of(STICK_RECIPE, 1L), iterator.next());
		Assertions.assertEquals(Map.of(), iterator.next());
		Assertions.assertFalse(iterator.hasNext());
	}

	@Test
	public void iteratorRecalculatesNextBatchFromUpdatedInventory() {
		RecipeChainMath math = RecipeChainMath.of(List.of(
			input(0, result(TABLE_RECIPE, key("table"), 1, 1)),
			input(1, ingredient(TABLE_RECIPE, key("stick"), 4)),
			input(2, result(STICK_RECIPE, key("stick"), 4, 1)),
			input(3, ingredient(STICK_RECIPE, key("plank"), 2))
		), Set.of());
		math.createMasterRoot();

		RecipeChainIterator iterator = new RecipeChainIterator(math, List.of());
		iterator.updateInventory(List.of(input(100, item(key("stick"), 4))));

		Assertions.assertEquals(Map.of(TABLE_RECIPE, 1L), iterator.next());
		Assertions.assertEquals(Map.of(), iterator.next());
		Assertions.assertFalse(iterator.hasNext());
	}

	@Test
	public void iteratorReturnsParallelTopRecipesInTheSameBatch() {
		RecipeChainMath math = RecipeChainMath.of(List.of(
			input(0, result(TABLE_RECIPE, key("table"), 1, 1)),
			input(1, ingredient(TABLE_RECIPE, key("plank"), 4)),
			input(2, result(TORCH_RECIPE, key("torch"), 4, 1)),
			input(3, ingredient(TORCH_RECIPE, key("coal"), 1))
		), Set.of());
		math.createMasterRoot();

		RecipeChainIterator iterator = new RecipeChainIterator(math, List.of());
		iterator.updateInventory(List.of(
			input(100, item(key("plank"), 4)),
			input(101, item(key("coal"), 1))
		));

		Assertions.assertEquals(Map.of(TABLE_RECIPE, 1L, TORCH_RECIPE, 1L), iterator.next());
		Assertions.assertEquals(Map.of(), iterator.next());
		Assertions.assertFalse(iterator.hasNext());
	}

	@Test
	public void iteratorDoesNotLoopForeverOnCyclicRecipes() {
		ResourceLocation firstRecipe = ResourceLocation.fromNamespaceAndPath("test", "first");
		ResourceLocation secondRecipe = ResourceLocation.fromNamespaceAndPath("test", "second");
		RecipeChainMath math = RecipeChainMath.of(List.of(
			input(0, result(firstRecipe, key("first_item"), 1, 1)),
			input(1, ingredient(firstRecipe, key("second_item"), 1)),
			input(2, result(secondRecipe, key("second_item"), 1, 1)),
			input(3, ingredient(secondRecipe, key("first_item"), 1))
		), Set.of());
		math.createMasterRoot();

		RecipeChainIterator iterator = new RecipeChainIterator(math, List.of());
		int batches = 0;
		while (iterator.hasNext() && batches < 5) {
			iterator.next();
			batches++;
		}

		Assertions.assertTrue(batches < 5);
		Assertions.assertFalse(iterator.hasNext());
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	private static BookmarkItemMetadata item(BookmarkIngredientKey key, long amount) {
		return metadata(null, BookmarkItemType.ITEM, key, amount, 1);
	}

	private static BookmarkItemMetadata result(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor, long multiplier) {
		return metadata(recipeUid, BookmarkItemType.RESULT, key, factor, multiplier);
	}

	private static BookmarkItemMetadata ingredient(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor) {
		return metadata(recipeUid, BookmarkItemType.INGREDIENT, key, factor, 1);
	}

	private static BookmarkItemMetadata metadata(ResourceLocation recipeUid, BookmarkItemType type, BookmarkIngredientKey key, long factor, long multiplier) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			multiplier,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(key)
		);
	}

	private static BookmarkIngredientKey key(String uid) {
		return new BookmarkIngredientKey("test:item", uid);
	}
}
