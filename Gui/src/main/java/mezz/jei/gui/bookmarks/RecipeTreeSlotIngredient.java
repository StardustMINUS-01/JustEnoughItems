package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;

/**
 * A single slot ingredient extracted from a recipe layout of the favorite
 * recipe tree, ported from JEI 1.21.1
 * ({@code mezz.jei.gui.bookmarks.BookmarkList#createRecipeBookmarkEntries}).
 * Decouples the tree-entry resolution logic from {@code IRecipeLayoutDrawable}
 * so it can be unit tested without a client runtime.
 */
public record RecipeTreeSlotIngredient(
	ResourceLocation recipeUid,
	RecipeIngredientRole role,
	ITypedIngredient<?> ingredient
) {
	public RecipeTreeSlotIngredient {
		role = role == null ? RecipeIngredientRole.INPUT : role;
	}
}
