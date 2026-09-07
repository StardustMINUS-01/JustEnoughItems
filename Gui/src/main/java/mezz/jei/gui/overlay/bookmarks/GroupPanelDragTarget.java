package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;

import java.util.Optional;

class GroupPanelDragTarget implements IBookmarkDragTarget {
	private final ImmutableRect2i area;
	private final IBookmark targetBookmark;
	private final int targetGroupId;
	private final BookmarkList bookmarkList;

	public GroupPanelDragTarget(ImmutableRect2i area, IBookmark targetBookmark, int targetGroupId, BookmarkList bookmarkList) {
		this.area = area;
		this.targetBookmark = targetBookmark;
		this.targetGroupId = targetGroupId;
		this.bookmarkList = bookmarkList;
	}

	@Override
	public ImmutableRect2i getArea() {
		return area;
	}

	@Override
	public void accept(IBookmark bookmark) {
		// GTNH NEI edits bookmark groups through bracket drags, not by dropping bookmarks on the bracket strip.
	}

	@Override
	public Optional<BookmarkDragPreview> getPreview(IBookmark bookmark, double mouseX, double mouseY) {
		return Optional.of(new BookmarkDragPreview(area, true));
	}
}
