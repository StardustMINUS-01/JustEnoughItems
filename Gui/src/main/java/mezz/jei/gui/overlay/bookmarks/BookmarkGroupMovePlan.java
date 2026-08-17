package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;

import java.util.Comparator;
import java.util.List;

public record BookmarkGroupMovePlan(
	String groupId,
	IBookmark targetBookmark,
	int offset,
	boolean rejected,
	boolean appendToEnd
) {
	public BookmarkGroupMovePlan(String groupId, IBookmark targetBookmark, int offset) {
		this(groupId, targetBookmark, offset, false, false);
	}

	public static BookmarkGroupMovePlan create(
		BookmarkPanelLayout.RowSlot<IBookmark> start,
		BookmarkPanelLayout.RowSlot<IBookmark> end
	) {
		return create(List.of(start, end), start, end);
	}

	public static BookmarkGroupMovePlan create(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		BookmarkPanelLayout.RowSlot<IBookmark> start,
		BookmarkPanelLayout.RowSlot<IBookmark> end
	) {
		rowSlots = rowSlots.stream()
			.sorted(Comparator.comparingInt(row -> row.area().getY()))
			.toList();
		int endIndex = rowSlots.indexOf(end);
		if (endIndex < 0) {
			return reject(start, end);
		}

		int targetIndex = endIndex;
		boolean sourceGroupAboveTarget = rowSlots.stream()
			.limit(endIndex)
			.anyMatch(row -> start.groupId().equals(row.groupId()));
		if (sourceGroupAboveTarget) {
			targetIndex++;
		}
		if (targetIndex >= rowSlots.size()) {
			BookmarkPanelLayout.RowSlot<IBookmark> lastRow = rowSlots.get(rowSlots.size() - 1);
			if (start.groupId().equals(lastRow.groupId())) {
				return reject(start, lastRow);
			}
			return appendToEnd(start, lastRow);
		}

		BookmarkPanelLayout.RowSlot<IBookmark> targetRow = rowSlots.get(targetIndex);
		if (start.groupId().equals(targetRow.groupId())) {
			return reject(start, targetRow);
		}
		if (isInsideNonDefaultGroup(rowSlots, targetIndex)) {
			return reject(start, targetRow);
		}
		if (!BookmarkGroupManager.DEFAULT_GROUP_ID.equals(targetRow.groupId())) {
			targetRow = findFirstRowInGroup(rowSlots, targetRow.groupId());
		}
		return new BookmarkGroupMovePlan(start.groupId(), targetRow.item(), 0);
	}

	public boolean apply(BookmarkList bookmarkList) {
		if (rejected || BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
			return false;
		}
		if (appendToEnd) {
			return bookmarkList.moveGroupToEnd(groupId);
		}
		return bookmarkList.moveGroupToBookmark(groupId, targetBookmark, offset);
	}

	private static boolean isInsideNonDefaultGroup(List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots, int rowIndex) {
		if (rowIndex <= 0 || rowIndex >= rowSlots.size()) {
			return false;
		}
		String groupId = rowSlots.get(rowIndex).groupId();
		return !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId) &&
			groupId.equals(rowSlots.get(rowIndex - 1).groupId());
	}

	private static BookmarkPanelLayout.RowSlot<IBookmark> findFirstRowInGroup(
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
		String groupId
	) {
		return rowSlots.stream()
			.filter(row -> groupId.equals(row.groupId()))
			.findFirst()
			.orElseThrow();
	}

	private static BookmarkGroupMovePlan reject(
		BookmarkPanelLayout.RowSlot<IBookmark> start,
		BookmarkPanelLayout.RowSlot<IBookmark> target
	) {
		return new BookmarkGroupMovePlan(start.groupId(), target.item(), 0, true, false);
	}

	private static BookmarkGroupMovePlan appendToEnd(
		BookmarkPanelLayout.RowSlot<IBookmark> start,
		BookmarkPanelLayout.RowSlot<IBookmark> target
	) {
		return new BookmarkGroupMovePlan(start.groupId(), target.item(), 0, false, true);
	}
}
