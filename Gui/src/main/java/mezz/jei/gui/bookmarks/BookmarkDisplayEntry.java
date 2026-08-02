package mezz.jei.gui.bookmarks;

import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record BookmarkDisplayEntry<T>(
	T item,
	int sourceIndex,
	BookmarkItemMetadata metadata,
	boolean newLine,
	boolean resultOnly,
	Optional<ResourceLocation> displayRecipeUid,
	Optional<RecipeChainItem> recipeChainItem,
	boolean outputRecipe,
	boolean middleRecipe
) {
	public BookmarkDisplayEntry {
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
