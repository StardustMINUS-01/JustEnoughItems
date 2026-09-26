package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BookmarkSortDragStateTest {
	@Test
	public void plainDragRequiresDelayLeavingSlotAndDistance() {
		var area = new ImmutableRect2i(0, 0, 16, 16);
		var drag = BookmarkSortDragState.delayedItem(null, area, 8, 8, 1000, 250);
		drag.update(null, List.of(), 30, 8, 1249);
		Assertions.assertFalse(drag.isActive());
		drag.update(null, List.of(), 8, 8, 2000);
		Assertions.assertFalse(drag.isActive());
		drag.update(null, List.of(), 16, 8, 2000);
		Assertions.assertFalse(drag.isActive());
		drag.update(null, List.of(), 17, 8, 2000);
		Assertions.assertTrue(drag.isActive());
	}

	@Test
	public void shiftDragStillStartsOnLeavingSlotWithoutWaiting() {
		var drag = BookmarkSortDragState.item(null, new ImmutableRect2i(0, 0, 16, 16), 8, 8, 1000, 250);
		drag.update(null, List.of(), 17, 8, 1001);
		Assertions.assertTrue(drag.isActive());
	}

	@Test
	public void quickStationaryClickNeverActivatesPlainDrag() {
		var drag = BookmarkSortDragState.delayedItem(null, new ImmutableRect2i(0, 0, 16, 16), 8, 8, 1000, 0);
		drag.update(null, List.of(), 8, 8, 1000);
		Assertions.assertFalse(drag.isActive());
		drag.stop();
		Assertions.assertFalse(drag.isActive());
	}
	@Test
	public void floatingGroupPanelSlotsUseSharedGroupLeftX() {
		List<BookmarkSortDragState.PreviewSlot<?>> previewSlots = List.of(
			new BookmarkSortDragState.PreviewSlot<>(null, 0, 0, 16, 16),
			new BookmarkSortDragState.PreviewSlot<>(null, 18, 18, 16, 16)
		);

		List<BookmarkSortDragState.FloatingGroupPanelSlot> floatingSlots = BookmarkSortDragState.getFloatingGroupPanelSlots(
			previewSlots,
			100,
			50,
			0,
			0
		);

		Assertions.assertEquals(2, floatingSlots.size());
		ImmutableRect2i first = floatingSlots.get(0).slotArea();
		ImmutableRect2i second = floatingSlots.get(1).slotArea();
		Assertions.assertEquals(first.getX(), second.getX());
	}
}
