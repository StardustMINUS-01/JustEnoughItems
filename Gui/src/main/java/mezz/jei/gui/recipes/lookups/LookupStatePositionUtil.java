package mezz.jei.gui.recipes.lookups;

public final class LookupStatePositionUtil {
	private LookupStatePositionUtil() {
	}

	public static void restoreRecipeIndex(ILookupState state, int recipeIndex) {
		int recipesPerPage = state.getRecipesPerPage();
		int lastPageIndex = (state.pageCount() - 1) * recipesPerPage;
		int pageIndex = Math.max(0, recipeIndex - (recipeIndex % recipesPerPage));
		int restoredIndex = Math.min(pageIndex, lastPageIndex);

		switch (state) {
			case IngredientLookupState ingredientLookupState -> ingredientLookupState.setRecipeIndex(restoredIndex);
			case ProjectedLookupState projectedLookupState -> projectedLookupState.setRecipeIndex(restoredIndex);
			case SingleCategoryLookupState singleCategoryLookupState -> singleCategoryLookupState.setRecipeIndex(restoredIndex);
			// Third-party mixins can pass custom states through RecipeGuiLogic#setState.
			default -> restoreWithPageNavigation(state, restoredIndex / recipesPerPage);
		}
	}

	private static void restoreWithPageNavigation(ILookupState state, int targetPage) {
		state.goToFirstPage();
		for (int page = 0; page < targetPage; page++) {
			state.nextPage();
		}
	}
}
