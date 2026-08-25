package mezz.jei.gui.recipes.navigation;

import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.recipes.filtering.RecipeFilterMode;
import mezz.jei.gui.recipes.lookups.ILookupState;
import net.minecraft.network.chat.Component;

import java.util.Map;

public final class RecipeNavigationEntry {
	private final ILookupState lookupState;
	private final Component title;
	private RecipeFilterMode filterMode;
	private String searchQuery;
	private IRecipeCategory<?> recipeCategory;
	private int recipeIndex;
	private int recipesPerPage;
	private Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> inputSelections = Map.of();

	public RecipeNavigationEntry(
		ILookupState lookupState,
		Component title,
		RecipeFilterMode filterMode,
		String searchQuery,
		ILookupState displayedState
	) {
		this.lookupState = lookupState;
		this.title = title;
		updateView(filterMode, searchQuery, displayedState);
	}

	public void updateView(RecipeFilterMode filterMode, String searchQuery, ILookupState displayedState) {
		this.filterMode = filterMode;
		this.searchQuery = searchQuery;
		this.recipeCategory = displayedState.getFocusedRecipes().getRecipeCategory();
		this.recipeIndex = displayedState.getRecipeIndex();
		this.recipesPerPage = displayedState.getRecipesPerPage();
	}

	public void updateInputSelections(Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> visibleSelections) {
		this.inputSelections = visibleSelections;
	}

	public ILookupState getLookupState() {
		return lookupState;
	}

	public Component getTitle() {
		return title;
	}

	public RecipeFilterMode getFilterMode() {
		return filterMode;
	}

	public String getSearchQuery() {
		return searchQuery;
	}

	public IRecipeCategory<?> getRecipeCategory() {
		return recipeCategory;
	}

	public int getRecipeIndex() {
		return recipeIndex;
	}

	public int getRecipesPerPage() {
		return recipesPerPage;
	}

	public Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> getInputSelections() {
		return inputSelections;
	}
}
