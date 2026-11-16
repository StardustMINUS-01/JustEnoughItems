package mezz.jei.test.gui;

import mezz.jei.api.gui.tooltip.IInteractiveClientTooltipComponent;
import mezz.jei.api.gui.tooltip.ITooltipInteractionContext;
import mezz.jei.api.gui.tooltip.TooltipCloseReason;
import mezz.jei.api.gui.tooltip.TooltipPointerResult;
import mezz.jei.gui.input.TooltipInteractionSession;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipInteractionSessionTest {
	@Test
	void capturesOutsideAndDeliversOneRelease() {
		Component component = new Component();
		var session = session(component, true);
		draw(session, 20, 30);
		assertTrue(session.mousePressed(25, 35, 0, 1));
		assertTrue(component.context.isMouseCaptured());
		assertTrue(session.mouseDragged(-10, 200, 0, -35, 165));
		assertEquals(-30, component.context.getMouseX());
		assertEquals(170, component.context.getMouseY());
		assertTrue(session.mouseReleased(-10, 200, 0, 1));
		assertFalse(session.mouseReleased(-10, 200, 0, 1));
		assertEquals(1, component.drags);
		assertEquals(1, component.releases);
		assertFalse(component.context.isMouseCaptured());
	}

	@Test
	void closeCancelsCaptureAndSwallowsLateRelease() {
		Component component = new Component();
		var session = session(component, true);
		draw(session, 20, 30);
		session.mousePressed(25, 35, 0, 0);
		session.close(TooltipCloseReason.PIN_RELEASED);
		session.close(TooltipCloseReason.SCREEN_CHANGED);
		assertEquals(1, component.cancelled);
		assertEquals(1, component.closed);
		assertEquals(0, component.releases);
		assertFalse(component.context.isActive());
		assertFalse(component.context.isPinned());
		assertTrue(session.mouseReleased(25, 35, 0, 0));
		assertEquals(0, component.releases);
	}

	@Test
	void focusNavigationAndCharacterInputAreIsolated() {
		Component first = new Component(), second = new Component();
		var session = new TooltipInteractionSession(List.of(first, second), null, true);
		session.mount();
		session.beginFrame(200, 200, 0, 0);
		session.components().get(0).renderImage(null, 20, 30, null);
		session.components().get(1).renderImage(null, 20, 60, null);
		assertTrue(session.keyPressed(258, 0, 0));
		assertTrue(first.context.isFocused());
		assertTrue(session.charTyped('x', 0));
		assertEquals(1, first.characters);
		session.keyPressed(258, 0, 0);
		assertTrue(second.context.isFocused());
		assertFalse(first.context.isFocused());
		session.keyPressed(258, 0, 1);
		assertTrue(first.context.isFocused());
		assertTrue(session.keyPressed(81, 0, 0));
		assertTrue(session.keyReleased(81, 0, 0));
	}

	@Test
	void hoverIsPassiveAndLayoutInvalidatesHitRegions() {
		Component hover = new Component();
		var passive = session(hover, false);
		draw(passive, 20, 30);
		assertFalse(passive.mousePressed(25, 35, 0, 0));
		assertFalse(hover.context.isPinned());
		assertEquals(0, hover.mounted);
		Component pinned = new Component();
		var active = session(pinned, true);
		draw(active, 20, 30);
		pinned.context.requestLayout();
		assertFalse(active.mousePressed(25, 35, 0, 0));
		draw(active, 100, 100);
		assertFalse(active.mousePressed(25, 35, 0, 0));
		assertTrue(active.mousePressed(105, 105, 0, 0));
	}

	@Test
	void clippedCoordinatesAndUnhandledScrollDoNotReachBackground() {
		Component component = new Component();
		var session = session(component, true);
		draw(session, -10, -10);
		assertEquals(0, component.context.getVisibleBounds().left());
		assertEquals(30, component.context.getVisibleBounds().width());
		assertFalse(session.mouseScrolled(-5, 0, 0, 1));
		assertTrue(session.mouseScrolled(5, 5, 0, 1));
	}

	private static TooltipInteractionSession session(Component component, boolean pinned) {
		var session = new TooltipInteractionSession(List.of(component), null, pinned);
		session.mount();
		return session;
	}

	private static void draw(TooltipInteractionSession session, int x, int y) {
		session.beginFrame(200, 200, 0, 0);
		session.components().get(0).renderImage(null, x, y, null);
	}

	private static final class Component implements IInteractiveClientTooltipComponent {
		ITooltipInteractionContext context;
		int mounted, closed, cancelled, drags, releases, characters;
		@Override
		public int getWidth(Font font) { return 40; }
		@Override
		public int getHeight() { return 30; }
		@Override
		public void renderImage(Font font, int x, int y, GuiGraphics graphics) {}
		@Override
		public void onLayout(ITooltipInteractionContext context) { this.context = context; }
		@Override
		public void onMounted(ITooltipInteractionContext context) { mounted++; }
		@Override
		public void onClosed(ITooltipInteractionContext context, TooltipCloseReason reason) { closed++; }
		@Override
		public void onInteractionCancelled(ITooltipInteractionContext context) { cancelled++; }
		@Override
		public boolean isFocusable() { return true; }
		@Override
		public TooltipPointerResult mousePressed(ITooltipInteractionContext context, int button, int modifiers) { return TooltipPointerResult.CAPTURE; }
		@Override
		public boolean mouseDragged(ITooltipInteractionContext context, int button, double dx, double dy) { drags++; return true; }
		@Override
		public boolean mouseReleased(ITooltipInteractionContext context, int button, int modifiers) { releases++; return true; }
		@Override
		public boolean charTyped(ITooltipInteractionContext context, char character, int modifiers) { characters++; return true; }
	}
}
