package mezz.jei.gui.input;

import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class InputModifiers {
	private InputModifiers() {
	}

	public static boolean hasShift(UserInput input) {
		return Screen.hasShiftDown() || (input.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
	}

	public static boolean hasControl(UserInput input) {
		return Screen.hasControlDown() || (input.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
	}

	public static boolean hasAlt(UserInput input) {
		return Screen.hasAltDown() || (input.getModifiers() & GLFW.GLFW_MOD_ALT) != 0;
	}
}
