package mezz.jei.test.bench;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.RecipeTreeBookmarkEntry;
import mezz.jei.gui.bookmarks.RecipeTreeBookmarkGroup;
import mezz.jei.gui.bookmarks.RecipeTreeSlotIngredient;
import mezz.jei.gui.favorites.FavoriteTreeBookmarkEntryResolver;
import mezz.jei.library.ingredients.subtypes.SubtypeInterpreters;
import mezz.jei.library.ingredients.subtypes.SubtypeManager;
import mezz.jei.library.load.registration.IngredientManagerBuilder;
import mezz.jei.test.lib.TestAmountIngredient;
import mezz.jei.test.lib.TestAmountIngredientHelper;
import mezz.jei.test.lib.TestAmountIngredientRenderer;
import mezz.jei.test.lib.TestColorHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benchmark tests for the 1.20.1 port of the JEI 1.21.1 favorite-tree
 * bookmark writer ({@code FavoriteTreeBookmarkEntryResolver} +
 * {@code BookmarkList#addRecipeLayoutProjectionBookmarkGroup}): one group per
 * recipe, contiguously ordered, with amounts aggregated per unique ingredient
 * across all slots of the recipe (1.21.1 {@code mergeRecipeInputs} semantics),
 * output entries before input entries, and group titles derived from the
 * recipe's primary output.
 */
public class JeiRecipeTreeEntryResolverTest {
	private static final ResourceLocation RECIPE_A = new ResourceLocation("test", "recipe_a");
	private static final ResourceLocation RECIPE_B = new ResourceLocation("test", "recipe_b");

	@Test
	public void resolvesOneGroupPerRecipeInTreeOrder() {
		IIngredientManager ingredientManager = createManager();
		List<RecipeTreeSlotIngredient> slots = List.of(
			slot(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT),
			slot(RECIPE_A, 2, 3, RecipeIngredientRole.INPUT),
			slot(RECIPE_B, 3, 5, RecipeIngredientRole.OUTPUT)
		);
		List<RecipeTreeBookmarkGroup> groups = FavoriteTreeBookmarkEntryResolver.resolve(slots, ingredientManager);
		assertEquals(2, groups.size());
		assertEquals("§eTest Amount Ingredient Display Name TestAmountIngredient#1", groups.get(0).title());
		assertEquals("§eTest Amount Ingredient Display Name TestAmountIngredient#3", groups.get(1).title());
	}

	@Test
	public void aggregatesAmountAcrossSlotsOfSameIngredient() {
		IIngredientManager ingredientManager = createManager();
		List<RecipeTreeSlotIngredient> slots = List.of(
			slot(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT),
			slot(RECIPE_A, 2, 3, RecipeIngredientRole.INPUT),
			// the same ingredient #2 appears in a second input slot: amounts sum
			slot(RECIPE_A, 2, 4, RecipeIngredientRole.INPUT)
		);
		List<RecipeTreeBookmarkGroup> groups = FavoriteTreeBookmarkEntryResolver.resolve(slots, ingredientManager);
		assertEquals(1, groups.size());
		List<RecipeTreeBookmarkEntry> entries = groups.get(0).entries();
		assertEquals(2, entries.size());
		// output first
		assertEquals(RecipeIngredientRole.OUTPUT, entries.get(0).role());
		assertEquals(2, entries.get(0).amount());
		// merged input: 3 + 4 = 7
		assertEquals(RecipeIngredientRole.INPUT, entries.get(1).role());
		assertEquals(7, entries.get(1).amount());
		assertEquals(RECIPE_A, entries.get(1).recipeUid());
	}

	@Test
	public void outputsComeBeforeInputsWithinGroup() {
		IIngredientManager ingredientManager = createManager();
		List<RecipeTreeSlotIngredient> slots = List.of(
			slot(RECIPE_A, 1, 2, RecipeIngredientRole.INPUT),
			slot(RECIPE_A, 2, 3, RecipeIngredientRole.INPUT),
			slot(RECIPE_A, 3, 4, RecipeIngredientRole.OUTPUT)
		);
		List<RecipeTreeBookmarkGroup> groups = FavoriteTreeBookmarkEntryResolver.resolve(slots, ingredientManager);
		assertEquals(1, groups.size());
		List<RecipeTreeBookmarkEntry> entries = groups.get(0).entries();
		// three distinct ingredients (#1, #2, #3): each yields its own entry,
		// mirroring the 1.21.1 BookmarkList#addSlotBookmarks de-duplication
		// based on RecipeBookmark.equals (recipeUid + displayRole + ingredient)
		assertEquals(3, entries.size());
		assertEquals(RecipeIngredientRole.OUTPUT, entries.get(0).role());
		assertEquals(RecipeIngredientRole.INPUT, entries.get(1).role());
		assertEquals(RecipeIngredientRole.INPUT, entries.get(2).role());
		// amounts are per-ingredient, not merged across distinct ingredients
		assertEquals(4, entries.get(0).amount());
		assertEquals(2, entries.get(1).amount());
		assertEquals(3, entries.get(2).amount());
	}

	@Test
	public void groupTitleFallsBackToFirstInput() {
		IIngredientManager ingredientManager = createManager();
		List<RecipeTreeSlotIngredient> slots = List.of(
			slot(RECIPE_A, 1, 2, RecipeIngredientRole.INPUT),
			slot(RECIPE_A, 2, 3, RecipeIngredientRole.INPUT)
		);
		assertEquals(
			"§eTest Amount Ingredient Display Name TestAmountIngredient#1",
			FavoriteTreeBookmarkEntryResolver.getGroupTitle(slots, ingredientManager)
		);
	}

	@Test
	public void groupTitleFallsBackToRecipe() {
		IIngredientManager ingredientManager = createManager();
		assertEquals(
			"Recipe",
			FavoriteTreeBookmarkEntryResolver.getGroupTitle(List.of(), ingredientManager)
		);
	}

	@Test
	public void emptySlotsResolveToNoGroups() {
		IIngredientManager ingredientManager = createManager();
		assertTrue(FavoriteTreeBookmarkEntryResolver.resolve(List.of(), ingredientManager).isEmpty());
	}

	@Test
	public void entriesAreContiguousWithinGroup() {
		IIngredientManager ingredientManager = createManager();
		List<RecipeTreeSlotIngredient> slots = List.of(
			slot(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT),
			slot(RECIPE_B, 2, 3, RecipeIngredientRole.OUTPUT),
			slot(RECIPE_A, 3, 4, RecipeIngredientRole.INPUT)
		);
		List<RecipeTreeBookmarkGroup> groups = FavoriteTreeBookmarkEntryResolver.resolve(slots, ingredientManager);
		assertEquals(2, groups.size());
		// group A: output + input
		assertEquals("TestAmountIngredient#1", groups.get(0).entries().get(0).ingredient().getIngredient().toString());
		assertEquals(2, groups.get(0).entries().size());
		// group B: single output
		assertEquals(1, groups.get(1).entries().size());
	}

	@Test
	public void defaultAmountIsOneWhenUnresolvable() {
		IIngredientManager ingredientManager = createManager();
		TestAmountIngredient ingredient = new TestAmountIngredient(9, 0);
		ITypedIngredient<TestAmountIngredient> typed = ingredientManager
			.createTypedIngredient(TestAmountIngredient.TYPE, ingredient)
			.orElseThrow();
		List<RecipeTreeSlotIngredient> slots = List.of(
			new RecipeTreeSlotIngredient(RECIPE_A, RecipeIngredientRole.INPUT, typed)
		);
		List<RecipeTreeBookmarkGroup> groups = FavoriteTreeBookmarkEntryResolver.resolve(slots, ingredientManager);
		assertEquals(1, groups.size());
		RecipeTreeBookmarkEntry entry = groups.get(0).entries().get(0);
		assertEquals(1, entry.amount());
	}

	private static RecipeTreeSlotIngredient slot(
		ResourceLocation recipeUid,
		int number,
		int amount,
		RecipeIngredientRole role
	) {
		IIngredientManager ingredientManager = createManager();
		TestAmountIngredient ingredient = new TestAmountIngredient(number, amount);
		ITypedIngredient<TestAmountIngredient> typed = ingredientManager
			.createTypedIngredient(TestAmountIngredient.TYPE, ingredient)
			.orElseThrow();
		return new RecipeTreeSlotIngredient(recipeUid, role, typed);
	}

	private static IIngredientManager createManager() {
		SubtypeManager subtypeManager = new SubtypeManager(new SubtypeInterpreters());
		IngredientManagerBuilder builder = new IngredientManagerBuilder(subtypeManager, new TestColorHelper());
		builder.register(
			TestAmountIngredient.TYPE,
			List.of(),
			new TestAmountIngredientHelper(false),
			new TestAmountIngredientRenderer()
		);
		return builder.build();
	}
}
