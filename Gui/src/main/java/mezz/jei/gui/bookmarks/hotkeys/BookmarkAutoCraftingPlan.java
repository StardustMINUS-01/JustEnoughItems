package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.FocusedRecipeCandidate;

import java.util.Optional;
import java.util.OptionalInt;

public record BookmarkAutoCraftingPlan(
	FocusedRecipe recipe,
	int targetQuantity,
	Optional<BookmarkIngredientKey> activePermutation
) {
	public BookmarkAutoCraftingPlan {
		targetQuantity = Math.max(1, targetQuantity);
		activePermutation = activePermutation == null ? Optional.empty() : activePermutation;
	}

	public static Optional<BookmarkAutoCraftingPlan> fromFocusedRecipe(
		Optional<FocusedRecipeCandidate> candidate,
		OptionalInt targetQuantity,
		Optional<BookmarkIngredientKey> activePermutation
	) {
		return candidate
			.filter(c -> !c.ingredientBookmark())
			.map(FocusedRecipeCandidate::recipe)
			.map(recipe -> new BookmarkAutoCraftingPlan(
				recipe,
				targetQuantity.orElse(1),
				activePermutation
			));
	}

	public static Optional<BookmarkAutoCraftingPlan> fromBookmarkInput(RecipeChainInput input) {
		BookmarkItemMetadata metadata = input.metadata();
		if (metadata.type() != BookmarkItemType.RESULT) {
			return Optional.empty();
		}
		if (metadata.recipeTypeUid() == null || metadata.recipeUid() == null) {
			return Optional.empty();
		}
		return Optional.of(new BookmarkAutoCraftingPlan(
			new FocusedRecipe(metadata.recipeTypeUid(), metadata.recipeUid()),
			toIntQuantity(metadata.multiplier()),
			Optional.ofNullable(input.selectedKey())
		));
	}

	private static int toIntQuantity(long quantity) {
		if (quantity > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int) quantity;
	}
}
