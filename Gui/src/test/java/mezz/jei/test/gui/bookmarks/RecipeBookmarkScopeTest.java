package mezz.jei.test.gui.bookmarks;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RecipeBookmarkScopeTest {
	private static final ResourceLocation RECIPE = ResourceLocation.parse("test:plate");
	private static final IIngredientType<String> TEST_INGREDIENT_TYPE = () -> String.class;

	@Test
	public void scopedRecipeBookmarksDoNotEqualRecipeBookmarksFromOtherGroups() {
		RecipeBookmark<String, String> unscoped = recipeBookmark(null);
		RecipeBookmark<String, String> sameUnscoped = recipeBookmark(null);
		RecipeBookmark<String, String> groupOne = recipeBookmark("group_1");
		RecipeBookmark<String, String> sameGroupOne = recipeBookmark("group_1");
		RecipeBookmark<String, String> groupTwo = recipeBookmark("group_2");

		Assertions.assertEquals(unscoped, sameUnscoped);
		Assertions.assertEquals(groupOne, sameGroupOne);
		Assertions.assertNotEquals(unscoped, groupOne);
		Assertions.assertNotEquals(groupOne, groupTwo);
	}

	private static RecipeBookmark<String, String> recipeBookmark(@Nullable Object equalityScope) {
		return new RecipeBookmark<>(
			null,
			"recipe",
			RECIPE,
			new TestTypedIngredient("plate"),
			RecipeIngredientRole.OUTPUT,
			equalityScope
		);
	}

	private record TestTypedIngredient(String ingredient) implements ITypedIngredient<String> {
		@Override
		public ITypedIngredient<String> normalize(mezz.jei.api.ingredients.IIngredientHelper<String> helper) {
			return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(getType(), helper.normalizeIngredient(getIngredient()));
		}

		@Override
		public IIngredientType<String> getType() {
			return TEST_INGREDIENT_TYPE;
		}

		@Override
		public String getIngredient() {
			return ingredient;
		}
	}
}
