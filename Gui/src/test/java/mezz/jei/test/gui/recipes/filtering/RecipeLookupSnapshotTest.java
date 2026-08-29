package mezz.jei.test.gui.recipes.filtering;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
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
import mezz.jei.gui.recipes.filtering.IRecipeSearchTextMatcher;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RecipeLookupSnapshotTest {
	private static final ResourceLocation TYPE_UID = ResourceLocation.parse("test:macerating");
	private static final ResourceLocation OUTPUT = ResourceLocation.parse("test:silver_dust");
	private static final TestRecipeCategory CATEGORY = new TestRecipeCategory();

	@Test
	public void intersectsSearchWithPreferredAndStrictComplement() {
		RecipeLookupSnapshot snapshot = new RecipeLookupSnapshot(
			List.of(new RecipeLookupSnapshot.CategoryRecipes<>(
				CATEGORY,
				List.of(
					entry("ingot", "Silver Ingot", "c:ingots"),
					entry("plate", "Silver Plate", "c:plates")
				)
			)),
			new RecipePreferenceRules(List.of(new RecipePreferenceRule(
				IngredientExpression.parseIngredient("test:silver_dust").orElseThrow(),
				Optional.of(IngredientExpression.parseIngredient("#c:ingots").orElseThrow()),
				Optional.empty()
			)))
		);

		Assertions.assertEquals(
			List.of("ingot", "plate"),
			recipes(snapshot.project(RecipeFilterMode.ALL, RecipeSearchQuery.parse("silver")))
		);
		Assertions.assertEquals(
			List.of("ingot"),
			recipes(snapshot.project(RecipeFilterMode.PREFERRED, RecipeSearchQuery.parse("")))
		);
		Assertions.assertEquals(
			List.of("plate"),
			recipes(snapshot.project(RecipeFilterMode.NOT_PREFERRED, RecipeSearchQuery.parse("")))
		);
		Assertions.assertTrue(
			snapshot.project(RecipeFilterMode.PREFERRED, RecipeSearchQuery.parse("plate")).isEmpty()
		);
	}

	@Test
	public void usesRegisteredSearchStorageForRecipesAndInputCandidates() {
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

	private static RecipeLookupSnapshot.RecipeEntry<String> entry(String recipe, String inputName, String tag) {
		FocusedRecipe focusedRecipe = new FocusedRecipe(TYPE_UID, ResourceLocation.fromNamespaceAndPath("test", recipe));
		IngredientMatchInfo input = IngredientMatchInfo.item(
			ResourceLocation.fromNamespaceAndPath("test", recipe),
			Set.of(ResourceLocation.parse(tag))
		);
		RecipePreferenceCandidate candidate = new RecipePreferenceCandidate(
			focusedRecipe,
			List.of(input),
			List.of(IngredientMatchInfo.item(OUTPUT, Set.of()))
		);
		RecipeSearchIngredient ingredient = new RecipeSearchIngredient(
			inputName,
			ResourceLocation.fromNamespaceAndPath("test", recipe),
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

	private static final class TestRecipeCategory implements IRecipeCategory<String> {
		private static final RecipeType<String> TYPE = RecipeType.create("test", "macerating", String.class);

		@Override
		public RecipeType<String> getRecipeType() {
			return TYPE;
		}

		@Override
		public Component getTitle() {
			return Component.literal("Macerating");
		}

		@Override
		public @Nullable IDrawable getIcon() {
			return null;
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, String recipe, IFocusGroup focuses) {
		}

		@Override
		public int getWidth() {
			return 100;
		}

		@Override
		public int getHeight() {
			return 50;
		}
	}
}
