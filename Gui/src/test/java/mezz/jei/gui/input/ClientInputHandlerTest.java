package mezz.jei.gui.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.InputPhase;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.ConsumedInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers;
import mezz.jei.gui.input.focus.GuiKeyboardFocus;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientInputHandlerTest {
	private static final InputConstants.Key SEARCH_KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G);
	private static final InputConstants.Key OTHER_KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H);

	@Test
	public void wheelDoesNotReachPreviousOverlayOnAnUnsupportedScreen() {
		Screen overlayScreen = new Screen(Component.empty()) {};
		Screen standaloneScreen = new Screen(Component.empty()) {};
		RecordingInputTarget target = new RecordingInputTarget();
		ClientInputHandler handler = mouseHandler(overlayScreen, target);

		assertTrue(handler.onGuiMouseScroll(overlayScreen, 12, 45, 0.25, -1));
		assertEquals(List.of(12.0, 45.0, 0.25, -1.0), target.wheel);
		assertFalse(handler.onGuiMouseScroll(standaloneScreen, 12, 45, 0, 1));
		assertFalse(handler.onGuiMouseScroll(standaloneScreen, 12, 45, 0, -1));
		assertEquals(1, target.wheelCalls);
		assertTrue(handler.onGuiMouseScroll(overlayScreen, 24, 60, 0, 1));
		assertEquals(2, target.wheelCalls);
		assertEquals(List.of(24.0, 60.0, 0.0, 1.0), target.wheel);
	}

	@Test
	public void dragDoesNotReachPreviousOverlayOnAnUnsupportedScreen() {
		Screen overlayScreen = new Screen(Component.empty()) {};
		Screen standaloneScreen = new Screen(Component.empty()) {};
		RecordingInputTarget target = new RecordingInputTarget();
		ClientInputHandler handler = mouseHandler(overlayScreen, target);

		assertTrue(handler.onGuiMouseDragged(overlayScreen, 12, 45, 0, 3, 4));
		assertFalse(handler.onGuiMouseDragged(standaloneScreen, 12, 45, 0, 3, 4));
		assertEquals(1, target.dragCalls);
	}

	private static ClientInputHandler mouseHandler(Screen overlayScreen, RecordingInputTarget target) {
		IScreenHelper screenHelper = (IScreenHelper) Proxy.newProxyInstance(
			IScreenHelper.class.getClassLoader(), new Class<?>[]{IScreenHelper.class},
			(proxy, method, args) -> method.getName().equals("getGuiProperties") && args[0] == overlayScreen ? Optional.of(Boolean.TRUE) : Optional.empty()
		);
		InputArea area = InputArea.builder("Overlay", (x, y) -> true)
			.controls(target)
			.apiDragHandler((x, y, key, dx, dy) -> {
				target.dragCalls++;
				return true;
			})
			.build();
		GuiInputScene scene = new GuiInputScene(new GuiKeyboardFocus(), area);
		GuiInputController controller = new GuiInputController(scene, new InputCommands(), new InactiveEditor(), null);
		return new ClientInputHandler(controller, null, screenHelper);
	}

	@Test
	public void focusedTextFieldRoutesTerminalSearchBeforeScreen() {
		IJeiKeyMappingWithExtraModifiers searchKey = new TestKeyMapping(SEARCH_KEY);
		IJeiKeyMappingWithExtraModifiers noMatch = new TestKeyMapping(InputConstants.UNKNOWN);
		IInternalKeyMappings keyMappings = (IInternalKeyMappings) Proxy.newProxyInstance(
			IInternalKeyMappings.class.getClassLoader(), new Class<?>[]{IInternalKeyMappings.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getSearchIngredientInTerminal" -> searchKey;
				case "getFocusSearch" -> noMatch;
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
		GuiInputController[] controllers = new GuiInputController[1];
		int[] searches = {0};
		InputCommands commands = new InputCommands();
		commands.add("Terminal search", input -> input.is(keyMappings.getSearchIngredientInTerminal()), (screen, input) -> {
			searches[0]++;
			// A terminal integration can change screens while accepting the search.
			controllers[0].screenChanged();
			return Optional.of(ConsumedInput.INSTANCE);
		});
		controllers[0] = new GuiInputController(new GuiInputScene(new GuiKeyboardFocus()), commands, new InactiveEditor(), keyMappings);
		ClientInputHandler handler = new ClientInputHandler(controllers[0], keyMappings);
		Screen screen = new FocusedTextFieldScreen();

		assertTrue(handler.onKeyboardKeyPressedPre(screen, new UserInput(SEARCH_KEY, 0, 0, GLFW.GLFW_MOD_CONTROL, InputPhase.PRESS)));
		assertEquals(1, searches[0]);
		assertFalse(handler.onKeyboardKeyPressedPre(screen, new UserInput(OTHER_KEY, 0, 0, GLFW.GLFW_MOD_CONTROL, InputPhase.PRESS)));
		assertEquals(1, searches[0]);
	}

	@Test
	public void foregroundEditorReceivesCharactersBeforeSearch() {
		int[] characters = {0, 0};
		ITextInputHandler foreground = new InactiveEditor() {
			@Override
			public boolean hasKeyboardFocus() { return true; }
			@Override
			public boolean onCharTyped(char codePoint, int modifiers) {
				characters[0]++;
				return true;
			}
		};
		ITextInputHandler search = new InactiveEditor() {
			@Override
			public boolean hasKeyboardFocus() { return true; }
			@Override
			public boolean onCharTyped(char codePoint, int modifiers) {
				characters[1]++;
				return true;
			}
		};
		GuiInputScene scene = new GuiInputScene(new GuiKeyboardFocus(),
			InputArea.builder("Foreground", foreground).controls(foreground).build());
		GuiInputController controller = new GuiInputController(scene, new InputCommands(), search, null);

		assertTrue(controller.hasKeyboardFocus());
		assertTrue(controller.charTyped('x', 0));
		assertEquals(1, characters[0]);
		assertEquals(0, characters[1]);
	}

	private static class RecordingInputTarget implements IInputTarget {
		private int wheelCalls;
		private int dragCalls;
		private List<Double> wheel = List.of();

		@Override
		public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
			return Optional.empty();
		}

		@Override
		public boolean scroll(double x, double y, double dx, double dy) {
			wheelCalls++;
			wheel = List.of(x, y, dx, dy);
			return true;
		}
	}

	private record TestKeyMapping(InputConstants.Key key) implements IJeiKeyMappingWithExtraModifiers {
		@Override
		public boolean isActiveAndMatches(InputConstants.Key input) { return key.equals(input); }
		@Override
		public boolean isActiveAndMatchesAllowingExtraModifiers(InputConstants.Key input) { return key.equals(input); }
		@Override
		public boolean isUnbound() { return InputConstants.UNKNOWN.equals(key); }
		@Override
		public Component getTranslatedKeyMessage() { return Component.empty(); }
	}

	private static class InactiveEditor implements ITextInputHandler {
		@Override
		public boolean hasKeyboardFocus() { return false; }
		@Override
		public void focus() {}
		@Override
		public void clearFocus() {}
		@Override
		public boolean onCharTyped(char codePoint, int modifiers) { return false; }
		@Override
		public boolean isMouseOver(double mouseX, double mouseY) { return false; }
		@Override
		public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) { return Optional.empty(); }
	}

	private static class FocusedTextFieldScreen extends Screen {
		private final EditBox searchField = new EditBox(null, 0, 0, 20, 12, Component.empty());

		private FocusedTextFieldScreen() {
			super(Component.empty());
			searchField.setFocused(true);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(searchField);
		}
	}
}
