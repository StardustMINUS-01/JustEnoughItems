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
