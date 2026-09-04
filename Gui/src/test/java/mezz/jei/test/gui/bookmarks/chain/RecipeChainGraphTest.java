package mezz.jei.test.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainGraph;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class RecipeChainGraphTest {
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
	private static final ResourceLocation PLATE_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "plate");
	private static final ResourceLocation MACHINE_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "machine");

	@Test
	public void indexesRecipeInputsAndSelectsExactPreferredResult() {
		RecipeChainInput plateResult = input(0, result(PLATE_RECIPE, Set.of(key("plate")), 1));
		RecipeChainInput plateIngredient = input(1, ingredient(PLATE_RECIPE, Set.of(key("ingot"))));
		RecipeChainInput machineResult = input(2, result(MACHINE_RECIPE, Set.of(key("machine")), 1));
		RecipeChainInput machineIngredient = input(3, ingredient(MACHINE_RECIPE, Set.of(key("plate"), key("plate_variant"))));
		RecipeChainInput exactPlateResult = input(4, result(
			ResourceLocation.fromNamespaceAndPath("test", "exact_plate"),
			Set.of(key("plate"), key("plate_variant")),
			1
		));

		RecipeChainGraph graph = RecipeChainGraph.create(List.of(
			plateResult,
			plateIngredient,
			machineResult,
			machineIngredient,
			exactPlateResult
		));

		Assertions.assertEquals(List.of(machineIngredient), graph.ingredientsFor(MACHINE_RECIPE));
		Assertions.assertEquals(exactPlateResult, graph.findPreferredResult(machineIngredient, Set.of()).orElseThrow());
	}

	@Test
	public void excludesVisitedProducerRecipesWithoutScanningUnrelatedRecipes() {
		RecipeChainInput firstResult = input(0, result(PLATE_RECIPE, Set.of(key("plate")), 1));
		RecipeChainInput machineIngredient = input(1, ingredient(MACHINE_RECIPE, Set.of(key("plate"))));
		RecipeChainGraph graph = RecipeChainGraph.create(List.of(firstResult, machineIngredient));

		Assertions.assertTrue(graph.findPreferredResult(machineIngredient, Set.of(PLATE_RECIPE)).isEmpty());
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	private static BookmarkItemMetadata result(ResourceLocation recipeUid, Set<BookmarkIngredientKey> keys, long factor) {
		return metadata(recipeUid, BookmarkItemType.RESULT, keys, factor);
	}

	private static BookmarkItemMetadata ingredient(ResourceLocation recipeUid, Set<BookmarkIngredientKey> keys) {
		return metadata(recipeUid, BookmarkItemType.INGREDIENT, keys, 1);
	}

	private static BookmarkItemMetadata metadata(ResourceLocation recipeUid, BookmarkItemType type, Set<BookmarkIngredientKey> keys, long factor) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			1,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			keys
		);
	}

	private static BookmarkIngredientKey key(String uid) {
		return new BookmarkIngredientKey("test:item", uid);
	}
}
