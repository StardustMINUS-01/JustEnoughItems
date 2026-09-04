package mezz.jei.gui.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.handlers.ChatLinkInputHandler;
import mezz.jei.gui.input.handlers.DragRouter;
import mezz.jei.gui.input.handlers.UserInputRouter;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientInputHandlerTest {
	private static final InputConstants.Key SEARCH_KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G);
	private static final InputConstants.Key OTHER_KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H);

	@Test
	public void focusedTextFieldOnlyRoutesTerminalSearchHotkeyBeforeScreen() {
		IJeiKeyMapping searchKey = (IJeiKeyMapping) Proxy.newProxyInstance(
			IJeiKeyMapping.class.getClassLoader(),
			new Class<?>[]{IJeiKeyMapping.class},
			(proxy, method, args) -> method.getName().equals("isActiveAndMatches") && SEARCH_KEY.equals(args[0])
		);
		IInternalKeyMappings keyMappings = (IInternalKeyMappings) Proxy.newProxyInstance(
			IInternalKeyMappings.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class},
			(proxy, method, args) -> method.getName().equals("getSearchIngredientInTerminal") ? searchKey : null
		);
		UserInputRouter inputRouter = new UserInputRouter("test") {
			@Override
			public boolean handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
				return true;
			}
		};
		ChatLinkInputHandler chatLinkInputHandler = new ChatLinkInputHandler(null, null, null, null, null, null) {
			@Override
			public boolean handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
				return false;
			}
		};
		IScreenHelper screenHelper = (IScreenHelper) Proxy.newProxyInstance(
			IScreenHelper.class.getClassLoader(),
			new Class<?>[]{IScreenHelper.class},
			(proxy, method, args) -> method.getName().equals("getGuiProperties") ? Optional.of(Boolean.TRUE) : null
		);
		ClientInputHandler inputHandler = new ClientInputHandler(
			List.of(),
			chatLinkInputHandler,
			inputRouter,
			new DragRouter(),
			keyMappings,
			screenHelper
		);
		Screen screen = new FocusedTextFieldScreen();

		assertTrue(inputHandler.onKeyboardKeyPressedPre(
			screen,
			new UserInput(SEARCH_KEY, 0, 0, GLFW.GLFW_MOD_CONTROL, InputType.IMMEDIATE)
		));
		assertFalse(inputHandler.onKeyboardKeyPressedPre(
			screen,
			new UserInput(OTHER_KEY, 0, 0, GLFW.GLFW_MOD_CONTROL, InputType.IMMEDIATE)
		));
	}

	private static class FocusedTextFieldScreen extends Screen {
		@SuppressWarnings("unused")
		private final EditBox searchField = new EditBox(null, 0, 0, 20, 12, Component.empty());

		private FocusedTextFieldScreen() {
			super(Component.empty());
			searchField.setFocused(true);
		}
	}
}
