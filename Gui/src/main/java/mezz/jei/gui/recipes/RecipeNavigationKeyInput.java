package mezz.jei.gui.recipes;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.recipes.navigation.RecipeNavigationDirection;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

final class RecipeNavigationKeyInput {
	public record Action(RecipeNavigationDirection direction, boolean jumpToEnd) {
	}

	private RecipeNavigationKeyInput() {
	}

	public static Optional<Action> getAction(UserInput input) {
		if (input.getKey().getType() != InputConstants.Type.KEYSYM) {
			return Optional.empty();
		}

		int modifiers = input.getModifiers();
		if (!InputModifiers.hasAlt(modifiers) || InputModifiers.hasControl(modifiers)) {
			return Optional.empty();
		}

		RecipeNavigationDirection direction = switch (input.getKey().getValue()) {
			case GLFW.GLFW_KEY_LEFT -> RecipeNavigationDirection.BACK;
			case GLFW.GLFW_KEY_RIGHT -> RecipeNavigationDirection.FORWARD;
			default -> null;
		};
		return Optional.ofNullable(direction)
			.map(value -> new Action(value, InputModifiers.hasShift(modifiers)));
	}
}
