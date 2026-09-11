package mezz.jei.test.gui.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InputModifiersTest {
	private static final InputConstants.Key LEFT_MOUSE = InputConstants.Type.MOUSE.getOrCreate(0);

	@Test
	public void requiresCheatMode() {
		IJeiKeyMapping binding = new TestKeyMapping(LEFT_MOUSE);
		UserInput input = new UserInput(LEFT_MOUSE, 0, 0, 0, InputType.SIMULATE);

		assertTrue(InputModifiers.isCheatGiveInput(input, binding, true));
		assertFalse(InputModifiers.isCheatGiveInput(input, binding, false));
		UserInput other = new UserInput(InputConstants.Type.MOUSE.getOrCreate(1), 0, 0, 0, InputType.SIMULATE);
		assertFalse(InputModifiers.isCheatGiveInput(other, binding, true));
	}

	private record TestKeyMapping(InputConstants.Key key) implements IJeiKeyMapping {
		@Override
		public boolean isActiveAndMatches(InputConstants.Key input) {
			return key.equals(input);
		}

		@Override
		public boolean isUnbound() {
			return false;
		}

		@Override
		public Component getTranslatedKeyMessage() {
			return Component.literal("test");
		}
	}
}
