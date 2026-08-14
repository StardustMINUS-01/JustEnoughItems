package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;

/**
 * A resolved bookmark entry for a single recipe-tree recipe: the ingredient
 * to display together with its aggregated amount. One entry per unique
 * ingredient of the recipe (outputs first, then inputs), with the amount
 * summed across all slots of the recipe that contain the same ingredient,
 * mirroring the 1.21.1 {@code mergeRecipeInputs} semantics.
 */
public record RecipeTreeBookmarkEntry(
	ResourceLocation recipeUid,
	ITypedIngredient<?> ingredient,
	RecipeIngredientRole role,
	long amount
) {
	public RecipeTreeBookmarkEntry {
		amount = Math.max(1, amount);
		role = role == null ? RecipeIngredientRole.INPUT : role;
	}
}
