package mezz.jei.gui.input;

import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class InputModifiers {
	private InputModifiers() {
	}

	public static boolean isCheatGiveInput(UserInput input, IJeiKeyMapping cheatItemStack, boolean cheatItemsEnabled) {
		return cheatItemsEnabled && input.is(cheatItemStack);
	}

	public static boolean hasShift(UserInput input) {
		return Screen.hasShiftDown() || hasShift(input.getModifiers());
	}

	public static boolean hasControl(UserInput input) {
		return Screen.hasControlDown() || (input.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
	}

	public static boolean hasAlt(UserInput input) {
		return Screen.hasAltDown() || (input.getModifiers() & GLFW.GLFW_MOD_ALT) != 0;
	}

	public static boolean hasShift(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
	}

	public static boolean hasControl(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
	}

	public static boolean hasAlt(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_ALT) != 0;
	}

	public static boolean hasShiftOrControl(int modifiers) {
		return (modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL)) != 0;
	}

	public static boolean hasControlOrAlt(int modifiers) {
		return (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0;
	}

	public static boolean hasAnyModifier(int modifiers) {
		return (modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0;
	}
}
