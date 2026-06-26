package mezz.jei.gui.input;

import java.util.Optional;

public final class FocusedRecipeResolver {
	private FocusedRecipeResolver() {
	}

	public static Optional<FocusedRecipe> resolveCraftingLookup(
		Optional<FocusedRecipeCandidate> recipeGui,
		Optional<FocusedRecipeCandidate> bookmark,
		Optional<FocusedRecipeCandidate> panel
	) {
		return recipeGui
			.or(() -> bookmark)
			.or(() -> panel)
			.map(FocusedRecipeCandidate::recipe);
	}

	public static Optional<FocusedRecipe> resolveHotkey(
		Optional<FocusedRecipeCandidate> bookmark,
		Optional<FocusedRecipeCandidate> panel,
		Optional<FocusedRecipeCandidate> recipeGui
	) {
		return firstNonIngredientBookmark(bookmark)
			.or(() -> firstNonIngredientBookmark(panel))
			.or(() -> firstNonIngredientBookmark(recipeGui));
	}

	private static Optional<FocusedRecipe> firstNonIngredientBookmark(Optional<FocusedRecipeCandidate> candidate) {
		return candidate
			.filter(c -> !c.ingredientBookmark())
			.map(FocusedRecipeCandidate::recipe);
	}
}
