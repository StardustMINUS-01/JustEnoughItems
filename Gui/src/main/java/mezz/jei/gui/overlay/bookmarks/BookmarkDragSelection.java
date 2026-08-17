package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record BookmarkDragSelection(
	List<IBookmark> bookmarks,
	List<PreviewSlot> previewSlots
) {
	public BookmarkDragSelection {
		bookmarks = List.copyOf(bookmarks);
		previewSlots = List.copyOf(previewSlots);
	}

	public static BookmarkDragSelection create(
		BookmarkList bookmarkList,
		IBookmark sourceBookmark,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		ImmutableRect2i origin
	) {
		List<IBookmark> bookmarks = List.of(sourceBookmark);
		Set<IBookmark> bookmarkSet = new HashSet<>(bookmarks);
		List<PreviewSlot> previewSlots = panelSlots.stream()
			.filter(slot -> bookmarkSet.contains(slot.item()))
			.map(slot -> new PreviewSlot(
				slot.item(),
				slot.area().getX() - origin.getX(),
				slot.area().getY() - origin.getY()
			))
			.toList();
		if (previewSlots.isEmpty()) {
			previewSlots = List.of(new PreviewSlot(sourceBookmark, 0, 0));
		}
		return new BookmarkDragSelection(bookmarks, previewSlots);
	}

	public record PreviewSlot(IBookmark bookmark, int relativeX, int relativeY) {
	}
}
