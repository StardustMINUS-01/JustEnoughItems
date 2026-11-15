package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;

public record FavoriteRecipeOverlay(
	FavoriteRecipeStore recipes,
	FavoriteRecipePanelState panelState,
	IngredientGridWithNavigation contents
) {
}
