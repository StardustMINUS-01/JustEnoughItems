package mezz.jei.gui.bookmarks;

import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record BookmarkDisplayEntry<T>(
	T item,
	int sourceIndex,
	BookmarkItemMetadata metadata,
	BookmarkViewMode viewMode,
	Optional<ResourceLocation> displayRecipeUid,
	Optional<RecipeChainItem> recipeChainItem,
	boolean outputRecipe,
	boolean middleRecipe,
	@Nullable ResourceLocation collapsedBlockId,
	@Nullable BookmarkSlotBorder border
) {
	public BookmarkDisplayEntry {
		displayRecipeUid = displayRecipeUid == null ? Optional.empty() : displayRecipeUid;
		recipeChainItem = recipeChainItem == null ? Optional.empty() : recipeChainItem;
	}

	public BookmarkDisplayEntry(
		T item,
		int sourceIndex,
		BookmarkItemMetadata metadata,
		BookmarkViewMode viewMode,
		Optional<ResourceLocation> displayRecipeUid,
		Optional<RecipeChainItem> recipeChainItem,
		boolean outputRecipe,
		boolean middleRecipe
	) {
		this(item, sourceIndex, metadata, viewMode, displayRecipeUid, recipeChainItem, outputRecipe, middleRecipe, null, null);
	}

	public boolean isOutputRecipe() {
		return outputRecipe;
	}

	public boolean isMiddleRecipe() {
		return middleRecipe;
	}

	public BookmarkDisplayEntry<T> withBorder(@Nullable BookmarkSlotBorder border) {
		return new BookmarkDisplayEntry<>(
			item,
			sourceIndex,
			metadata,
			viewMode,
			displayRecipeUid,
			recipeChainItem,
			outputRecipe,
			middleRecipe,
			collapsedBlockId,
			border
		);
	}
}
