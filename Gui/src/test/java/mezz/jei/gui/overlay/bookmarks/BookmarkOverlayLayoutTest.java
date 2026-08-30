package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkDisplaySlot;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BookmarkOverlayLayoutTest {
	@Test
	public void projectedPanelStartsAtRenderedFirstItemIndex() {
		List<BookmarkDisplaySlot<String>> displaySlots = List.of(
			createDisplaySlot(54, "before"),
			createDisplaySlot(55, "first"),
			createDisplaySlot(56, "second")
		);
		List<ImmutableRect2i> pageAreas = IntStream.range(0, 45)
			.mapToObj(index -> new ImmutableRect2i(index * 18, 0, 18, 18))
			.toList();
		BookmarkOverlayLayout.PanelSnapshotKey key = new BookmarkOverlayLayout.PanelSnapshotKey(
			0,
			9,
			55,
			pageAreas,
			List.of()
		);

		List<BookmarkPanelLayout.PanelSlot<String>> projected = BookmarkOverlayLayout.createProjectedPanelSlots(
			displaySlots,
			pageAreas,
			key
		);

		assertEquals(List.of("first", "second"), projected.stream().map(BookmarkPanelLayout.PanelSlot::item).toList());
		assertEquals(pageAreas.subList(0, 2), projected.stream().map(BookmarkPanelLayout.PanelSlot::area).toList());
	}

	@Test
	public void panelSnapshotKeyChangesWhenSmoothScrollMovesSlotAreas() {
		BookmarkOverlayLayout.PanelSnapshotKey first = new BookmarkOverlayLayout.PanelSnapshotKey(
			0,
			9,
			55,
			List.of(new ImmutableRect2i(0, 0, 18, 18)),
			List.of()
		);
		BookmarkOverlayLayout.PanelSnapshotKey partiallyScrolled = new BookmarkOverlayLayout.PanelSnapshotKey(
			0,
			9,
			55,
			List.of(new ImmutableRect2i(0, -6, 18, 18)),
			List.of()
		);

		assertNotEquals(first, partiallyScrolled);
	}

	@Test
	public void visibleRowConnectsToAdjacentOffscreenRows() {
		List<BookmarkDisplaySlot<String>> displaySlots = List.of(
			createDisplaySlot(0, "previous-1", "group"),
			createDisplaySlot(1, "previous-2", "group"),
			createDisplaySlot(2, "visible-1", "group"),
			createDisplaySlot(3, "visible-2", "group"),
			createDisplaySlot(4, "next-1", "group"),
			createDisplaySlot(5, "next-2", "group")
		);
		ImmutableRect2i visibleArea = new ImmutableRect2i(0, 0, 18, 18);

		BookmarkOverlayLayout.BoundaryConnections result = BookmarkOverlayLayout.calculateBoundaryConnections(
			displaySlots,
			List.of(visibleArea, visibleArea.moveRight(18)),
			2,
			2,
			List.of(2),
			List.of(new BookmarkPanelLayout.RowSlot<>("visible-1", "group", visibleArea))
		);

		assertTrue(result.connectedToPrevious());
		assertTrue(result.connectedToNext());
	}

	@Test
	public void visibleRowDoesNotConnectAcrossGroupBoundaries() {
		List<BookmarkDisplaySlot<String>> displaySlots = List.of(
			createDisplaySlot(0, "previous", "before"),
			createDisplaySlot(2, "visible", "group"),
			createDisplaySlot(4, "next", "after")
		);
		ImmutableRect2i visibleArea = new ImmutableRect2i(0, 0, 18, 18);

		BookmarkOverlayLayout.BoundaryConnections result = BookmarkOverlayLayout.calculateBoundaryConnections(
			displaySlots,
			List.of(visibleArea, visibleArea.moveRight(18)),
			2,
			2,
			List.of(2),
			List.of(new BookmarkPanelLayout.RowSlot<>("visible", "group", visibleArea))
		);

		assertFalse(result.connectedToPrevious());
		assertFalse(result.connectedToNext());
	}

	@Test
	public void boundaryConnectionsUseVariableRowWidths() {
		List<BookmarkDisplaySlot<String>> displaySlots = List.of(
			createDisplaySlot(0, "previous-1", "group"),
			createDisplaySlot(1, "previous-2", "group"),
			createDisplaySlot(2, "visible", "group"),
			createDisplaySlot(3, "next-1", "group"),
			createDisplaySlot(4, "next-2", "group"),
			createDisplaySlot(5, "next-3", "group")
		);
		ImmutableRect2i visibleArea = new ImmutableRect2i(0, 0, 18, 18);

		BookmarkOverlayLayout.BoundaryConnections result = BookmarkOverlayLayout.calculateBoundaryConnections(
			displaySlots,
			List.of(visibleArea),
			2,
			3,
			List.of(2, 1, 3),
			List.of(new BookmarkPanelLayout.RowSlot<>("visible", "group", visibleArea))
		);

		assertTrue(result.connectedToPrevious());
		assertTrue(result.connectedToNext());
	}

	private static BookmarkDisplaySlot<String> createDisplaySlot(int slotIndex, String item) {
		return createDisplaySlot(slotIndex, item, "group");
	}

	private static BookmarkDisplaySlot<String> createDisplaySlot(int slotIndex, String item, String groupId) {
		BookmarkDisplayEntry<String> entry = new BookmarkDisplayEntry<>(
			item,
			slotIndex,
			BookmarkItemMetadata.defaultForGroup(groupId),
			BookmarkViewMode.DEFAULT,
			false,
			Optional.empty(),
			Optional.empty(),
			false,
			false
		);
		return new BookmarkDisplaySlot<>(slotIndex, entry, false, false);
	}
}
