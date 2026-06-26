package mezz.jei.gui.recipes;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public class RecipeBookmarkButtonHotkeyInputHandler implements IUserInputHandler {
	private final BiPredicate<Double, Double> isMouseOver;
	private final BiFunction<UserInput, Boolean, Boolean> addRecipeBookmarkGroup;
	private final Runnable playClickSound;

	public RecipeBookmarkButtonHotkeyInputHandler(
		BiPredicate<Double, Double> isMouseOver,
		BiFunction<UserInput, Boolean, Boolean> addRecipeBookmarkGroup,
		Runnable playClickSound
	) {
		this.isMouseOver = isMouseOver;
		this.addRecipeBookmarkGroup = addRecipeBookmarkGroup;
		this.playClickSound = playClickSound;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (!isBookmarkRecipeHotkey(input, keyBindings) || !isMouseOver.test(input.getMouseX(), input.getMouseY())) {
			return Optional.empty();
		}

		boolean preserveAmount = hasControl(input);
		if (!addRecipeBookmarkGroup.apply(input, preserveAmount)) {
			return Optional.empty();
		}
		if (!input.isSimulate()) {
			playClickSound.run();
		}
		return Optional.of(this);
	}

	private static boolean isBookmarkRecipeHotkey(UserInput input, IInternalKeyMappings keyBindings) {
		return hasShift(input) &&
			!hasAlt(input) &&
			keyBindings.getBookmark().matchesIgnoringModifiers(input.getKey());
	}

	private static boolean hasShift(UserInput input) {
		return (input.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
	}

	private static boolean hasControl(UserInput input) {
		return (input.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
	}

	private static boolean hasAlt(UserInput input) {
		return (input.getModifiers() & GLFW.GLFW_MOD_ALT) != 0;
	}
}
