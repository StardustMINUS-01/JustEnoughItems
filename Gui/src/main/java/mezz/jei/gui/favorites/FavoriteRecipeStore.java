package mezz.jei.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.input.FocusedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class FavoriteRecipeStore {
	private final Map<BookmarkIngredientKey, FocusedRecipe> recipesByTarget = new LinkedHashMap<>();
	private final Map<FocusedRecipe, BookmarkIngredientKey> targetsByRecipe = new LinkedHashMap<>();
	private final Map<FocusedRecipe, Map<Integer, FavoriteSlotInput>> inputsByRecipe = new LinkedHashMap<>();
	private final Map<BookmarkIngredientKey, FocusedRecipe> generatedRecipesByTarget = new LinkedHashMap<>();
	private final Map<FocusedRecipe, BookmarkIngredientKey> generatedTargetsByRecipe = new LinkedHashMap<>();
	private final List<IIngredientGridSource.SourceListChangedListener> listeners = new ArrayList<>();
	private @Nullable BiFunction<BookmarkIngredientKey, RecipeLayoutBuildCache, Optional<FocusedRecipe>> generatedFavoriteResolver;

	public void setGeneratedFavoriteResolver(
		@Nullable Function<BookmarkIngredientKey, Optional<FocusedRecipe>> generatedFavoriteResolver
	) {
		this.generatedFavoriteResolver = generatedFavoriteResolver == null ? null : (key, layoutCache) -> generatedFavoriteResolver.apply(key);
	}

	public void setGeneratedFavoriteResolver(
		@Nullable BiFunction<BookmarkIngredientKey, RecipeLayoutBuildCache, Optional<FocusedRecipe>> generatedFavoriteResolver
	) {
		this.generatedFavoriteResolver = generatedFavoriteResolver;
	}

	public void setFavorite(BookmarkIngredientKey target, FocusedRecipe recipe, Map<Integer, FavoriteSlotInput> inputs) {
		removeFavorite(target);
		removeFavorite(recipe);
		recipesByTarget.put(target, recipe);
		targetsByRecipe.put(recipe, target);
		inputsByRecipe.put(recipe, Map.copyOf(inputs));
		notifyListenersOfChange();
	}

	public void setFavorites(List<Entry> entries) {
		recipesByTarget.clear();
		targetsByRecipe.clear();
		inputsByRecipe.clear();
		for (Entry entry : entries) {
			recipesByTarget.put(entry.target(), entry.recipe());
			targetsByRecipe.put(entry.recipe(), entry.target());
			inputsByRecipe.put(entry.recipe(), entry.inputs());
		}
		notifyListenersOfChange();
	}

	public void removeFavorite(BookmarkIngredientKey target) {
		FocusedRecipe recipe = recipesByTarget.remove(target);
		if (recipe != null) {
			targetsByRecipe.remove(recipe);
			inputsByRecipe.remove(recipe);
			notifyListenersOfChange();
		}
	}

	public void removeFavorite(FocusedRecipe recipe) {
		BookmarkIngredientKey target = targetsByRecipe.remove(recipe);
		if (target != null) {
			recipesByTarget.remove(target);
			inputsByRecipe.remove(recipe);
			notifyListenersOfChange();
		}
	}

	public Optional<Entry> getManualEntry(FocusedRecipe recipe) {
		BookmarkIngredientKey target = targetsByRecipe.get(recipe);
		if (target == null) {
			return Optional.empty();
		}
		return Optional.of(new Entry(target, recipe, inputsByRecipe.getOrDefault(recipe, Map.of())));
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
		return getGeneratedFavorite(target, new RecipeLayoutBuildCache());
	}

	public Optional<FocusedRecipe> getGeneratedFavorite(
		BookmarkIngredientKey target,
		RecipeLayoutBuildCache layoutCache
	) {
		Optional<FocusedRecipe> cached = Optional.ofNullable(generatedRecipesByTarget.get(target));
		if (cached.isPresent()) {
			return cached;
		}
		if (generatedFavoriteResolver == null) {
			return Optional.empty();
		}
		Optional<FocusedRecipe> resolved = generatedFavoriteResolver.apply(target, layoutCache);
		if (resolved.isPresent()) {
			setGeneratedFavorite(target, resolved.get());
		}
		return resolved;
	}

	public Optional<FocusedRecipe> getFavorite(BookmarkIngredientKey target) {
		return getFavorite(target, new RecipeLayoutBuildCache());
	}

	public Optional<FocusedRecipe> getFavorite(
		BookmarkIngredientKey target,
		RecipeLayoutBuildCache layoutCache
	) {
		return getManualFavorite(target)
			.or(() -> getGeneratedFavorite(target, layoutCache));
	}

	public boolean containsManual(BookmarkIngredientKey target) {
		return recipesByTarget.containsKey(target);
	}

	public List<Entry> entries() {
		return recipesByTarget.entrySet()
			.stream()
			.map(entry -> new Entry(entry.getKey(), entry.getValue(), inputsByRecipe.getOrDefault(entry.getValue(), Map.of())))
			.toList();
	}

	public boolean cycleFavoriteInputs(FocusedRecipe recipe, FavoriteSlotInput slotInput, long step) {
		if (step == 0) {
			return false;
		}
		Map<Integer, FavoriteSlotInput> inputs = inputsByRecipe.get(recipe);
		if (inputs == null) {
			return false;
		}
		int index = slotInput.permutations().indexOf(slotInput.selected());
		if (index < 0 || slotInput.permutations().size() <= 1) {
			return false;
		}
		var selected = slotInput.permutations().get(Math.floorMod(index - (int) Math.signum(step), slotInput.permutations().size()));
		return selectFavoriteInputs(recipe, slotInput, selected, false);
	}

	public boolean selectFavoriteInputs(FocusedRecipe recipe, FavoriteSlotInput source, BookmarkIngredientKey selected, boolean synchronize) {
		var inputs = inputsByRecipe.get(recipe);
		if (inputs == null || !source.permutations().contains(selected)) {
			return false;
		}
		Map<Integer, FavoriteSlotInput> updated = new LinkedHashMap<>(inputs);
		for (var entry : inputs.entrySet()) {
			var input = entry.getValue();
			if (input.permutations().equals(source.permutations()) && (synchronize || input.selected().equals(source.selected()))) {
				updated.put(entry.getKey(), new FavoriteSlotInput(selected, input.permutations()));
			}
		}
		if (updated.equals(inputs)) {
			return false;
		}
		inputsByRecipe.put(recipe, Map.copyOf(updated));
		notifyListenersOfChange();
		return true;
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
		inputsByRecipe.clear();
		for (Entry entry : reordered) {
			recipesByTarget.put(entry.target(), entry.recipe());
			targetsByRecipe.put(entry.recipe(), entry.target());
			inputsByRecipe.put(entry.recipe(), entry.inputs());
		}
		notifyListenersOfChange();
		return true;
	}

	public void clear() {
		recipesByTarget.clear();
		targetsByRecipe.clear();
		inputsByRecipe.clear();
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

	public record FavoriteSlotInput(BookmarkIngredientKey selected, List<BookmarkIngredientKey> permutations) {
		public FavoriteSlotInput {
			permutations = permutations == null || permutations.isEmpty() ? List.of(selected) : List.copyOf(permutations);
		}
	}

	public record Entry(
		BookmarkIngredientKey target,
		FocusedRecipe recipe,
		Map<Integer, FavoriteSlotInput> inputs
	) {
		public Entry {
			inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
		}
	}
}
