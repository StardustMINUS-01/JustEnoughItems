package mezz.jei.gui.config;

import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.IBookmark;
import org.jetbrains.annotations.Nullable;

public record BookmarkConfigEntry(
	@Nullable BookmarkGroup group,
	@Nullable IBookmark bookmark,
	@Nullable BookmarkItemMetadata metadata
) {
	public static BookmarkConfigEntry group(BookmarkGroup group) {
		return new BookmarkConfigEntry(group, null, null);
	}

	public static BookmarkConfigEntry bookmark(IBookmark bookmark, BookmarkItemMetadata metadata) {
		return new BookmarkConfigEntry(null, bookmark, metadata);
	}
}
