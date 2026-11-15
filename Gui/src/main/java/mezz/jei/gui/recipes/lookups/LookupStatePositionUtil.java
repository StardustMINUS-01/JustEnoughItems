package mezz.jei.gui.recipes.lookups;

public final class LookupStatePositionUtil {
	private LookupStatePositionUtil() {
	}

	public static void restoreRecipeIndex(ILookupState state, int recipeIndex) {
		int recipesPerPage = state.getRecipesPerPage();
		int lastPageIndex = (state.pageCount() - 1) * recipesPerPage;
		int pageIndex = Math.max(0, recipeIndex - (recipeIndex % recipesPerPage));
		int restoredIndex = Math.min(pageIndex, lastPageIndex);

		state.setRecipeIndex(restoredIndex);
	}
}
