package mezz.jei.test.gui.input.focus;

import mezz.jei.gui.input.focus.ScreenFocusHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScreenFocusHandlerTest {
	@Test
	public void excludesTheFieldThatIsTakingFocus() {
		TestScreen screen = new TestScreen();
		TestGuiEventListener searchField = new TestGuiEventListener();
		searchField.setFocused(true);
		screen.setFocused(searchField);

		ScreenFocusHandler focusHandler = ScreenFocusHandler.create(screen, searchField);

		Assertions.assertNull(focusHandler);
		Assertions.assertTrue(searchField.isFocused());
	}

	private static final class TestScreen extends Screen {
		private TestScreen() {
			super(Component.empty());
		}
	}

	private static final class TestGuiEventListener implements GuiEventListener {
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
