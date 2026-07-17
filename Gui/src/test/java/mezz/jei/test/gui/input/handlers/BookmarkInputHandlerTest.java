package mezz.jei.test.gui.input.handlers;

import mezz.jei.gui.input.handlers.BookmarkInputHandler;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BookmarkInputHandlerTest {
	@Test
	public void shiftCraftsAll() {
		assertTrue(BookmarkInputHandler.isCraftAllModifier(GLFW.GLFW_MOD_SHIFT));
	}

	@Test
	public void controlShiftCraftsMissing() {
		assertFalse(BookmarkInputHandler.isCraftAllModifier(GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT));
	}
}
