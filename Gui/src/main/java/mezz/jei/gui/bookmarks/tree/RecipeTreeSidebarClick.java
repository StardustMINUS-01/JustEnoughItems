package mezz.jei.gui.bookmarks.tree;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;

/** Keeps sidebar lookups on release, without turning a drag or scroll into navigation. */
final class RecipeTreeSidebarClick<T> {
	private static final double DRAG_DISTANCE_SQUARED = 16;
	private @Nullable Press<T> press;
	private boolean cancelled;

	boolean press(@Nullable Object target, T ingredient, double x, double y, int button, boolean modified) {
		reset();
		if (target == null || modified || (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
			return false;
		}
		press = new Press<>(target, ingredient, x, y, button);
		return true;
	}

	void move(double x, double y) {
		if (press != null && distanceSquared(press, x, y) > DRAG_DISTANCE_SQUARED) {
			cancel();
		}
	}

	void cancel() {
		cancelled = true;
	}

	void reset() {
		press = null;
		cancelled = false;
	}

	boolean release(@Nullable Object target, double x, double y, int button, BiConsumer<T, RecipeTreeInput> lookup) {
		var pressed = press;
		if (pressed == null || button != pressed.button()) {
			return false;
		}
		boolean activate = !cancelled && target == pressed.target() && distanceSquared(pressed, x, y) <= DRAG_DISTANCE_SQUARED;
		reset();
		if (activate) {
			lookup.accept(pressed.ingredient(), button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? RecipeTreeInput.SHOW_RECIPE : RecipeTreeInput.SHOW_USES);
		}
		// Consume the release even when cancelled, so it cannot activate another control.
		return true;
	}

	private static double distanceSquared(Press<?> press, double x, double y) {
		double dx = x - press.x(), dy = y - press.y();
		return dx * dx + dy * dy;
	}

	private record Press<T>(Object target, T ingredient, double x, double y, int button) {}
}
