package mezz.jei.gui.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.recipes.FilteredRecipeSlotView;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record RecipeLayoutProjection(
	IRecipeLayoutDrawable<?> layout,
	Optional<BookmarkIngredientKey> selectedOutputKey,
	Map<Integer, BookmarkIngredientKey> selectedInputKeys,
	Map<Integer, List<ITypedIngredient<?>>> filteredInputCandidates
) {
	public RecipeLayoutProjection {
		selectedOutputKey = selectedOutputKey == null ? Optional.empty() : selectedOutputKey;
		selectedInputKeys = selectedInputKeys == null ? Map.of() : Map.copyOf(selectedInputKeys);
		filteredInputCandidates = Map.copyOf(filteredInputCandidates);
	}

	public RecipeLayoutProjection(IRecipeLayoutDrawable<?> layout) {
		this(layout, Optional.empty(), Map.of(), Map.of());
	}

	public RecipeLayoutProjection(IRecipeLayoutDrawable<?> layout, Map<Integer, BookmarkIngredientKey> selectedInputKeys) {
		this(layout, Optional.empty(), selectedInputKeys, Map.of());
	}

	public RecipeLayoutProjection(
		IRecipeLayoutDrawable<?> layout,
		Map<Integer, BookmarkIngredientKey> selectedInputKeys,
		Map<Integer, List<ITypedIngredient<?>>> filteredInputCandidates
	) {
		this(layout, Optional.empty(), selectedInputKeys, filteredInputCandidates);
	}

	public RecipeLayoutProjection(
		IRecipeLayoutDrawable<?> layout,
		Optional<BookmarkIngredientKey> selectedOutputKey,
		Map<Integer, BookmarkIngredientKey> selectedInputKeys
	) {
		this(layout, selectedOutputKey, selectedInputKeys, Map.of());
	}

	public Optional<BookmarkIngredientKey> selectedInputKey(int inputSlotIndex) {
		return Optional.ofNullable(selectedInputKeys.get(inputSlotIndex));
	}

	public IRecipeSlotsView getRecipeSlotsView() {
		if (filteredInputCandidates.isEmpty()) {
			return layout.getRecipeSlotsView();
		}
		List<IRecipeSlotView> slots = layout.getRecipeSlotsView().getSlotViews();
		java.util.ArrayList<IRecipeSlotView> projected = new java.util.ArrayList<>(slots.size());
		int inputSlotIndex = 0;
		for (IRecipeSlotView slot : slots) {
			if (slot.getRole() == RecipeIngredientRole.INPUT) {
				List<ITypedIngredient<?>> candidates = filteredInputCandidates.get(inputSlotIndex++);
				if (candidates != null) {
					projected.add(new FilteredRecipeSlotView(slot, candidates));
					continue;
				}
			}
			projected.add(slot);
		}
		List<IRecipeSlotView> immutable = List.copyOf(projected);
		return () -> immutable;
	}
}
