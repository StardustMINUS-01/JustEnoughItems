package mezz.jei.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Stores bookmarked recipes by ingredient target, ported from JEI 1.21.1
 * ({@code mezz.jei.gui.favorites.FavoriteRecipeStore}). The 1.21.1
 * {@code IIngredientGridSource} listener mechanism is omitted in this port
 * because it is only needed by the GUI ingredient grid, which the 1.20.1
 * favorite-tree engine does not integrate with.
 */
public class FavoriteRecipeStore {
	private final Map<BookmarkIngredientKey, FocusedRecipe> recipesByTarget = new LinkedHashMap<>();
	private final Map<FocusedRecipe, BookmarkIngredientKey> targetsByRecipe = new LinkedHashMap<>();
	private final Map<FocusedRecipe, Map<Integer, FavoriteSlotInput>> inputsByRecipe = new LinkedHashMap<>();
	private final Map<BookmarkIngredientKey, FocusedRecipe> generatedRecipesByTarget = new LinkedHashMap<>();
	private final Map<FocusedRecipe, BookmarkIngredientKey> generatedTargetsByRecipe = new LinkedHashMap<>();
	private @Nullable BiFunction<BookmarkIngredientKey, RecipeLayoutBuildCache, Optional<FocusedRecipe>> generatedFavoriteResolver;

	public void setGeneratedFavoriteResolver(
		@Nullable Function<BookmarkIngredientKey, Optional<FocusedRecipe>> generatedFavoriteResolver
	) {
		this.generatedFavoriteResolver = generatedFavoriteResolver == null ?
			null :
			(key, layoutCache) -> generatedFavoriteResolver.apply(key);
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
	}

	public void removeFavorite(BookmarkIngredientKey target) {
		FocusedRecipe recipe = recipesByTarget.remove(target);
		if (recipe != null) {
			targetsByRecipe.remove(recipe);
			inputsByRecipe.remove(recipe);
		}
	}

	public void removeFavorite(FocusedRecipe recipe) {
		BookmarkIngredientKey target = targetsByRecipe.remove(recipe);
		if (target != null) {
			recipesByTarget.remove(target);
			inputsByRecipe.remove(recipe);
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

	public boolean containsFavorite(BookmarkIngredientKey target) {
		return getFavorite(target).isPresent();
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

	public void clear() {
		recipesByTarget.clear();
		targetsByRecipe.clear();
		inputsByRecipe.clear();
		generatedRecipesByTarget.clear();
		generatedTargetsByRecipe.clear();
	}

	public void clearGeneratedFavorites() {
		generatedRecipesByTarget.clear();
		generatedTargetsByRecipe.clear();
	}

	public boolean isEmpty() {
		return recipesByTarget.isEmpty();
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
			permutations = permutations == null || permutations.isEmpty() ?
				List.of(selected) :
				List.copyOf(permutations);
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
