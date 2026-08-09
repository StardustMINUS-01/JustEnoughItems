package mezz.jei.test.gui.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.bookmarks.BookmarkSortDragState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

public class BookmarkSortDragStateTest {
	@Test
	public void floatingGroupPanelSlotsUseSharedGroupLeftX() throws ReflectiveOperationException {
		BookmarkSortDragState state = BookmarkSortDragState.group("group", new ImmutableRect2i(100, 50, 16, 16), 100, 50);
		setPreviewSlots(state, List.of(
			previewSlot(0, 0),
			previewSlot(18, 18)
		));

		List<BookmarkSortDragState.FloatingGroupPanelSlot> floatingSlots = state.getFloatingGroupPanelSlots(100, 50);

		Assertions.assertEquals(2, floatingSlots.size());
		ImmutableRect2i first = floatingSlots.get(0).slotArea();
		ImmutableRect2i second = floatingSlots.get(1).slotArea();
		Assertions.assertEquals(first.getX(), second.getX());
	}

	private static Object previewSlot(int relativeX, int relativeY) throws ReflectiveOperationException {
		Class<?> previewSlotClass = Class.forName("mezz.jei.gui.overlay.bookmarks.BookmarkSortDragState$PreviewSlot");
		Constructor<?> constructor = previewSlotClass.getDeclaredConstructor(Object.class, int.class, int.class, int.class, int.class);
		constructor.setAccessible(true);
		return constructor.newInstance(null, relativeX, relativeY, 16, 16);
	}

	private static void setPreviewSlots(BookmarkSortDragState state, List<Object> previewSlots) throws ReflectiveOperationException {
		Field field = BookmarkSortDragState.class.getDeclaredField("previewSlots");
		field.setAccessible(true);
		field.set(state, previewSlots);
	}
}
