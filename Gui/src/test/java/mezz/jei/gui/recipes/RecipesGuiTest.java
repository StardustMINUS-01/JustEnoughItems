package mezz.jei.gui.recipes;

import mezz.jei.common.config.IClientConfig;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.search.BakedSubstringIndexBuilder;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRule;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.match.IngredientExpression;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.recipes.filtering.RecipeFilterMode;
import mezz.jei.gui.recipes.filtering.RecipeLookupSnapshot;
import mezz.jei.gui.recipes.filtering.RecipeSearchDocument;
import mezz.jei.gui.recipes.filtering.RecipeSearchIngredient;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.LookupStatePositionUtil;
import mezz.jei.gui.recipes.lookups.ProjectedLookupState;
import mezz.jei.gui.recipes.lookups.SingleCategoryLookupState;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class RecipesGuiTest {
	@Test
	public void filtersRecipesAndCandidates() {
		IRecipeCategory<String> category = category("macerating");
		RecipeLookupSnapshot.RecipeEntry<String> ingot = entry(category, "ingot");
		RecipeLookupSnapshot.RecipeEntry<String> plate = entry(category, "plate");
		RecipePreferenceRules rules = new RecipePreferenceRules(List.of(new RecipePreferenceRule(
			IngredientExpression.parseIngredient("test:dust").orElseThrow(),
			Optional.of(IngredientExpression.parseIngredient("test:ingot").orElseThrow()), Optional.empty()
		)));
		RecipeLookupSnapshot snapshot = new RecipeLookupSnapshot(
			List.of(new RecipeLookupSnapshot.CategoryRecipes<>(category, List.of(ingot, plate))),
			rules, AliasIndex::new
		);
		assertEquals(List.of("ingot"), snapshot.project(RecipeFilterMode.PREFERRED, RecipeSearchQuery.parse("")).get(0).getRecipes());
		assertEquals(List.of("plate"), snapshot.project(RecipeFilterMode.NOT_PREFERRED, RecipeSearchQuery.parse("")).get(0).getRecipes());
		assertTrue(snapshot.project(RecipeFilterMode.PREFERRED, RecipeSearchQuery.parse("i:plate")).isEmpty());

		var query = RecipeSearchQuery.parse("i:alias");
		var matcher = snapshot.createSearchTextMatcher();
		assertEquals(List.of("ingot"), snapshot.project(RecipeFilterMode.ALL, query, matcher).get(0).getRecipes());
		assertTrue(query.matchesInputCandidate(ingot.searchDocument().inputs().get(0), matcher));
		assertFalse(query.matchesInputCandidate(plate.searchDocument().inputs().get(0), matcher));
	}

	@Test
	public void restoresFilteredPagesAndEmptyResults() {
		IRecipeCategory<String> first = category("first");
		IRecipeCategory<String> second = category("second");
		IFocusedRecipes<String> recipes = new StaticFocusedRecipes<>(first, List.of("a", "b", "c", "d", "e"));
		var original = new SingleCategoryLookupState(recipes, null);
		var filtered = new ProjectedLookupState(original, List.of(recipes, new StaticFocusedRecipes<>(second, List.of("f"))));
		filtered.setRecipesPerPage(2);
		LookupStatePositionUtil.restoreRecipeIndex(filtered, 100);
		assertEquals(4, filtered.getRecipeIndex());
		filtered.nextPage();
		assertEquals(0, filtered.getRecipeIndex());
		filtered.previousPage();
		assertEquals(4, filtered.getRecipeIndex());
		filtered.nextRecipeCategory();
		assertSame(second, filtered.getFocusedRecipes().getRecipeCategory());
		assertEquals(0, filtered.getRecipeIndex());
		filtered.previousRecipeCategory();
		assertSame(first, filtered.getFocusedRecipes().getRecipeCategory());
		assertEquals(0, original.getRecipeIndex());

		var empty = new ProjectedLookupState(original, List.of());
		empty.nextRecipeCategory();
		empty.previousRecipeCategory();
		empty.previousPage();
		LookupStatePositionUtil.restoreRecipeIndex(empty, 100);
		assertTrue(empty.getRecipeCategories().isEmpty());
		assertTrue(empty.getFocusedRecipes().getRecipes().isEmpty());
		assertSame(first, empty.getFocusedRecipes().getRecipeCategory());
		assertEquals(0, empty.getRecipeIndex());
	}

	@SuppressWarnings("unchecked")
	private static IRecipeCategory<String> category(String id) {
		RecipeType<String> type = RecipeType.create("test", id, String.class);
		return (IRecipeCategory<String>) Proxy.newProxyInstance(IRecipeCategory.class.getClassLoader(), new Class<?>[]{IRecipeCategory.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getRecipeType" -> type;
				default -> throw new UnsupportedOperationException(method.getName());
			});
	}

	private static RecipeLookupSnapshot.RecipeEntry<String> entry(IRecipeCategory<String> category, String name) {
		ResourceLocation id = new ResourceLocation("test", name);
		var input = new RecipeSearchIngredient(name, id, "test", Set.of());
		var document = new RecipeSearchDocument(List.of(input), List.of(), List.of(), List.of());
		var candidate = new RecipePreferenceCandidate(
			new FocusedRecipe(category.getRecipeType().getUid(), id),
			List.of(IngredientMatchInfo.item(id, Set.of())),
			List.of(IngredientMatchInfo.item(new ResourceLocation("test:dust"), Set.of()))
		);
		return new RecipeLookupSnapshot.RecipeEntry<>(name, document, Optional.of(candidate));
	}

	private static final class AliasIndex<T> extends BakedSubstringIndexBuilder<T> {
		@Override
		public void put(String key, T value) {
			super.put(key, value);
			if (key.equals("ingot")) {
				super.put("alias", value);
			}
		}
	}

	@Test
	public void smallScreenUsesMinimumRecipeGuiHeight() {
		// Setup: the screen height is smaller than the recipe GUI's minimum height.
		int screenHeight = IClientConfig.minRecipeGuiHeight / 2;
		int maxHeight = IClientConfig.defaultRecipeGuiHeight;

		// Operation: calculate the initial recipe GUI size.
		RecipeGuiSizing.Size size = RecipeGuiSizing.calculateInitialSize(screenHeight, false, maxHeight);

		// Assertions: the recipe GUI keeps enough height for its internal layout.
		assertEquals(IClientConfig.minRecipeGuiHeight, size.ySize());
		assertEquals(0, size.extraSpace());
	}

	@Test
	public void smallScreenWithCenterSearchUsesMinimumRecipeGuiHeight() {
		// Setup: centered-search mode reserves more vertical space around the recipe GUI.
		int screenHeight = IClientConfig.minRecipeGuiHeight / 2;
		int maxHeight = IClientConfig.defaultRecipeGuiHeight;

		// Operation: calculate the initial recipe GUI size in centered-search mode.
		RecipeGuiSizing.Size size = RecipeGuiSizing.calculateInitialSize(screenHeight, true, maxHeight);

		// Assertions: the same minimum-height guard applies with centered search enabled.
		assertEquals(IClientConfig.minRecipeGuiHeight, size.ySize());
		assertEquals(0, size.extraSpace());
	}

	@Test
	public void tallScreenClampsToMaxRecipeGuiHeight() {
		// Setup: a tall screen has more available height than the configured maximum recipe GUI height.
		int maxHeight = IClientConfig.minRecipeGuiHeight + 20;
		int screenHeight = maxHeight + 200;

		// Operation: calculate the initial recipe GUI size.
		RecipeGuiSizing.Size size = RecipeGuiSizing.calculateInitialSize(screenHeight, false, maxHeight);

		// Assertions: the recipe GUI height is clamped to the configured maximum, with extra height for centering.
		assertEquals(maxHeight, size.ySize());
		assertTrue(size.extraSpace() > 0);
	}
}
