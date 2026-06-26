package mezz.jei.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class FavoriteTreeBuilder {
	private final FavoriteRecipeStore favoriteRecipes;
	private final RecipeResolver recipeResolver;

	public FavoriteTreeBuilder(FavoriteRecipeStore favoriteRecipes, RecipeResolver recipeResolver) {
		this.favoriteRecipes = favoriteRecipes;
		this.recipeResolver = recipeResolver;
	}

	public FavoriteTreeResult build(FocusedRecipe root, int depth) {
		Optional<ResolvedRecipe> rootRecipe = recipeResolver.resolve(root);
		if (rootRecipe.isEmpty()) {
			return new FavoriteTreeResult(List.of());
		}

		List<FavoriteTreeRecipe> recipes = new ArrayList<>();
		List<ResolvedRecipe> loop = new ArrayList<>();
		Set<BookmarkIngredientKey> visitedIngredients = new LinkedHashSet<>();
		Set<FocusedRecipe> visitedRecipes = new LinkedHashSet<>();

		loop.add(rootRecipe.get());
		visitedRecipes.add(root);

		int remainingDepth = depth;
		while (!loop.isEmpty() && remainingDepth-- >= 0) {
			boolean canAddChildren = remainingDepth >= 0;
			List<ResolvedRecipe> localLoop = new ArrayList<>();
			for (ResolvedRecipe recipe : loop) {
				recipes.add(resolveTreeRecipe(recipe, visitedIngredients, visitedRecipes, localLoop, canAddChildren));
			}
			loop = localLoop;
		}

		return new FavoriteTreeResult(recipes);
	}

	private FavoriteTreeRecipe resolveTreeRecipe(
		ResolvedRecipe recipe,
		Set<BookmarkIngredientKey> visitedIngredients,
		Set<FocusedRecipe> visitedRecipes,
		List<ResolvedRecipe> localLoop,
		boolean canAddChildren
	) {
		List<FavoriteTreeInput> inputs = recipe.inputs()
			.stream()
			.map(input -> resolveTreeInput(input, visitedIngredients, visitedRecipes, localLoop, canAddChildren))
			.toList();
		return new FavoriteTreeRecipe(recipe.recipe(), inputs);
	}

	private FavoriteTreeInput resolveTreeInput(
		ResolvedInput input,
		Set<BookmarkIngredientKey> visitedIngredients,
		Set<FocusedRecipe> visitedRecipes,
		List<ResolvedRecipe> localLoop,
		boolean canAddChildren
	) {
		List<BookmarkIngredientKey> permutations = input.normalizedPermutationKeys();
		Optional<BookmarkIngredientKey> selectedKey = permutations.stream()
			.filter(visitedIngredients::contains)
			.findFirst();

		if (selectedKey.isEmpty()) {
			selectedKey = findFavoriteKey(input.displayedKey(), permutations);
		}

		Optional<FocusedRecipe> selectedRecipe = selectedKey.flatMap(favoriteRecipes::getFavorite);
		selectedKey.ifPresent(visitedIngredients::add);
		if (canAddChildren && selectedRecipe.isPresent() && visitedRecipes.add(selectedRecipe.get())) {
			recipeResolver.resolve(selectedRecipe.get())
				.ifPresent(localLoop::add);
		}

		int activePermutationIndex = selectedKey
			.map(key -> activePermutationIndex(permutations, key))
			.orElseGet(() -> activePermutationIndex(permutations, input.displayedKey()));

		return new FavoriteTreeInput(
			input.displayedKey(),
			permutations,
			selectedKey,
			selectedRecipe,
			activePermutationIndex
		);
	}

	private Optional<BookmarkIngredientKey> findFavoriteKey(BookmarkIngredientKey displayedKey, List<BookmarkIngredientKey> permutations) {
		if (favoriteRecipes.containsFavorite(displayedKey)) {
			return Optional.of(displayedKey);
		}
		return permutations.stream()
			.filter(favoriteRecipes::containsFavorite)
			.findFirst();
	}

	private static int activePermutationIndex(List<BookmarkIngredientKey> permutations, BookmarkIngredientKey selectedKey) {
		int index = permutations.indexOf(selectedKey);
		return Math.max(index, 0);
	}

	@FunctionalInterface
	public interface RecipeResolver {
		Optional<ResolvedRecipe> resolve(FocusedRecipe recipe);
	}

	public record ResolvedRecipe(
		FocusedRecipe recipe,
		List<ResolvedInput> inputs
	) {
		public ResolvedRecipe {
			inputs = inputs == null ? List.of() : List.copyOf(inputs);
		}
	}

	public record ResolvedInput(
		BookmarkIngredientKey displayedKey,
		List<BookmarkIngredientKey> permutationKeys
	) {
		public ResolvedInput {
			permutationKeys = permutationKeys == null ? List.of() : List.copyOf(permutationKeys);
		}

		private List<BookmarkIngredientKey> normalizedPermutationKeys() {
			if (permutationKeys.isEmpty()) {
				return List.of(displayedKey);
			}
			return permutationKeys;
		}
	}

	public record FavoriteTreeResult(
		List<FavoriteTreeRecipe> recipes
	) {
		public FavoriteTreeResult {
			recipes = recipes == null ? List.of() : List.copyOf(recipes);
		}
	}

	public record FavoriteTreeRecipe(
		FocusedRecipe recipe,
		List<FavoriteTreeInput> inputs
	) {
		public FavoriteTreeRecipe {
			inputs = inputs == null ? List.of() : List.copyOf(inputs);
		}
	}

	public record FavoriteTreeInput(
		BookmarkIngredientKey displayedKey,
		List<BookmarkIngredientKey> permutationKeys,
		Optional<BookmarkIngredientKey> selectedFavoriteKey,
		Optional<FocusedRecipe> selectedFavoriteRecipe,
		int activePermutationIndex
	) {
		public FavoriteTreeInput {
			permutationKeys = permutationKeys == null ? List.of() : List.copyOf(permutationKeys);
			selectedFavoriteKey = selectedFavoriteKey == null ? Optional.empty() : selectedFavoriteKey;
			selectedFavoriteRecipe = selectedFavoriteRecipe == null ? Optional.empty() : selectedFavoriteRecipe;
		}
	}
}
