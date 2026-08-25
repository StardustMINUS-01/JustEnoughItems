package mezz.jei.test.gui.recipes.lookups;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.ProjectedLookupState;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

public class ProjectedLookupStateTest {
	@Test
	public void emptyProjectionKeepsAnInternalShellAndOneDisabledPage() {
		TestRecipeCategory category = new TestRecipeCategory();
		IFocusedRecipes<String> originalRecipes = new StaticFocusedRecipes<>(category, List.of("one", "two"));
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
	public void projectedPaginationDoesNotMutateOriginalState() {
		TestRecipeCategory category = new TestRecipeCategory();
		IFocusedRecipes<String> originalRecipes = new StaticFocusedRecipes<>(category, List.of("one", "two", "three"));
		ILookupState original = new TestLookupState(originalRecipes, emptyFocusGroup());
		ProjectedLookupState projected = new ProjectedLookupState(
			original,
			List.of(new StaticFocusedRecipes<>(category, List.of("one", "three")))
		);
		projected.setRecipesPerPage(1);

		Assertions.assertSame(projected.getRecipeCategories(), projected.getRecipeCategories());
		Assertions.assertTrue(projected.nextPage());
		Assertions.assertEquals(1, projected.getRecipeIndex());
		Assertions.assertEquals(0, original.getRecipeIndex());
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
			return 1;
		}

		@Override
		public void setRecipesPerPage(int recipesPerPage) {
		}

		@Override
		public int getRecipeIndex() {
			return 0;
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
		}

		@Override
		public boolean nextPage() {
			return false;
		}

		@Override
		public boolean previousPage() {
			return false;
		}

		@Override
		public int pageCount() {
			return 2;
		}
	}

	private static final class TestRecipeCategory implements IRecipeCategory<String> {
		private static final RecipeType<String> TYPE = RecipeType.create("test", "category", String.class);

		@Override
		public RecipeType<String> getRecipeType() {
			return TYPE;
		}

		@Override
		public Component getTitle() {
			return Component.literal("Test");
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
