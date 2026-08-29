package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BookmarkSortDragStateTest {
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
