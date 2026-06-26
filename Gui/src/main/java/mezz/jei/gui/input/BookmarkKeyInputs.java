package mezz.jei.gui.input;

import mezz.jei.common.input.IInternalKeyMappings;
import org.lwjgl.glfw.GLFW;

public final class BookmarkKeyInputs {
	private BookmarkKeyInputs() {
	}

	public static boolean isPlainBookmarkKey(UserInput input, IInternalKeyMappings keyBindings) {
		return !hasAnyModifier(input.getModifiers()) &&
			input.is(keyBindings.getBookmark());
	}

	public static boolean isShiftBookmarkKey(UserInput input, IInternalKeyMappings keyBindings) {
		int modifiers = input.getModifiers();
		return hasShift(modifiers) &&
			!hasControlOrAlt(modifiers) &&
			keyBindings.getBookmark().matchesIgnoringModifiers(input.getKey());
	}

	public static boolean isBookmarkKeyWithoutControlOrAlt(UserInput input, IInternalKeyMappings keyBindings) {
		return !hasControlOrAlt(input.getModifiers()) &&
			keyBindings.getBookmark().matchesIgnoringModifiers(input.getKey());
	}

	private static boolean hasAnyModifier(int modifiers) {
		return (modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0;
	}

	private static boolean hasShift(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
	}

	private static boolean hasControlOrAlt(int modifiers) {
		return (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0;
	}
}
