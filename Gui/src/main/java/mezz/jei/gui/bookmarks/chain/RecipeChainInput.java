package mezz.jei.gui.bookmarks.chain;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import org.jetbrains.annotations.Nullable;

public record RecipeChainInput(
	int index,
	BookmarkItemMetadata metadata,
	@Nullable BookmarkIngredientKey selectedKey,
	@Nullable ITypedIngredient<?> selectedIngredient
) {
	public RecipeChainInput(int index, BookmarkItemMetadata metadata) {
		this(index, metadata, null, null);
	}

	public RecipeChainInput(int index, BookmarkItemMetadata metadata, @Nullable BookmarkIngredientKey selectedKey) {
		this(index, metadata, selectedKey, null);
	}
}
