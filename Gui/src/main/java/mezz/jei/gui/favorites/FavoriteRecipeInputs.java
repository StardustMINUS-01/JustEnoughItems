package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.recipes.InputSlotSelectionState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FavoriteRecipeInputs {
	private FavoriteRecipeInputs() {
	}

	public static Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> capture(
		IRecipeLayoutDrawable<?> recipeLayout,
		InputSlotSelectionState selectionState,
		IIngredientManager ingredientManager
	) {
		Map<Integer, BookmarkIngredientKey> selections = selectionState.currentSelections(recipeLayout);
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.INPUT);
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> inputs = new LinkedHashMap<>();
		for (int i = 0; i < inputSlots.size(); i++) {
			IRecipeSlotView slot = inputSlots.get(i);
			List<BookmarkIngredientKey> permutations = slot.getAllIngredients()
				.map(ingredient -> createKey(ingredient, ingredientManager))
				.distinct()
				.toList();
			if (permutations.isEmpty()) {
				continue;
			}
			BookmarkIngredientKey selected = selections.get(i);
			if (selected == null || !permutations.contains(selected)) {
				selected = permutations.getFirst();
			}
			inputs.put(i, new FavoriteRecipeStore.FavoriteSlotInput(selected, permutations));
		}
		return Map.copyOf(inputs);
	}

	private static BookmarkIngredientKey createKey(ITypedIngredient<?> ingredient, IIngredientManager ingredientManager) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}
}
