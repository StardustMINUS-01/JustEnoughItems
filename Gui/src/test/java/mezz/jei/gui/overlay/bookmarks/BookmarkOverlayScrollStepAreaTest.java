package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BookmarkOverlayScrollStepAreaTest {
	@Test
	public void toolbarButtonsPlaceFavoriteThenHistoryAfterBookmark() {
		ImmutableRect2i bookmarkButtonArea = new ImmutableRect2i(6, 100, 20, 20);

		ImmutableRect2i favoriteButtonArea = BookmarkOverlay.calculateFavoritePanelButtonArea(bookmarkButtonArea);
		ImmutableRect2i historyButtonArea = BookmarkOverlay.calculateHistoryButtonArea(favoriteButtonArea);

		assertEquals(28, favoriteButtonArea.getX());
		assertEquals(50, historyButtonArea.getX());
	}

	@Test
	public void scrollStepAreaSitsRightOfHistoryButtonAndAlignsRightEdge() {
		ImmutableRect2i historyButtonArea = new ImmutableRect2i(50, 100, 20, 20);

		ImmutableRect2i area = BookmarkOverlay.calculateScrollStepArea(historyButtonArea, 194);

		assertEquals(72, area.getX());
		assertEquals(100, area.getY());
		assertEquals(123, area.getWidth());
		assertEquals(20, area.getHeight());
		assertEquals(195, area.getX() + area.getWidth());
	}

	@Test
	public void scrollStepAreaIsEmptyWhenNoRoom() {
		ImmutableRect2i historyButtonArea = new ImmutableRect2i(50, 100, 20, 20);

		ImmutableRect2i area = BookmarkOverlay.calculateScrollStepArea(historyButtonArea, 60);

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
