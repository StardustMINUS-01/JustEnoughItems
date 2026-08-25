package mezz.jei.gui.recipes;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.recipes.navigation.RecipeNavigationDirection;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RecipeNavigationKeyInputTest {
	@Test
	public void altArrowNavigatesOneQuery() {
		assertEquals(
			new RecipeNavigationKeyInput.Action(RecipeNavigationDirection.BACK, false),
			RecipeNavigationKeyInput.getAction(input(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_ALT)).orElseThrow()
		);
		assertEquals(
			new RecipeNavigationKeyInput.Action(RecipeNavigationDirection.FORWARD, false),
			RecipeNavigationKeyInput.getAction(input(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_MOD_ALT)).orElseThrow()
		);
	}

	@Test
	public void shiftAltArrowNavigatesToSessionEndpoint() {
		int modifiers = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT;
		assertEquals(
			new RecipeNavigationKeyInput.Action(RecipeNavigationDirection.BACK, true),
			RecipeNavigationKeyInput.getAction(input(GLFW.GLFW_KEY_LEFT, modifiers)).orElseThrow()
		);
		assertEquals(
			new RecipeNavigationKeyInput.Action(RecipeNavigationDirection.FORWARD, true),
			RecipeNavigationKeyInput.getAction(input(GLFW.GLFW_KEY_RIGHT, modifiers)).orElseThrow()
		);
	}

	@Test
	public void unrelatedOrControlModifiedKeysAreIgnored() {
		assertFalse(RecipeNavigationKeyInput.getAction(input(GLFW.GLFW_KEY_LEFT, 0)).isPresent());
		assertFalse(RecipeNavigationKeyInput.getAction(input(GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_MOD_ALT)).isPresent());
		assertFalse(RecipeNavigationKeyInput.getAction(input(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_CONTROL)).isPresent());
	}

	private static UserInput input(int keyCode, int modifiers) {
		return new UserInput(
			InputConstants.Type.KEYSYM.getOrCreate(keyCode),
			0,
			0,
			modifiers,
			InputType.IMMEDIATE
		);
	}
}
