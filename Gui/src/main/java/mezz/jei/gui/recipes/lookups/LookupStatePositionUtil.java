package mezz.jei.gui.recipes.lookups;

public final class LookupStatePositionUtil {
	private LookupStatePositionUtil() {
	}

	public static void restoreRecipeIndex(ILookupState state, int recipeIndex) {
		int recipesPerPage = state.getRecipesPerPage();
		int lastPageIndex = (state.pageCount() - 1) * recipesPerPage;
		int pageIndex = Math.max(0, recipeIndex - (recipeIndex % recipesPerPage));
		int restoredIndex = Math.min(pageIndex, lastPageIndex);

		if (state instanceof IngredientLookupState ingredientLookupState) {
			ingredientLookupState.setRecipeIndex(restoredIndex);
		} else if (state instanceof SingleCategoryLookupState singleCategoryLookupState) {
			singleCategoryLookupState.setRecipeIndex(restoredIndex);
		} else {
			restoreWithPageNavigation(state, restoredIndex / recipesPerPage);
		}
	}

	private static void restoreWithPageNavigation(ILookupState state, int targetPage) {
		state.goToFirstPage();
		for (int page = 0; page < targetPage; page++) {
			state.nextPage();
		}
	}
}
