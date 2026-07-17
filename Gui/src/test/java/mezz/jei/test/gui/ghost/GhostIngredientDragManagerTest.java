package mezz.jei.test.gui.ghost;

import mezz.jei.gui.ghost.GhostIngredientDragManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GhostIngredientDragManagerTest {
	@Test
	public void shouldRefreshHoverTargetsWhenScreenChanges() {
		Screen oldScreen = new TestScreen();
		Screen newScreen = new TestScreen();

		Assertions.assertTrue(GhostIngredientDragManager.shouldRefreshHoveredTargets(oldScreen, newScreen, null, null));
	}

	private static class TestScreen extends Screen {
		protected TestScreen() {
			super(Component.empty());
		}
	}
}
