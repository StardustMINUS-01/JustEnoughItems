package mezz.jei.gui.favorites;

import mezz.jei.gui.input.FocusedRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class FavoriteRecipePanelState {
	public record RecipeInputKey(String ingredientTypeUid, String ingredientUid) {
	}

	public enum DisplayMode {
		GRID_RESULTS,
		RECIPE_ROWS
	}

	private enum SelectedPanel {
		BOOKMARK,
		FAVORITE
	}

	private boolean favoritePanelEnabled;
	private SelectedPanel selectedPanel = SelectedPanel.BOOKMARK;
	private DisplayMode displayMode = DisplayMode.GRID_RESULTS;
	private final Set<FocusedRecipe> collapsedRecipeRows = new HashSet<>();
	private final Map<FocusedRecipe, List<RecipeInputKey>> recipeInputOrders = new HashMap<>();
	private final Set<FocusedRecipe> hiddenSortDragRecipes = new HashSet<>();
	private final Map<FocusedRecipe, Set<RecipeInputKey>> hiddenSortDragInputs = new HashMap<>();
	private final List<Runnable> displayStateChangedListeners = new ArrayList<>();

	public boolean isFavoritePanelVisible() {
		return favoritePanelEnabled && selectedPanel == SelectedPanel.FAVORITE;
	}

	public boolean isBookmarkPanelVisible() {
		return selectedPanel == SelectedPanel.BOOKMARK;
	}

	public DisplayMode displayMode() {
		return displayMode;
	}

	public boolean handleFavoritePanelButtonClick(boolean shiftDown) {
		if (shiftDown) {
			return cycleDisplayMode();
		}
		toggleFavoritePanel();
		return true;
	}

	public void toggleFavoritePanel() {
		if (isFavoritePanelVisible()) {
			favoritePanelEnabled = false;
			showBookmarkPanel();
		} else {
			favoritePanelEnabled = true;
			selectedPanel = SelectedPanel.FAVORITE;
		}
	}

	public void showBookmarkPanel() {
		selectedPanel = SelectedPanel.BOOKMARK;
	}

	public void showFavoritePanel() {
		favoritePanelEnabled = true;
		selectedPanel = SelectedPanel.FAVORITE;
	}

	public void updateFavoritePanelAvailability(boolean hasFavorites) {
		if (!hasFavorites) {
			favoritePanelEnabled = false;
			showBookmarkPanel();
		}
	}

	public boolean cycleDisplayMode() {
		if (!isFavoritePanelVisible()) {
			return false;
		}
		displayMode = displayMode == DisplayMode.GRID_RESULTS ? DisplayMode.RECIPE_ROWS : DisplayMode.GRID_RESULTS;
		notifyDisplayStateChanged();
		return true;
	}

	public boolean toggleRecipeRowCollapsed(FocusedRecipe recipe) {
		if (!isFavoritePanelVisible() || displayMode != DisplayMode.RECIPE_ROWS) {
			return false;
		}
		if (!collapsedRecipeRows.add(recipe)) {
			collapsedRecipeRows.remove(recipe);
		}
		notifyDisplayStateChanged();
		return true;
	}

	public boolean isRecipeRowCollapsed(FocusedRecipe recipe) {
		return collapsedRecipeRows.contains(recipe);
	}

	public boolean moveRecipeInput(
		FocusedRecipe recipe,
		List<RecipeInputKey> currentOrder,
		RecipeInputKey sourceInput,
		RecipeInputKey targetInput,
		int offset
	) {
		if (sourceInput.equals(targetInput)) {
			return false;
		}
		List<RecipeInputKey> reordered = new ArrayList<>(currentOrder);
		if (!reordered.remove(sourceInput)) {
			return false;
		}
		int targetIndex = reordered.indexOf(targetInput);
		if (targetIndex < 0) {
			return false;
		}
		int insertionIndex = Math.max(0, Math.min(reordered.size(), targetIndex + offset));
		reordered.add(insertionIndex, sourceInput);
		if (reordered.equals(currentOrder)) {
			return false;
		}
		recipeInputOrders.put(recipe, List.copyOf(reordered));
		notifyDisplayStateChanged();
		return true;
	}

	public List<RecipeInputKey> orderRecipeInputs(FocusedRecipe recipe, List<RecipeInputKey> inputs) {
		return orderRecipeInputs(recipe, inputs, Function.identity());
	}

	public <T> List<T> orderRecipeInputs(FocusedRecipe recipe, List<T> inputs, Function<T, RecipeInputKey> keyFactory) {
		List<RecipeInputKey> storedOrder = recipeInputOrders.get(recipe);
		if (storedOrder == null || storedOrder.isEmpty() || inputs.isEmpty()) {
			return List.copyOf(inputs);
		}
		List<T> ordered = new ArrayList<>();
		Set<RecipeInputKey> addedKeys = new HashSet<>();
		for (RecipeInputKey orderedKey : storedOrder) {
			inputs.stream()
				.filter(input -> orderedKey.equals(keyFactory.apply(input)))
				.findFirst()
				.ifPresent(input -> {
					ordered.add(input);
					addedKeys.add(orderedKey);
				});
		}
		for (T input : inputs) {
			if (!addedKeys.contains(keyFactory.apply(input))) {
				ordered.add(input);
			}
		}
		return List.copyOf(ordered);
	}

	public boolean setSortDragHiddenRecipe(FocusedRecipe recipe) {
		boolean changed = !hiddenSortDragRecipes.equals(Set.of(recipe)) || !hiddenSortDragInputs.isEmpty();
		hiddenSortDragRecipes.clear();
		hiddenSortDragRecipes.add(recipe);
		hiddenSortDragInputs.clear();
		if (changed) {
			notifyDisplayStateChanged();
		}
		return changed;
	}

	public boolean setSortDragHiddenInput(FocusedRecipe recipe, RecipeInputKey inputKey) {
		Set<RecipeInputKey> inputKeys = Set.of(inputKey);
		boolean changed = !hiddenSortDragRecipes.isEmpty() || !inputKeys.equals(hiddenSortDragInputs.get(recipe)) || hiddenSortDragInputs.size() != 1;
		hiddenSortDragRecipes.clear();
		hiddenSortDragInputs.clear();
		hiddenSortDragInputs.put(recipe, inputKeys);
		if (changed) {
			notifyDisplayStateChanged();
		}
		return changed;
	}

	public boolean clearSortDragHiddenElements() {
		boolean changed = !hiddenSortDragRecipes.isEmpty() || !hiddenSortDragInputs.isEmpty();
		hiddenSortDragRecipes.clear();
		hiddenSortDragInputs.clear();
		if (changed) {
			notifyDisplayStateChanged();
		}
		return changed;
	}

	public boolean isSortDragHidden(FocusedRecipe recipe, boolean favoriteTarget, Optional<RecipeInputKey> inputKey) {
		if (hiddenSortDragRecipes.contains(recipe)) {
			return true;
		}
		if (favoriteTarget || inputKey.isEmpty()) {
			return false;
		}
		return hiddenSortDragInputs.getOrDefault(recipe, Set.of()).contains(inputKey.get());
	}

	public void addDisplayStateChangedListener(Runnable listener) {
		displayStateChangedListeners.add(listener);
	}

	private void notifyDisplayStateChanged() {
		for (Runnable listener : displayStateChangedListeners) {
			listener.run();
		}
	}
}
