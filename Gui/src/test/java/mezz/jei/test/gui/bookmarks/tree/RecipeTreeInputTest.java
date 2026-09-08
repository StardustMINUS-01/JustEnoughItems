package mezz.jei.test.gui.bookmarks.tree;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.tree.RecipeTreeInput;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTreeInputTest {
	@Test
	void shiftClickOnlyTogglesWhilePlainClickOnlySelects() {
		assertEquals(RecipeTreeInput.TOGGLE, RecipeTreeInput.click(0, true).orElseThrow());
		assertEquals(RecipeTreeInput.SELECT, RecipeTreeInput.click(0, false).orElseThrow());
		assertTrue(RecipeTreeInput.click(1, true).isEmpty());
		assertTrue(RecipeTreeInput.click(2, false).isEmpty());
	}

	@Test
	void onlyRecipeUsesAndIngredientBookmarkKeysAreHandled() {
		var keys = keys(GLFW.GLFW_KEY_R);
		assertEquals(RecipeTreeInput.SHOW_RECIPE, RecipeTreeInput.key(input(GLFW.GLFW_KEY_R, 0), keys).orElseThrow());
		assertEquals(RecipeTreeInput.SHOW_USES, RecipeTreeInput.key(input(GLFW.GLFW_KEY_U, 0), keys).orElseThrow());
		assertEquals(RecipeTreeInput.BOOKMARK, RecipeTreeInput.key(input(GLFW.GLFW_KEY_A, 0), keys).orElseThrow());
		for (int key : new int[] {GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_T}) {
			assertTrue(RecipeTreeInput.key(input(key, 0), keys).isEmpty());
		}
	}

	@Test
	void modifiedShortcutsAreNotImportedButReboundKeysWork() {
		var keys = keys(GLFW.GLFW_KEY_P);
		assertTrue(RecipeTreeInput.key(input(GLFW.GLFW_KEY_R, 0), keys).isEmpty());
		assertEquals(RecipeTreeInput.SHOW_RECIPE, RecipeTreeInput.key(input(GLFW.GLFW_KEY_P, 0), keys).orElseThrow());
		for (int modifier : new int[] {GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_CONTROL, GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SUPER}) {
			for (int key : new int[] {GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_A}) {
				assertTrue(RecipeTreeInput.key(input(key, modifier), keys).isEmpty());
			}
		}
	}

	private static UserInput input(int key, int modifiers) {
		return new UserInput(InputConstants.Type.KEYSYM.getOrCreate(key), 0, 0, modifiers, InputType.IMMEDIATE);
	}

	private static IInternalKeyMappings keys(int recipeKey) {
		return (IInternalKeyMappings) Proxy.newProxyInstance(IInternalKeyMappings.class.getClassLoader(), new Class<?>[] {IInternalKeyMappings.class},
			(proxy, method, args) -> new Key(switch (method.getName()) {
				case "getShowRecipe" -> recipeKey;
				case "getShowUses" -> GLFW.GLFW_KEY_U;
				case "getBookmark" -> GLFW.GLFW_KEY_A;
				default -> throw new AssertionError("Unexpected shortcut: " + method.getName());
			}));
	}

	private record Key(int code) implements IJeiKeyMapping {
		@Override public boolean isActiveAndMatches(InputConstants.Key key) { return key.getValue() == code; }
		@Override public boolean isUnbound() { return false; }
		@Override public Component getTranslatedKeyMessage() { return Component.literal("test"); }
	}
}
