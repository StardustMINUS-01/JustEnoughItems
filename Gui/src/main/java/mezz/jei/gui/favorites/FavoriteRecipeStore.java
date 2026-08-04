package mezz.jei.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FavoriteRecipeStore {
	private final Map<BookmarkIngredientKey, FocusedRecipe> recipesByTarget = new LinkedHashMap<>();
	private final Map<FocusedRecipe, BookmarkIngredientKey> targetsByRecipe = new LinkedHashMap<>();
	private final Map<BookmarkIngredientKey, FocusedRecipe> generatedRecipesByTarget = new LinkedHashMap<>();
	private final Map<FocusedRecipe, BookmarkIngredientKey> generatedTargetsByRecipe = new LinkedHashMap<>();
	private final List<IIngredientGridSource.SourceListChangedListener> listeners = new ArrayList<>();

	public void setFavorite(BookmarkIngredientKey target, FocusedRecipe recipe) {
		removeFavorite(target);
		removeFavorite(recipe);
		recipesByTarget.put(target, recipe);
		targetsByRecipe.put(recipe, target);
		notifyListenersOfChange();
	}

	public void removeFavorite(BookmarkIngredientKey target) {
		FocusedRecipe recipe = recipesByTarget.remove(target);
		if (recipe != null) {
			targetsByRecipe.remove(recipe);
			notifyListenersOfChange();
		}
	}

	public void removeFavorite(FocusedRecipe recipe) {
		BookmarkIngredientKey target = targetsByRecipe.remove(recipe);
		if (target != null) {
			recipesByTarget.remove(target);
			notifyListenersOfChange();
		}
	}

	public Optional<FocusedRecipe> getManualFavorite(BookmarkIngredientKey target) {
		return Optional.ofNullable(recipesByTarget.get(target));
	}

	public Optional<BookmarkIngredientKey> getManualFavorite(FocusedRecipe recipe) {
		return Optional.ofNullable(targetsByRecipe.get(recipe));
	}

	public void setGeneratedFavorite(BookmarkIngredientKey target, FocusedRecipe recipe) {
		removeGeneratedFavorite(target);
		removeGeneratedFavorite(recipe);
		generatedRecipesByTarget.put(target, recipe);
		generatedTargetsByRecipe.put(recipe, target);
	}

	public Optional<FocusedRecipe> getGeneratedFavorite(BookmarkIngredientKey target) {
		return Optional.ofNullable(generatedRecipesByTarget.get(target));
	}

	public Optional<FocusedRecipe> getFavorite(BookmarkIngredientKey target) {
		return getManualFavorite(target)
			.or(() -> getGeneratedFavorite(target));
	}

	public boolean containsFavorite(BookmarkIngredientKey target) {
		return getFavorite(target).isPresent();
	}

	public boolean containsManual(BookmarkIngredientKey target) {
		return recipesByTarget.containsKey(target);
	}

	public List<Entry> entries() {
		return recipesByTarget.entrySet()
			.stream()
			.map(entry -> new Entry(entry.getKey(), entry.getValue()))
			.toList();
	}

	public boolean moveFavorite(FocusedRecipe sourceRecipe, FocusedRecipe targetRecipe, int offset) {
		if (sourceRecipe.equals(targetRecipe)) {
			return false;
		}
		if (!targetsByRecipe.containsKey(sourceRecipe) || !targetsByRecipe.containsKey(targetRecipe)) {
			return false;
		}

		List<Entry> oldEntries = entries();
		List<Entry> reordered = new ArrayList<>(oldEntries);
		Optional<Entry> sourceEntry = reordered.stream()
			.filter(entry -> entry.recipe().equals(sourceRecipe))
			.findFirst();
		if (sourceEntry.isEmpty()) {
			return false;
		}
		reordered.remove(sourceEntry.get());

		int targetIndex = -1;
		for (int i = 0; i < reordered.size(); i++) {
			if (reordered.get(i).recipe().equals(targetRecipe)) {
				targetIndex = i;
				break;
			}
		}
		if (targetIndex < 0) {
			return false;
		}
		int insertionIndex = Math.max(0, Math.min(reordered.size(), targetIndex + offset));
		reordered.add(insertionIndex, sourceEntry.get());
		if (reordered.equals(oldEntries)) {
			return false;
		}

		recipesByTarget.clear();
		targetsByRecipe.clear();
		for (Entry entry : reordered) {
			recipesByTarget.put(entry.target(), entry.recipe());
			targetsByRecipe.put(entry.recipe(), entry.target());
		}
		notifyListenersOfChange();
		return true;
	}

	public void clear() {
		recipesByTarget.clear();
		targetsByRecipe.clear();
		generatedRecipesByTarget.clear();
		generatedTargetsByRecipe.clear();
		notifyListenersOfChange();
	}

	public void clearGeneratedFavorites() {
		generatedRecipesByTarget.clear();
		generatedTargetsByRecipe.clear();
	}

	public boolean isEmpty() {
		return recipesByTarget.isEmpty();
	}

	public void addSourceListChangedListener(IIngredientGridSource.SourceListChangedListener listener) {
		listeners.add(listener);
	}

	private void notifyListenersOfChange() {
		for (IIngredientGridSource.SourceListChangedListener listener : listeners) {
			listener.onSourceListChanged();
		}
	}

	private void removeGeneratedFavorite(BookmarkIngredientKey target) {
		FocusedRecipe recipe = generatedRecipesByTarget.remove(target);
		if (recipe != null) {
			generatedTargetsByRecipe.remove(recipe);
		}
	}

	private void removeGeneratedFavorite(FocusedRecipe recipe) {
		BookmarkIngredientKey target = generatedTargetsByRecipe.remove(recipe);
		if (target != null) {
			generatedRecipesByTarget.remove(target);
		}
	}

	public record Entry(BookmarkIngredientKey target, FocusedRecipe recipe) {
	}
}
