package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BookmarkOverlayScrollStepAreaTest {
	@Test
	public void scrollStepAreaSitsRightOfFavoriteButtonAndAlignsRightEdge() {
		ImmutableRect2i favoriteButtonArea = new ImmutableRect2i(50, 100, 20, 20);

		ImmutableRect2i area = BookmarkOverlay.calculateScrollStepArea(favoriteButtonArea, 194);

		assertEquals(72, area.getX());
		assertEquals(100, area.getY());
		assertEquals(122, area.getWidth());
		assertEquals(20, area.getHeight());
		assertEquals(194, area.getX() + area.getWidth());
	}

	@Test
	public void scrollStepAreaIsEmptyWhenNoRoom() {
		ImmutableRect2i favoriteButtonArea = new ImmutableRect2i(50, 100, 20, 20);

		ImmutableRect2i area = BookmarkOverlay.calculateScrollStepArea(favoriteButtonArea, 60);

		assertEquals(0, area.getWidth());
	}

	@Test
	public void historyAreaShiftsRightByGroupPanelWidth() {
		BookmarkOverlay.LayoutAreas layoutAreas = BookmarkOverlay.calculateLayoutAreas(
			new ImmutableRect2i(0, 0, 200, 300),
			true,
			3
		);
		ImmutableRect2i historyArea = layoutAreas.historyArea().orElseThrow();

		assertEquals(13, historyArea.getX());
		assertEquals(194, historyArea.getX() + historyArea.getWidth());
	}
}
