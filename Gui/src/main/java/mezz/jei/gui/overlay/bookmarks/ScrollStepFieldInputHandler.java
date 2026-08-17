package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

public class ScrollStepFieldInputHandler implements IUserInputHandler {
	private final ScrollStepTextField textField;

	public ScrollStepFieldInputHandler(ScrollStepTextField textField) {
		this.textField = textField;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (input.is(keyBindings.getEnterKey()) || input.is(keyBindings.getEscapeKey())) {
			return handleSetFocused(input, false);
		}
		if (input.callVanilla(
			textField::isMouseOver,
			textField::mouseClicked,
			textField::keyPressed
		)) {
			handleSetFocused(input, true);
			return Optional.of(this);
		}
		if (textField.canConsumeInput() && input.isAllowedChatCharacter()) {
			return Optional.of(this);
		}
		return Optional.empty();
	}

	private Optional<IUserInputHandler> handleSetFocused(UserInput input, boolean focused) {
		if (textField.isFocused() != focused) {
			if (!input.isSimulate()) {
				textField.setFocused(focused);
				if (!focused) {
					textField.syncFromScrollStep();
				}
			}
			return Optional.of(this);
		}
		return Optional.empty();
	}

	@Override
	public void unfocus() {
		textField.setFocused(false);
		textField.syncFromScrollStep();
	}
}
