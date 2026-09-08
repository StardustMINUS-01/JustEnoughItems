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
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientInputHandlerTest {
	private static final InputConstants.Key SEARCH_KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G);
	private static final InputConstants.Key OTHER_KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H);

	@Test
	public void wheelDoesNotReachPreviousOverlayOnAnUnsupportedScreen() {
		Screen overlayScreen = new Screen(Component.empty()) { };
		Screen standaloneScreen = new Screen(Component.empty()) { };
		var router = new RecordingMouseRouter();
		var handler = mouseHandler(overlayScreen, router);

		assertTrue(handler.onGuiMouseScroll(overlayScreen, 12, 45, 0.25, -1));
		assertEquals(List.of(12.0, 45.0, 0.25, -1.0), router.wheel);
		assertFalse(handler.onGuiMouseScroll(standaloneScreen, 12, 45, 0, 1));
		assertFalse(handler.onGuiMouseScroll(standaloneScreen, 12, 45, 0, -1));
		assertEquals(1, router.wheelCalls);
		assertTrue(handler.onGuiMouseScroll(overlayScreen, 24, 60, 0, 1));
		assertEquals(2, router.wheelCalls);
		assertEquals(List.of(24.0, 60.0, 0.0, 1.0), router.wheel);
	}

	@Test
	public void dragDoesNotReachPreviousOverlayOnAnUnsupportedScreen() {
		Screen overlayScreen = new Screen(Component.empty()) { };
		Screen standaloneScreen = new Screen(Component.empty()) { };
		var router = new RecordingMouseRouter();
		var handler = mouseHandler(overlayScreen, router);

		assertTrue(handler.onGuiMouseDragged(overlayScreen, 12, 45, 0, 3, 4));
		assertFalse(handler.onGuiMouseDragged(standaloneScreen, 12, 45, 0, 3, 4));
		assertEquals(1, router.dragCalls);
	}

	private static ClientInputHandler mouseHandler(Screen overlayScreen, UserInputRouter router) {
		IScreenHelper screenHelper = (IScreenHelper) Proxy.newProxyInstance(
			IScreenHelper.class.getClassLoader(), new Class<?>[]{IScreenHelper.class},
			(proxy, method, args) -> method.getName().equals("getGuiProperties") && args[0] == overlayScreen ? Optional.of(Boolean.TRUE) : Optional.empty()
		);
		return new ClientInputHandler(List.of(), null, router, new DragRouter(), null, screenHelper);
	}

	private static class RecordingMouseRouter extends UserInputRouter {
		private int wheelCalls, dragCalls;
		private List<Double> wheel = List.of();

		private RecordingMouseRouter() { super("test"); }

		@Override
		public boolean handleMouseScrolled(double x, double y, double dx, double dy) {
			wheelCalls++;
			wheel = List.of(x, y, dx, dy);
			return true;
		}

		@Override
		public boolean handleMouseDragged(double x, double y, InputConstants.Key key, double dx, double dy) {
			dragCalls++;
			return true;
		}
	}

	@Test
	public void focusedTextFieldOnlyRoutesTerminalSearchHotkeyBeforeScreen() {
		IJeiKeyMapping searchKey = (IJeiKeyMapping) Proxy.newProxyInstance(
			IJeiKeyMapping.class.getClassLoader(),
			new Class<?>[]{mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers.class},
			(proxy, method, args) -> method.getName().equals("isActiveAndMatches") && SEARCH_KEY.equals(args[0])
		);
		IInternalKeyMappings keyMappings = (IInternalKeyMappings) Proxy.newProxyInstance(
			IInternalKeyMappings.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class},
			(proxy, method, args) -> method.getName().equals("getSearchIngredientInTerminal") || method.getName().equals("getFocusSearch") ? searchKey : null
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
