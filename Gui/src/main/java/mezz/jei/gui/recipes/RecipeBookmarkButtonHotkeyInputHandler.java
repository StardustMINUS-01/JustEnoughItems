package mezz.jei.gui.recipes;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;

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

		boolean preserveAmount = InputModifiers.hasControl(input.getModifiers());
		if (!addRecipeBookmarkGroup.apply(input, preserveAmount)) {
			return Optional.empty();
		}
		if (!input.isSimulate()) {
			playClickSound.run();
		}
		return Optional.of(this);
	}

	private static boolean isBookmarkRecipeHotkey(UserInput input, IInternalKeyMappings keyBindings) {
		return InputModifiers.hasShift(input.getModifiers()) &&
			!InputModifiers.hasAlt(input.getModifiers()) &&
			keyBindings.getBookmark().matchesIgnoringModifiers(input.getKey());
	}
}
