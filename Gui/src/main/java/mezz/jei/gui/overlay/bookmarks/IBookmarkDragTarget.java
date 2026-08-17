package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.IBookmark;

import java.util.Optional;

public interface IBookmarkDragTarget {
	ImmutableRect2i getArea();
	void accept(IBookmark bookmark);

	default void accept(IBookmark bookmark, double mouseX, double mouseY) {
		accept(bookmark);
	}

	default Optional<BookmarkDragPreview> getPreview(IBookmark bookmark, double mouseX, double mouseY) {
		return Optional.of(new BookmarkDragPreview(getArea(), false));
	}
}
