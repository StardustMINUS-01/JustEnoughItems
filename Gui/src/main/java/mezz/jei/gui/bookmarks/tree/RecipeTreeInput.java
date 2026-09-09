package mezz.jei.gui.bookmarks.tree;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.UserInput;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

public enum RecipeTreeInput {
	SELECT, TOGGLE, SHOW_RECIPE, SHOW_USES, BOOKMARK;

	public static Optional<RecipeTreeInput> click(int button, boolean shift) {
		return button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? Optional.of(shift ? TOGGLE : SELECT) : Optional.empty();
	}

	public static Optional<RecipeTreeInput> key(UserInput input, IInternalKeyMappings keys) {
		int modifiers = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
		if ((input.getModifiers() & modifiers) != 0) {
			return Optional.empty();
		}
		if (input.is(keys.getShowRecipe())) {
			return Optional.of(SHOW_RECIPE);
		}
		if (input.is(keys.getShowUses())) {
			return Optional.of(SHOW_USES);
		}
		if (input.is(keys.getBookmark())) {
			return Optional.of(BOOKMARK);
		}
		return Optional.empty();
	}
}
