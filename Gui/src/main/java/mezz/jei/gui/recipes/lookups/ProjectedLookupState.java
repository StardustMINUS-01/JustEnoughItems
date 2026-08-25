package mezz.jei.gui.recipes.lookups;

import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.util.MathUtil;

import java.util.List;

public final class ProjectedLookupState implements ILookupState {
	private final IFocusGroup focuses;
	private final List<IFocusedRecipes<?>> projectedRecipes;
	private final List<IRecipeCategory<?>> recipeCategories;
	private final IFocusedRecipes<?> emptyRecipes;
	private int recipeCategoryIndex;
	private int recipeIndex;
	private int recipesPerPage = 1;

	public ProjectedLookupState(ILookupState original, List<IFocusedRecipes<?>> projectedRecipes) {
		this.focuses = original.getFocuses();
		this.projectedRecipes = List.copyOf(projectedRecipes);
		this.recipeCategories = this.projectedRecipes.stream()
			.<IRecipeCategory<?>>map(IFocusedRecipes::getRecipeCategory)
			.toList();
		IFocusedRecipes<?> originalRecipes = original.getFocusedRecipes();
		this.emptyRecipes = createEmptyRecipes(originalRecipes);
	}

	private static <T> IFocusedRecipes<T> createEmptyRecipes(IFocusedRecipes<T> focusedRecipes) {
		return new StaticFocusedRecipes<>(focusedRecipes.getRecipeCategory(), List.of());
	}

	@Override
	public List<IRecipeCategory<?>> getRecipeCategories() {
		return recipeCategories;
	}

	@Override
	public boolean moveToRecipeCategory(IRecipeCategory<?> recipeCategory) {
		for (int i = 0; i < projectedRecipes.size(); i++) {
			RecipeType<?> recipeType = projectedRecipes.get(i).getRecipeCategory().getRecipeType();
			if (recipeType.equals(recipeCategory.getRecipeType())) {
				return moveToRecipeCategoryIndex(i);
			}
		}
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

	void setRecipeIndex(int recipeIndex) {
		this.recipeIndex = recipeIndex;
	}

	@Override
	public IFocusGroup getFocuses() {
		return focuses;
	}

	@Override
	public IFocusedRecipes<?> getFocusedRecipes() {
		return projectedRecipes.isEmpty() ? emptyRecipes : projectedRecipes.get(recipeCategoryIndex);
	}

	@Override
	public IFocusedRecipes<?> getFocusedRecipes(IRecipeCategory<?> recipeCategory) {
		return projectedRecipes.stream()
			.filter(recipes -> recipes.getRecipeCategory().getRecipeType().equals(recipeCategory.getRecipeType()))
			.findFirst()
			.orElse(emptyRecipes);
	}

	@Override
	public boolean nextRecipeCategory() {
		if (projectedRecipes.size() < 2) {
			return false;
		}
		return moveToRecipeCategoryIndex((recipeCategoryIndex + 1) % projectedRecipes.size());
	}

	@Override
	public boolean previousRecipeCategory() {
		if (projectedRecipes.size() < 2) {
			return false;
		}
		return moveToRecipeCategoryIndex((projectedRecipes.size() + recipeCategoryIndex - 1) % projectedRecipes.size());
	}

	private boolean moveToRecipeCategoryIndex(int index) {
		if (recipeCategoryIndex == index) {
			return false;
		}
		recipeCategoryIndex = index;
		recipeIndex = 0;
		return true;
	}

	@Override
	public void goToFirstPage() {
		recipeIndex = 0;
	}

	@Override
	public boolean nextPage() {
		int originalIndex = recipeIndex;
		recipeIndex += recipesPerPage;
		if (recipeIndex >= recipeCount()) {
			recipeIndex = 0;
		}
		return recipeIndex != originalIndex;
	}

	@Override
	public boolean previousPage() {
		int originalIndex = recipeIndex;
		recipeIndex -= recipesPerPage;
		if (recipeIndex < 0) {
			recipeIndex = (pageCount() - 1) * recipesPerPage;
		}
		return recipeIndex != originalIndex;
	}

	@Override
	public int pageCount() {
		int recipeCount = recipeCount();
		return recipeCount <= 1 ? 1 : MathUtil.divideCeil(recipeCount, recipesPerPage);
	}

	private int recipeCount() {
		return getFocusedRecipes().getRecipes().size();
	}
}
