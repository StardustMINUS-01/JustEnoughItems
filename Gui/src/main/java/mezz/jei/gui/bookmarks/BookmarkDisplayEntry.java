package mezz.jei.gui.bookmarks;

import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record BookmarkDisplayEntry<T>(
	T item,
	int sourceIndex,
	BookmarkItemMetadata metadata,
	BookmarkViewMode viewMode,
	Optional<ResourceLocation> displayRecipeUid,
	Optional<RecipeChainItem> recipeChainItem,
	boolean outputRecipe,
	boolean middleRecipe
) {
	public BookmarkDisplayEntry {
		viewMode = viewMode == null ? BookmarkViewMode.DEFAULT : viewMode;
		displayRecipeUid = displayRecipeUid == null ? Optional.empty() : displayRecipeUid;
		recipeChainItem = recipeChainItem == null ? Optional.empty() : recipeChainItem;
	}

	public boolean isOutputRecipe() {
		return outputRecipe;
	}

	public boolean isMiddleRecipe() {
		return middleRecipe;
	}
}
