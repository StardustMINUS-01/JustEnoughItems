package mezz.jei.gui.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator;
import mezz.jei.gui.chat.ChatRecipeTooltip;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.gui.util.GuiEventListenerUtil;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Connects Minecraft's input callbacks to JEI's input controller. */
public class ClientInputHandler {
	private final GuiInputController controller;
	private final IInternalKeyMappings keybindings;
	private final @Nullable IScreenHelper screenHelper;
	/** Suppresses the handled key's character callbacks until that key is released. */
	@Nullable
	private InputConstants.Key characterSuppressionKey;

	public ClientInputHandler(GuiInputController controller, IInternalKeyMappings keybindings) {
		this(controller, keybindings, null);
	}

	public ClientInputHandler(GuiInputController controller, IInternalKeyMappings keybindings, @Nullable IScreenHelper screenHelper) {
		this.controller = controller;
		this.keybindings = keybindings;
		this.screenHelper = screenHelper;
	}

	public void onGuiChanged() {
		characterSuppressionKey = null;
		BookmarkAutoCraftingActivator.clearAutoCraftingInputs();
		ChatRecipeTooltip.INSTANCE.clear();
		controller.screenChanged();
	}

	/**
	 * Offers key presses to JEI before the Minecraft screen handles them, when no Minecraft
	 * text field has keyboard focus. The Focus Search shortcut and chat also get this early check.
	 *
	 * <p>For example, when a player uses Focus Search in the creative inventory, JEI can
	 * select its search field before the creative inventory's search field handles the key.</p>
	 */
	public boolean onKeyboardKeyPressedPre(Screen screen, UserInput input) {
		if (!isScreenSupported(screen)) {
			return false;
		}
		characterSuppressionKey = null;
		// Focus Search transfers keyboard focus even when the creative inventory's search field is selected.
		if (screen instanceof ChatScreen || controller.hasKeyboardFocus() || input.is(keybindings.getFocusSearch()) || input.is(keybindings.getSearchIngredientInTerminal()) || !isContainerTextFieldFocused(screen)) {
			return handleKeyPress(screen, input);
		}
		return false;
	}

	/**
	 * Offers a key press to JEI after the Minecraft screen has declined it, when a Minecraft
	 * text field has keyboard focus. This lets that text field handle the key first.
	 */
	public boolean onKeyboardKeyPressedPost(Screen screen, UserInput input) {
		if (!isScreenSupported(screen)) {
			return false;
		}
		if (!(screen instanceof ChatScreen) && isContainerTextFieldFocused(screen)) {
			return handleKeyPress(screen, input);
		}
		return false;
	}

	private boolean handleKeyPress(Screen screen, UserInput input) {
		Optional<IInputInteraction> interaction = controller.keyPressed(screen, input);
		characterSuppressionKey = interaction.filter(IInputInteraction::consumesCharacters)
			.map(ignored -> input.getKey())
			.orElse(null);
		return interaction.isPresent();
	}

	/** Allows later IME commits that arrive without another key press. */
	public void onKeyboardKeyReleased(int keyCode, int scanCode) {
		BookmarkAutoCraftingActivator.releaseAutoCraftingInput(InputConstants.getKey(keyCode, scanCode));
		if (InputConstants.getKey(keyCode, scanCode).equals(characterSuppressionKey)) {
			characterSuppressionKey = null;
		}
	}

	/**
	 * Offers typed characters to JEI before the Minecraft screen handles them, when no
	 * Minecraft text field has keyboard focus. JEI inserts them only if its search field
	 * has keyboard focus. Characters from a consumed action are cancelled before reaching
	 * either JEI's editor or the screen.
	 */
	public boolean onKeyboardCharTypedPre(Screen screen, char codePoint, int modifiers) {
		if (!isScreenSupported(screen)) {
			return false;
		}
		if (screen instanceof ChatScreen && ChatRecipeTooltip.INSTANCE.isPinned() && !InteractiveTooltipController.INSTANCE.hasKeyboardFocus()) {
			return true;
		}
		if (characterSuppressionKey != null) {
			return true;
		}
		if (controller.hasKeyboardFocus() || !isContainerTextFieldFocused(screen)) {
			return controller.charTyped(codePoint, modifiers);
		}
		return false;
	}

	/**
	 * Offers typed characters to JEI after a Minecraft text field has had the first chance
	 * to handle them. JEI inserts them only if its search field has keyboard focus.
	 */
	public void onKeyboardCharTypedPost(Screen screen, char codePoint, int modifiers) {
		if (isScreenSupported(screen) && characterSuppressionKey == null && isContainerTextFieldFocused(screen)) {
			controller.charTyped(codePoint, modifiers);
		}
	}

	public boolean onGuiMouseClicked(Screen screen, UserInput input) {
		if (!isScreenSupported(screen)) {
			return false;
		}
		characterSuppressionKey = null;
		return controller.mousePressed(screen, input);
	}

	public boolean onGuiMouseReleased(Screen screen, UserInput input) {
		if (!isScreenSupported(screen)) {
			// A standalone JEI screen keeps its capture even after the tooltip closes.
			return !InteractiveTooltipController.INSTANCE.isMouseReleaseRouted(input.getKey().getValue()) &&
				InteractiveTooltipController.INSTANCE.mouseReleased(input.getMouseX(), input.getMouseY(), input.getKey().getValue(), input.getModifiers());
		}
		boolean handled = controller.mouseReleased(screen, input);
		BookmarkAutoCraftingActivator.releaseAutoCraftingInput(input.getKey());
		return InteractiveTooltipController.INSTANCE.mouseReleased(input.getMouseX(), input.getMouseY(), input.getKey().getValue(), input.getModifiers()) || handled;
	}

	public boolean onGuiMouseScroll(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
		return controller.mouseScrolled(mouseX, mouseY, scrollDeltaX, scrollDeltaY);
	}

	public boolean onGuiMouseDragged(Screen screen, double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (!isScreenSupported(screen)) {
			return false;
		}
		InputConstants.Key input = InputConstants.Type.MOUSE.getOrCreate(button);
		return controller.mouseDragged(screen, mouseX, mouseY, input, dragX, dragY);
	}

	public boolean onGuiMouseScroll(Screen screen, double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
		return isScreenSupported(screen) && onGuiMouseScroll(mouseX, mouseY, scrollDeltaX, scrollDeltaY);
	}

	private boolean isScreenSupported(Screen screen) {
		return screen instanceof ChatScreen || screenHelper == null || screenHelper.getGuiProperties(screen).isPresent();
	}

	private boolean isContainerTextFieldFocused(Screen screen) {
		return GuiEventListenerUtil.getEditBoxes(screen)
			.anyMatch(textField -> textField.isActive() && textField.isFocused());
	}
}
