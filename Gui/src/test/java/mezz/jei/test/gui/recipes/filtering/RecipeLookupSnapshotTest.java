package mezz.jei.test.gui.recipes.filtering;

import mezz.jei.api.recipe.RecipeType;
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
import mezz.jei.gui.recipes.filtering.IRecipeSearchTextMatcher;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures.TestRecipeCategory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RecipeLookupSnapshotTest {
	private static final ResourceLocation TYPE_UID = ResourceLocation.parse("test:macerating");
	private static final ResourceLocation OUTPUT = ResourceLocation.parse("test:silver_dust");
	private static final TestRecipeCategory CATEGORY = new TestRecipeCategory(RecipeType.create("test", "macerating", String.class), TYPE_UID);

	@Test
	public void filtersPreferences() {
		RecipeLookupSnapshot snapshot = new RecipeLookupSnapshot(
			List.of(new RecipeLookupSnapshot.CategoryRecipes<>(
				CATEGORY,
				List.of(
					entry("ingot", "Silver Ingot", "c:ingots"),
					entry("plate", "Silver Plate", "c:plates"),
					new RecipeLookupSnapshot.RecipeEntry<>("unknown", entry("unknown", "Silver Unknown", "c:unknown").searchDocument(), Optional.empty())
				)
			)),
			new RecipePreferenceRules(List.of(new RecipePreferenceRule(
				IngredientExpression.parseIngredient("test:silver_dust").orElseThrow(),
				Optional.of(IngredientExpression.parseIngredient("#c:ingots").orElseThrow()),
				Optional.empty()
			)))
		);

		Assertions.assertEquals(
			List.of("ingot", "plate", "unknown"),
			recipes(snapshot.project(RecipeFilterMode.ALL, RecipeSearchQuery.parse("silver")))
		);
		Assertions.assertEquals(
			List.of("ingot"),
			recipes(snapshot.project(RecipeFilterMode.PREFERRED, RecipeSearchQuery.parse("")))
		);
		Assertions.assertEquals(
			List.of("plate", "unknown"),
			recipes(snapshot.project(RecipeFilterMode.NOT_PREFERRED, RecipeSearchQuery.parse("")))
		);
		Assertions.assertTrue(
			snapshot.project(RecipeFilterMode.PREFERRED, RecipeSearchQuery.parse("plate")).isEmpty()
		);
	}

	@Test
	public void matchesSearchAliases() {
		RecipeSearchIngredient ingredient = new RecipeSearchIngredient(
			"至高木板",
			ResourceLocation.parse("test:supreme_planks"),
			"test",
			Set.of("c:planks")
		);
		RecipeLookupSnapshot snapshot = new RecipeLookupSnapshot(
			List.of(new RecipeLookupSnapshot.CategoryRecipes<>(
				CATEGORY,
				List.of(entry("supreme_planks", "至高木板", "c:planks"))
			)),
			AliasSearchStorageBuilder::new
		);
		RecipeSearchQuery query = RecipeSearchQuery.parse("i:zhigaomuban");
		IRecipeSearchTextMatcher matcher = snapshot.createSearchTextMatcher();

		Assertions.assertEquals(
			List.of("supreme_planks"),
			recipes(snapshot.project(RecipeFilterMode.ALL, query, matcher))
		);
		Assertions.assertTrue(query.matchesInputCandidate(ingredient, matcher));
	}

	private static List<String> recipes(List<IFocusedRecipes<?>> projected) {
		return projected.stream()
			.flatMap(focused -> focused.getRecipes().stream())
			.map(String.class::cast)
			.toList();
	}

	private static RecipeLookupSnapshot.RecipeEntry<Object> entry(String recipe, String inputName, String tag) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", recipe);
		FocusedRecipe focusedRecipe = new FocusedRecipe(TYPE_UID, id);
		IngredientMatchInfo input = IngredientMatchInfo.item(
			id,
			Set.of(ResourceLocation.parse(tag))
		);
		RecipePreferenceCandidate candidate = new RecipePreferenceCandidate(
			focusedRecipe,
			List.of(input),
			List.of(IngredientMatchInfo.item(OUTPUT, Set.of()))
		);
		RecipeSearchIngredient ingredient = new RecipeSearchIngredient(
			inputName,
			id,
			"test",
			Set.of(tag)
		);
		RecipeSearchDocument document = new RecipeSearchDocument(
			List.of(ingredient),
			List.of(),
			List.of(),
			List.of("Macerating", "test:" + recipe)
		);
		return new RecipeLookupSnapshot.RecipeEntry<>(recipe, document, Optional.of(candidate));
	}

	private static final class AliasSearchStorageBuilder<T> extends BakedSubstringIndexBuilder<T> {
		@Override
		public void put(String key, T value) {
			super.put(key, value);
			if (key.contains("至高木板")) {
				super.put("zhigaomuban", value);
			}
		}
	}

}
