package mezz.jei.gui.recipes;

import mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import mezz.jei.common.input.interaction.ConsumedInput;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.common.input.UserInput;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public class RecipeBookmarkButtonHotkeyInputHandler implements IInputTarget {
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
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (!isBookmarkRecipeHotkey(input, keyBindings) || !isMouseOver.test(input.getMouseX(), input.getMouseY())) {
			return Optional.empty();
		}

		boolean preserveAmount = InputModifiers.hasControl(input.getModifiers());
		if (!addRecipeBookmarkGroup.apply(input, preserveAmount)) {
			return Optional.empty();
		}
		if (input.isMouseInput()) {
			return Optional.of(new InputAction(release -> {
					if (addRecipeBookmarkGroup.apply(release, preserveAmount)) {
						playClickSound.run();
					}
				})
				.within(isMouseOver::test));
		}
		playClickSound.run();
		return Optional.of(ConsumedInput.INSTANCE);
	}

	private static boolean isBookmarkRecipeHotkey(UserInput input, IInternalKeyMappings keyBindings) {
		return InputModifiers.hasShift(input.getModifiers()) &&
			!InputModifiers.hasAlt(input.getModifiers()) &&
			IJeiKeyMappingWithExtraModifiers.matchesShortcut(keyBindings.getBookmark(), input.getKey());
	}
}
