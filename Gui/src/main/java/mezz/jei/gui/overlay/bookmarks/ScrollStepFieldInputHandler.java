package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IGuiInputLayer;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.ConsumedInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import mezz.jei.gui.input.ITextInputHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

public class ScrollStepFieldInputHandler implements IGuiInputLayer, ITextInputHandler {
	private final ScrollStepTextField textField;

	public ScrollStepFieldInputHandler(ScrollStepTextField textField) {
		this.textField = textField;
	}

	@Override
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
		if (hasKeyboardFocus() && (input.is(keys.getEnterKey()) || input.is(keys.getEscapeKey()))) {
			return Optional.of(InputAction.run(this::clearFocus));
		}
		if (input.isMouseInput() && input.getKey().getValue() == InputConstants.MOUSE_BUTTON_LEFT &&
			isMouseOver(input.getMouseX(), input.getMouseY())
		) {
			return Optional.of(new InputAction(release -> {
					if (release.ifMouseEvent(textField::mouseClicked)) {
						focus();
					}
				})
				.within(this));
		}
		if (!hasKeyboardFocus()) {
			return Optional.empty();
		}
		if (input.ifKeyboardEvent(textField::keyPressed)) {
			return Optional.of(ConsumedInput.INSTANCE);
		}
		return textField.canConsumeInput() && input.isAllowedChatCharacter() ? Optional.of(ConsumedInput.TEXT_INPUT) : Optional.empty();
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return textField.isVisible() && textField.isMouseOver(mouseX, mouseY);
	}

	@Override
	public boolean hasKeyboardFocus() {
		return textField.isVisible() && textField.isFocused();
	}

	@Override
	public void focus() {
		textField.setFocused(true);
	}

	@Override
	public void clearFocus() {
		textField.setFocused(false);
		textField.syncFromScrollStep();
	}

	@Override
	public void resetInput() {
		clearFocus();
	}

	@Override
	public boolean onCharTyped(char codePoint, int modifiers) {
		return hasKeyboardFocus() && textField.charTyped(codePoint, modifiers);
	}

	@Override
	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
	}
}
