package mezz.jei.gui.recipes;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.InputPhase;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeBookmarkButtonHotkeyInputHandlerTest {
	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void mouseShortcutOnlyBookmarksWhenReleasedInside(boolean inside) {
		InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(0);
		AtomicInteger bookmarks = new AtomicInteger();
		AtomicInteger sounds = new AtomicInteger();
		RecipeBookmarkButtonHotkeyInputHandler handler = new RecipeBookmarkButtonHotkeyInputHandler(
			(x, y) -> x >= 0 && x < 16 && y >= 0 && y < 16,
			(input, preserveAmount) -> {
				assertTrue(preserveAmount);
				if (!input.isSimulate()) {
					bookmarks.incrementAndGet();
				}
				return true;
			}, sounds::incrementAndGet);
		int modifiers = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL;
		var interaction = handler.beginInput(null, new UserInput(key, 8, 8, modifiers, InputPhase.PRESS), keys(key)).orElseThrow();
		assertEquals(0, bookmarks.get());
		assertEquals(0, sounds.get());
		interaction.complete(new UserInput(key, inside ? 8 : 20, 8, modifiers, InputPhase.RELEASE));
		assertEquals(inside ? 1 : 0, bookmarks.get());
		assertEquals(inside ? 1 : 0, sounds.get());
	}

	@Test
	void keyboardShortcutExecutesExactlyOnce() {
		InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_A);
		AtomicInteger bookmarks = new AtomicInteger();
		RecipeBookmarkButtonHotkeyInputHandler handler = new RecipeBookmarkButtonHotkeyInputHandler(
			(x, y) -> true, (input, preserveAmount) -> { bookmarks.incrementAndGet(); return true; }, () -> {});
		UserInput input = new UserInput(key, 8, 8, GLFW.GLFW_MOD_SHIFT, InputPhase.PRESS);
		var interaction = handler.beginInput(null, input, keys(key)).orElseThrow();
		interaction.complete(input);
		assertEquals(1, bookmarks.get());
	}

	private static IInternalKeyMappings keys(InputConstants.Key key) {
		IJeiKeyMappingWithExtraModifiers mapping = new IJeiKeyMappingWithExtraModifiers() {
			@Override
			public boolean isActiveAndMatches(InputConstants.Key input) { return key.equals(input); }
			@Override
			public boolean isActiveAndMatchesAllowingExtraModifiers(InputConstants.Key input) { return key.equals(input); }
			@Override
			public boolean isUnbound() { return false; }
			@Override
			public Component getTranslatedKeyMessage() { return Component.literal("test"); }
		};
		return (IInternalKeyMappings) Proxy.newProxyInstance(IInternalKeyMappings.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class}, (proxy, method, args) -> {
				if (method.getName().equals("getBookmark")) {
					return mapping;
				}
				throw new UnsupportedOperationException(method.getName());
			});
	}
}
