package mezz.jei.test.neoforge.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.keys.JeiKeyModifier;
import mezz.jei.neoforge.input.ForgeJeiKeyMappingBuilder;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NeoForgeJeiKeyMappingTest {
	@Test
	public void matchesKeyWhenIgnoringModifiers() {
		IJeiKeyMapping mapping = new ForgeJeiKeyMappingBuilder("jei.test", "key.jei.test")
			.setModifier(JeiKeyModifier.SHIFT)
			.buildKeyboardKey(GLFW.GLFW_KEY_A);

		InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_A);

		assertTrue(mapping.matchesIgnoringModifiers(key));
	}
}
