package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;

import java.util.List;
import java.util.Set;

public record BookmarkGroupingPlan(
	List<IBookmark> bookmarks,
	int targetGroupId,
	boolean exclude,
	List<IBookmark> releasedBookmarks
) {
	public BookmarkGroupingPlan {
		bookmarks = List.copyOf(bookmarks);
		releasedBookmarks = List.copyOf(releasedBookmarks);
	}

	public static BookmarkGroupingPlan create(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> start,
		BookmarkPanelLayout.RowSlot<IBookmark> end,
		boolean exclude
	) {
		List<IBookmark> bookmarks = BookmarkPanelLayout.getItemsBetweenRecipeBounds(panelSlots, start, end);
		List<IBookmark> releasedBookmarks = getReleasedBookmarks(panelSlots, start, end, bookmarks, exclude);
		int targetGroupId = exclude ? BookmarkGroupManager.DEFAULT_GROUP_ID : start.groupId();
		return new BookmarkGroupingPlan(bookmarks, targetGroupId, exclude, releasedBookmarks);
	}

	public boolean apply(BookmarkList bookmarkList, String newGroupTitle) {
		List<IBookmark> expandedBookmarks = bookmarkList.expandToRecipeBlocks(bookmarks);
		if (expandedBookmarks.isEmpty()) {
			return false;
		}
		if (exclude) {
			return bookmarkList.moveBookmarksToGroup(expandedBookmarks, BookmarkGroupManager.DEFAULT_GROUP_ID);
		}
		if (targetGroupId == BookmarkGroupManager.DEFAULT_GROUP_ID) {
			bookmarkList.createGroupForBookmarks(newGroupTitle, expandedBookmarks);
			return true;
		}
		boolean changed = bookmarkList.moveBookmarksToGroup(expandedBookmarks, targetGroupId);
		if (!releasedBookmarks.isEmpty()) {
			Set<IBookmark> alreadyMoved = Set.copyOf(expandedBookmarks);
			List<IBookmark> expandedReleasedBookmarks = bookmarkList.expandToRecipeBlocks(releasedBookmarks).stream()
				.filter(bookmark -> !alreadyMoved.contains(bookmark))
				.toList();
			if (!expandedReleasedBookmarks.isEmpty()) {
				changed = bookmarkList.moveBookmarksToGroup(expandedReleasedBookmarks, BookmarkGroupManager.DEFAULT_GROUP_ID) || changed;
			}
		}
		return changed;
	}

	private static List<IBookmark> getReleasedBookmarks(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> start,
		BookmarkPanelLayout.RowSlot<IBookmark> end,
		List<IBookmark> selectedBookmarks,
		boolean exclude
	) {
		if (exclude || start.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID) {
			return List.of();
		}

		Set<IBookmark> selected = Set.copyOf(selectedBookmarks);
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rows = BookmarkPanelLayout.createRowSlots(panelSlots);
		int endIndex = indexOfRow(rows, end);
		if (endIndex < 0) {
			return List.of();
		}

		List<IBookmark> released = new java.util.ArrayList<>();
		int direction = Integer.compare(end.area().getY(), start.area().getY());
		if (direction == 0) {
			return List.of();
		}
		if (direction > 0) {
			for (int i = endIndex + 1; i < rows.size(); i++) {
				if (start.groupId() != rows.get(i).groupId()) {
					break;
				}
				addRowItems(panelSlots, rows.get(i), selected, released);
			}
		} else {
			for (int i = endIndex - 1; i >= 0; i--) {
				if (start.groupId() != rows.get(i).groupId()) {
					break;
				}
				addRowItems(panelSlots, rows.get(i), selected, released);
			}
		}
		return released.stream().distinct().toList();
	}

	private static int indexOfRow(List<BookmarkPanelLayout.RowSlot<IBookmark>> rows, BookmarkPanelLayout.RowSlot<IBookmark> target) {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).area().getY() == target.area().getY()) {
				return i;
			}
		}
		return -1;
	}

	private static void addRowItems(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> row,
		Set<IBookmark> selected,
		List<IBookmark> released
	) {
		for (IBookmark bookmark : BookmarkPanelLayout.getItemsBetweenRecipeBounds(panelSlots, row, row)) {
			if (!selected.contains(bookmark)) {
				released.add(bookmark);
			}
		}
	}
}
