package mezz.jei.test.gui.recipes.lookups;

import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.LookupStatePositionUtil;
import mezz.jei.gui.recipes.lookups.ProjectedLookupState;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures.TestRecipeCategory;

import java.lang.reflect.Proxy;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;

public class ProjectedLookupStateTest {
	@Test
	public void handlesEmptyProjection() {
		TestRecipeCategory category = new TestRecipeCategory(RecipeType.create("test", "category", String.class), ResourceLocation.parse("test:recipe"));
		IFocusedRecipes<Object> originalRecipes = new StaticFocusedRecipes<>(category, List.of("one", "two"));
		ILookupState original = new TestLookupState(originalRecipes, emptyFocusGroup());

		ProjectedLookupState projected = new ProjectedLookupState(original, List.of());

		Assertions.assertTrue(projected.getRecipeCategories().isEmpty());
		Assertions.assertSame(category, projected.getFocusedRecipes().getRecipeCategory());
		Assertions.assertTrue(projected.getFocusedRecipes().getRecipes().isEmpty());
		Assertions.assertEquals(1, projected.pageCount());
		Assertions.assertFalse(projected.nextPage());
		Assertions.assertFalse(projected.nextRecipeCategory());
	}

	@Test
	public void isolatesPagination() {
		TestRecipeCategory category = new TestRecipeCategory(RecipeType.create("test", "category", String.class), ResourceLocation.parse("test:recipe"));
		IFocusedRecipes<Object> originalRecipes = new StaticFocusedRecipes<>(category, List.of("one", "two", "three"));
		ILookupState original = new TestLookupState(originalRecipes, emptyFocusGroup());
		ProjectedLookupState projected = new ProjectedLookupState(
			original,
			List.of(new StaticFocusedRecipes<>(category, List.of("one", "three")))
		);
		projected.setRecipesPerPage(1);

		Assertions.assertEquals(List.of(category), projected.getRecipeCategories());
		Assertions.assertSame(projected.getRecipeCategories(), projected.getRecipeCategories());
		Assertions.assertSame(original.getFocuses(), projected.getFocuses());
		Assertions.assertTrue(projected.nextPage());
		Assertions.assertEquals(1, projected.getRecipeIndex());
		Assertions.assertEquals(0, original.getRecipeIndex());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void restoresPage(boolean projected) {
		TestRecipeCategory category = new TestRecipeCategory(RecipeType.create("test", "category", String.class), ResourceLocation.parse("test:recipe"));
		IFocusedRecipes<Object> recipes = new StaticFocusedRecipes<>(category, List.of("one", "two", "three", "four", "five"));
		ILookupState original = new TestLookupState(recipes, emptyFocusGroup());
		ILookupState state = projected ? new ProjectedLookupState(original, List.of(recipes)) : original;
		state.setRecipesPerPage(2);

		LookupStatePositionUtil.restoreRecipeIndex(state, 3);
		Assertions.assertEquals(2, state.getRecipeIndex());

		LookupStatePositionUtil.restoreRecipeIndex(state, 20);
		Assertions.assertEquals(4, state.getRecipeIndex());
	}

	@SuppressWarnings("unchecked")
	private static IFocusGroup emptyFocusGroup() {
		return (IFocusGroup) Proxy.newProxyInstance(
			IFocusGroup.class.getClassLoader(),
			new Class<?>[]{IFocusGroup.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "isEmpty" -> true;
				case "getAllFocuses" -> List.of();
				case "getFocuses" -> java.util.stream.Stream.empty();
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static final class TestLookupState implements ILookupState {
		private final IFocusedRecipes<?> focusedRecipes;
		private final IFocusGroup focuses;
		private int recipeIndex;
		private int recipesPerPage = 1;

		private TestLookupState(IFocusedRecipes<?> focusedRecipes, IFocusGroup focuses) {
			this.focusedRecipes = focusedRecipes;
			this.focuses = focuses;
		}

		@Override
		public List<IRecipeCategory<?>> getRecipeCategories() {
			return List.of(focusedRecipes.getRecipeCategory());
		}

		@Override
		public boolean moveToRecipeCategory(IRecipeCategory<?> recipeCategory) {
			return false;
		}

		@Override
		public int getRecipesPerPage() {
			return recipesPerPage;
		}

		@Override
		public void setRecipesPerPage(int recipesPerPage) {
			this.recipesPerPage = recipesPerPage;
		}

		@Override
		public int getRecipeIndex() {
			return recipeIndex;
		}

		@Override
		public IFocusGroup getFocuses() {
			return focuses;
		}

		@Override
		public IFocusedRecipes<?> getFocusedRecipes() {
			return focusedRecipes;
		}

		@Override
		public IFocusedRecipes<?> getFocusedRecipes(IRecipeCategory<?> recipeCategory) {
			return focusedRecipes;
		}

		@Override
		public boolean nextRecipeCategory() {
			return false;
		}

		@Override
		public boolean previousRecipeCategory() {
			return false;
		}

		@Override
		public void goToFirstPage() {
			recipeIndex = 0;
		}

		@Override
		public boolean nextPage() {
			int oldIndex = recipeIndex;
			recipeIndex += recipesPerPage;
			if (recipeIndex >= focusedRecipes.getRecipes().size()) {
				recipeIndex = 0;
			}
			return recipeIndex != oldIndex;
		}

		@Override
		public boolean previousPage() {
			return false;
		}

		@Override
		public int pageCount() {
			return Math.max(1, (focusedRecipes.getRecipes().size() + recipesPerPage - 1) / recipesPerPage);
		}
	}

}
