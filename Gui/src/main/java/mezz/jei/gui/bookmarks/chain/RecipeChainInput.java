package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import org.jetbrains.annotations.Nullable;

public record RecipeChainInput(
	int index,
	BookmarkItemMetadata metadata,
	@Nullable BookmarkIngredientKey selectedKey
) {
	public RecipeChainInput(int index, BookmarkItemMetadata metadata) {
		this(index, metadata, null);
	}
}
