package mezz.jei.gui.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ReflectionUtil;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator;
import mezz.jei.gui.input.handlers.ChatLinkInputHandler;
import mezz.jei.gui.input.handlers.DragRouter;
import mezz.jei.gui.input.handlers.UserInputRouter;
import mezz.jei.gui.chat.ChatRecipeTooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

public class ClientInputHandler {
	private final List<ICharTypedHandler> charTypedHandlers;
	private final ChatLinkInputHandler chatLinkInputHandler;
	private final UserInputRouter inputRouter;
	private final DragRouter dragRouter;
	private final IInternalKeyMappings keybindings;
	private final IScreenHelper screenHelper;
	private final ReflectionUtil reflectionUtil = new ReflectionUtil();

	public ClientInputHandler(
		List<ICharTypedHandler> charTypedHandlers,
		ChatLinkInputHandler chatLinkInputHandler,
		UserInputRouter inputRouter,
		DragRouter dragRouter,
		IInternalKeyMappings keybindings,
		IScreenHelper screenHelper
	) {
		this.charTypedHandlers = charTypedHandlers;
		this.chatLinkInputHandler = chatLinkInputHandler;
		this.inputRouter = inputRouter;
		this.dragRouter = dragRouter;
		this.keybindings = keybindings;
		this.screenHelper = screenHelper;
	}

	public void onInitGui() {
		BookmarkAutoCraftingActivator.clearAutoCraftingInputs();
		this.chatLinkInputHandler.handleGuiChange();
		this.inputRouter.handleGuiChange();
		this.dragRouter.handleGuiChange();
	}

	/**
	 * When we have keyboard focus, use Pre
	 */
	public boolean onKeyboardKeyPressedPre(Screen screen, UserInput input) {
		if (screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return false;
		}
		if (this.chatLinkInputHandler.handleUserInput(screen, input, keybindings)) {
			return true;
		}

		boolean textFieldFocused = isContainerTextFieldFocused(screen);
		// AE text fields consume every focused key, so terminal search must run before the screen.
		if (PinnedTooltipManager.hasKeyboardFocus() || input.is(keybindings.getFocusSearch()) || !textFieldFocused || input.is(keybindings.getSearchIngredientInTerminal())) {
			if (screenHelper.getGuiProperties(screen).isPresent()) {
				return this.inputRouter.handleUserInput(screen, input, keybindings);
			}
		}
		return false;
	}

	/**
	 * Without keyboard focus, use Post
	 */
	public boolean onKeyboardKeyPressedPost(Screen screen, UserInput input) {
		if (screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return false;
		}
		if (isContainerTextFieldFocused(screen)) {
			if (screenHelper.getGuiProperties(screen).isPresent()) {
				return this.inputRouter.handleUserInput(screen, input, keybindings);
			}
		}
		return false;
	}

	public void onKeyboardKeyReleased(UserInput input) {
		BookmarkAutoCraftingActivator.releaseAutoCraftingInput(input.getKey());
	}

	/**
	 * When we have keyboard focus, use Pre
	 */
	public boolean onKeyboardCharTypedPre(Screen screen, char codePoint, int modifiers) {
		if (screen instanceof ChatScreen &&
			(ChatRecipeTooltip.INSTANCE.isPinned() || ChatRecipeTooltip.INSTANCE.hasTags())
		) {
			return true;
		}
		if (screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return false;
		}
		if (PinnedTooltipManager.hasKeyboardFocus() || !isContainerTextFieldFocused(screen)) {
			return handleCharTyped(codePoint, modifiers);
		}
		return false;
	}

	/**
	 * Without keyboard focus, use Post
	 */
	public void onKeyboardCharTypedPost(Screen screen, char codePoint, int modifiers) {
		if (screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return;
		}
		if (isContainerTextFieldFocused(screen)) {
			handleCharTyped(codePoint, modifiers);
		}
	}

	public boolean onGuiMouseClicked(Screen screen, UserInput input) {
		if (screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return false;
		}
		if (this.chatLinkInputHandler.handleUserInput(screen, input, keybindings)) {
			return true;
		}

		if (screenHelper.getGuiProperties(screen).isEmpty()) {
			return false;
		}

		boolean handled = this.inputRouter.handleUserInput(screen, input, keybindings);

		if (Minecraft.getInstance().screen == screen &&
			isMouseDragButton(input)
		) {
			handled |= this.dragRouter.startDrag(screen, input);
		}
		return handled;
	}

	public boolean onGuiMouseReleased(Screen screen, UserInput input) {
		if (screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return false;
		}
		if (this.chatLinkInputHandler.handleUserInput(screen, input, keybindings)) {
			return true;
		}

		if (screenHelper.getGuiProperties(screen).isEmpty()) {
			return false;
		}

		boolean handled = this.inputRouter.handleUserInput(screen, input, keybindings);

		if (isMouseDragButton(input)) {
			handled |= this.dragRouter.completeDrag(screen, input);
		}
		return handled;
	}

	public boolean onGuiMouseDragged(Screen screen, UserInput input) {
		if (screen instanceof ChatScreen &&
			(ChatRecipeTooltip.INSTANCE.isPinned() || ChatRecipeTooltip.INSTANCE.hasTags())
		) {
			return true;
		}
		if (screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return false;
		}
		if (screenHelper.getGuiProperties(screen).isEmpty()) {
			return false;
		}

		if (Minecraft.getInstance().screen == screen &&
			isMouseDragButton(input)
		) {
			return this.dragRouter.startDragIfIdle(screen, input);
		}
		return false;
	}

	public static boolean isMouseDragButton(UserInput input) {
		if (input.getKey().getType() != InputConstants.Type.MOUSE) {
			return false;
		}
		int mouseButton = input.getKey().getValue();
		return mouseButton == InputConstants.MOUSE_BUTTON_LEFT ||
			mouseButton == InputConstants.MOUSE_BUTTON_RIGHT;
	}

	public boolean onGuiMouseScroll(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
		return this.inputRouter.handleMouseScrolled(mouseX, mouseY, scrollDeltaX, scrollDeltaY);
	}

	public boolean onGuiMouseScroll(Screen screen, double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
		if (screen instanceof ChatScreen && ChatRecipeTooltip.INSTANCE.scroll(mouseX, mouseY, scrollDeltaX, scrollDeltaY)) {
			return true;
		}
		if (screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return false;
		}
		if (screenHelper.getGuiProperties(screen).isEmpty()) {
			return false;
		}
		return onGuiMouseScroll(mouseX, mouseY, scrollDeltaX, scrollDeltaY);
	}

	public boolean onGuiMouseDragged(Screen screen, double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (screen instanceof ChatScreen &&
			(ChatRecipeTooltip.INSTANCE.isPinned() || ChatRecipeTooltip.INSTANCE.hasTags())
		) {
			return true;
		}
		if (screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return false;
		}
		if (screenHelper.getGuiProperties(screen).isEmpty()) {
			return false;
		}
		InputConstants.Key input = InputConstants.Type.MOUSE.getOrCreate(button);
		return this.inputRouter.handleMouseDragged(mouseX, mouseY, input, dragX, dragY);
	}

	private boolean handleCharTyped(char codePoint, int modifiers) {
		return this.charTypedHandlers.stream()
			.filter(ICharTypedHandler::hasKeyboardFocus)
			.anyMatch(handler -> handler.onCharTyped(codePoint, modifiers));
	}

	private boolean isContainerTextFieldFocused(Screen screen) {
		return reflectionUtil.getFieldWithClass(screen, EditBox.class)
			.anyMatch(textField -> textField.isActive() && textField.isFocused());
	}
}
