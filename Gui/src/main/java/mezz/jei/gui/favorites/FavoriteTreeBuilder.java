package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FavoriteTreeBuilder {
	private final FavoriteRecipeStore favoriteRecipes;
	private final RecipeResolver recipeResolver;
	private final @Nullable SlotRuleResolver slotRuleResolver;

	public FavoriteTreeBuilder(FavoriteRecipeStore favoriteRecipes, RecipeResolver recipeResolver) {
		this(favoriteRecipes, recipeResolver, null);
	}

	public FavoriteTreeBuilder(
		FavoriteRecipeStore favoriteRecipes,
		RecipeResolver recipeResolver,
		@Nullable SlotRuleResolver slotRuleResolver
	) {
		this.favoriteRecipes = favoriteRecipes;
		this.recipeResolver = recipeResolver;
		this.slotRuleResolver = slotRuleResolver;
	}

	public FavoriteTreeResult build(FocusedRecipe root, int depth) {
		return build(root, depth, Map.of());
	}

	public FavoriteTreeResult build(
		FocusedRecipe root,
		int depth,
		Map<Integer, BookmarkIngredientKey> rootSelectedInputKeys
	) {
		Optional<ResolvedRecipe> rootRecipe = recipeResolver.resolve(root);
		if (rootRecipe.isEmpty()) {
			return new FavoriteTreeResult(List.of());
		}

		Map<Integer, BookmarkIngredientKey> normalizedRootSelectedInputKeys = rootSelectedInputKeys == null ?
			Map.of() :
			Map.copyOf(rootSelectedInputKeys);
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
				resolveTreeRecipe(
						root,
						recipe,
						normalizedRootSelectedInputKeys,
						visitedIngredients,
						visitedRecipes,
						localLoop,
						canAddChildren
					)
					.ifPresent(recipes::add);
			}
			loop = localLoop;
		}

		return new FavoriteTreeResult(recipes);
	}

	private Optional<FavoriteTreeRecipe> resolveTreeRecipe(
		FocusedRecipe root,
		ResolvedRecipe recipe,
		Map<Integer, BookmarkIngredientKey> rootSelectedInputKeys,
		Set<BookmarkIngredientKey> visitedIngredients,
		Set<FocusedRecipe> visitedRecipes,
		List<ResolvedRecipe> localLoop,
		boolean canAddChildren
	) {
		List<FavoriteTreeInput> inputs = recipe.inputs()
			.stream()
			.map(input -> resolveTreeInput(root, recipe, input, rootSelectedInputKeys))
			.toList();
		boolean complete = inputs.stream()
			.allMatch(input -> input.selectedFavoriteKey().isPresent());
		if (!complete) {
			return Optional.empty();
		}
		for (FavoriteTreeInput input : inputs) {
			input.selectedFavoriteKey().ifPresent(visitedIngredients::add);
			if (canAddChildren) {
				input.selectedFavoriteRecipe()
					.filter(visitedRecipes::add)
					.flatMap(recipeResolver::resolve)
					.ifPresent(localLoop::add);
			}
		}
		return Optional.of(new FavoriteTreeRecipe(recipe.recipe(), inputs, recipe.layout()));
	}

	private FavoriteTreeInput resolveTreeInput(
		FocusedRecipe root,
		ResolvedRecipe recipe,
		ResolvedInput input,
		Map<Integer, BookmarkIngredientKey> rootSelectedInputKeys
	) {
		BookmarkIngredientKey displayedKey = recipe.recipe().equals(root) ?
			rootSelectedInputKeys.getOrDefault(input.inputSlotIndex(), input.displayedKey()) :
			input.displayedKey();
		List<BookmarkIngredientKey> permutations = input.normalizedPermutationKeys();
		Optional<BookmarkIngredientKey> selectedKey;
		Optional<FocusedRecipe> selectedRecipe;
		if (permutations.size() > 1) {
			List<BookmarkIngredientKey> manualKeys = permutations.stream()
				.filter(key -> favoriteRecipes.getManualFavorite(key).isPresent())
				.toList();
			if (manualKeys.size() == 1) {
				BookmarkIngredientKey manualKey = manualKeys.getFirst();
				selectedKey = Optional.of(manualKey);
				selectedRecipe = favoriteRecipes.getManualFavorite(manualKey);
			} else {
				Optional<FocusedRecipe> ruleRecipe = slotRuleResolver == null ?
					Optional.empty() :
					slotRuleResolver.resolveSlot(permutations);
				if (ruleRecipe.isPresent()) {
					selectedKey = Optional.of(displayedKey);
					selectedRecipe = ruleRecipe;
				} else if (recipe.recipe().equals(root)) {
					selectedKey = Optional.of(displayedKey);
					selectedRecipe = favoriteRecipes.getFavorite(displayedKey);
				} else {
					selectedKey = Optional.empty();
					selectedRecipe = Optional.empty();
				}
			}
		} else {
			BookmarkIngredientKey singleKey = permutations.isEmpty() ?
				displayedKey :
				permutations.getFirst();
			selectedKey = Optional.of(singleKey);
			selectedRecipe = favoriteRecipes.getFavorite(singleKey);
		}

		return new FavoriteTreeInput(
			input.inputSlotIndex(),
			selectedKey,
			selectedRecipe
		);
	}

	@FunctionalInterface
	public interface RecipeResolver {
		Optional<ResolvedRecipe> resolve(FocusedRecipe recipe);
	}

	public record ResolvedRecipe(
		FocusedRecipe recipe,
		List<ResolvedInput> inputs,
		Optional<IRecipeLayoutDrawable<?>> layout
	) {
		public ResolvedRecipe {
			inputs = inputs == null ? List.of() : List.copyOf(inputs);
			layout = layout == null ? Optional.empty() : layout;
		}

		public ResolvedRecipe(FocusedRecipe recipe, List<ResolvedInput> inputs) {
			this(recipe, inputs, Optional.empty());
		}
	}

	public record ResolvedInput(
		int inputSlotIndex,
		BookmarkIngredientKey displayedKey,
		List<BookmarkIngredientKey> permutationKeys
	) {
		public ResolvedInput {
			inputSlotIndex = Math.max(0, inputSlotIndex);
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
		List<FavoriteTreeInput> inputs,
		Optional<IRecipeLayoutDrawable<?>> layout
	) {
		public FavoriteTreeRecipe {
			inputs = inputs == null ? List.of() : List.copyOf(inputs);
			layout = layout == null ? Optional.empty() : layout;
		}
	}

	public record FavoriteTreeInput(
		int inputSlotIndex,
		Optional<BookmarkIngredientKey> selectedFavoriteKey,
		Optional<FocusedRecipe> selectedFavoriteRecipe
	) {
		public FavoriteTreeInput {
			inputSlotIndex = Math.max(0, inputSlotIndex);
			selectedFavoriteKey = selectedFavoriteKey == null ? Optional.empty() : selectedFavoriteKey;
			selectedFavoriteRecipe = selectedFavoriteRecipe == null ? Optional.empty() : selectedFavoriteRecipe;
		}
	}
}
