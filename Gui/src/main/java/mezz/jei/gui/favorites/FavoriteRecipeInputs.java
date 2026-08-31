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
			BookmarkIngredientKey requested = selected; // effectively final for the lambda below
			if (requested == null || !permutations.contains(requested)) {
				// Same-ingredient fallback: the selection's NBT snapshot may differ from the
				// layout's variants (tool damage/materials); keep the user's exact key when the
				// ingredient kind matches, otherwise fall back to the first layout variant.
				boolean sameIngredient = requested != null &&
					permutations.stream().anyMatch(key -> key.matches(requested));
				if (requested == null || (!permutations.contains(requested) && !sameIngredient)) {
					selected = permutations.get(0);
				}
			}
			inputs.put(i, new FavoriteRecipeStore.FavoriteSlotInput(selected, permutations));
		}
		return Map.copyOf(inputs);
	}

	public static void apply(
		IRecipeLayoutDrawable<?> recipeLayout,
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> inputs,
		InputSlotSelectionState selectionState
	) {
		Map<Integer, BookmarkIngredientKey> selectedKeys = new LinkedHashMap<>();
		inputs.forEach((index, slotInput) -> selectedKeys.put(index, slotInput.selected()));
		selectionState.setSelectedKeys(selectedKeys);
		selectionState.apply(recipeLayout);
	}

	private static BookmarkIngredientKey createKey(ITypedIngredient<?> ingredient, IIngredientManager ingredientManager) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}
}
