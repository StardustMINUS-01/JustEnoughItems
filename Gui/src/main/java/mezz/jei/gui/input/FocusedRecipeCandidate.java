package mezz.jei.gui.input;

import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record FocusedRecipeCandidate(
	FocusedRecipe recipe,
	boolean ingredientBookmark
) {
	public static FocusedRecipeCandidate recipe(FocusedRecipe recipe) {
		return new FocusedRecipeCandidate(recipe, false);
	}

	public static FocusedRecipeCandidate ingredientBookmark(FocusedRecipe recipe) {
		return new FocusedRecipeCandidate(recipe, true);
	}

	public static Optional<FocusedRecipeCandidate> fromBookmarkMetadata(BookmarkItemMetadata metadata) {
		if (!metadata.type().isRecipeAssociated()) {
			return Optional.empty();
		}
		ResourceLocation recipeTypeUid = metadata.recipeTypeUid();
		ResourceLocation recipeUid = metadata.recipeUid();
		if (recipeTypeUid == null || recipeUid == null) {
			return Optional.empty();
		}
		FocusedRecipe recipe = new FocusedRecipe(recipeTypeUid, recipeUid);
		if (metadata.type().recipeRole() == RecipeIngredientRole.INPUT) {
			return Optional.of(ingredientBookmark(recipe));
		}
		return Optional.of(recipe(recipe));
	}
}
