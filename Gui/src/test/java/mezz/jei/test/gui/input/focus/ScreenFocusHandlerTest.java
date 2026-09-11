package mezz.jei.test.gui.input.focus;

import mezz.jei.gui.input.focus.ScreenFocusHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScreenFocusHandlerTest {
	@Test
	public void preservesExcludedFocus() {
		TestScreen screen = new TestScreen();
		TestListener searchField = new TestListener();
		searchField.setFocused(true);
		screen.setFocused(searchField);

		ScreenFocusHandler handler = ScreenFocusHandler.create(screen, searchField);

		Assertions.assertNull(handler);
		Assertions.assertTrue(searchField.isFocused());
		Assertions.assertNotNull(ScreenFocusHandler.create(screen));
	}

	private static final class TestScreen extends Screen {
		private TestScreen() {
			super(Component.empty());
		}
	}

	private static final class TestListener implements GuiEventListener {
		private boolean focused;

		@Override
		public void setFocused(boolean focused) {
			this.focused = focused;
		}

		@Override
		public boolean isFocused() {
			return focused;
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return false;
		}
	}
}
