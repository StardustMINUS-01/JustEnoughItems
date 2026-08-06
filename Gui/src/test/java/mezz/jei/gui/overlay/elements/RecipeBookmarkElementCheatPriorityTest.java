package mezz.jei.gui.overlay.elements;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecipeBookmarkElementCheatPriorityTest {
	private static final InputConstants.Key LEFT_MOUSE = InputConstants.Type.MOUSE.getOrCreate(0);

	@Test
	public void cheatGiveSkipsRecipeBookmarkTransfer() {
		UserInput input = new UserInput(LEFT_MOUSE, 0, 0, 0, InputType.SIMULATE);
		IInternalKeyMappings keyMappings = keyMappings(new TestKeyMapping(LEFT_MOUSE));

		assertTrue(RecipeBookmarkElement.shouldSkipTransferForCheatGive(input, keyMappings, true, true, false));
	}

	@Test
	public void cheatDisabledKeepsRecipeBookmarkTransfer() {
		UserInput input = new UserInput(LEFT_MOUSE, 0, 0, 0, InputType.SIMULATE);
		IInternalKeyMappings keyMappings = keyMappings(new TestKeyMapping(LEFT_MOUSE));

		assertFalse(RecipeBookmarkElement.shouldSkipTransferForCheatGive(input, keyMappings, false, true, false));
	}

	private static IInternalKeyMappings keyMappings(IJeiKeyMapping cheatItemStack) {
		IJeiKeyMapping noMatch = new TestKeyMapping(InputConstants.UNKNOWN);
		return (IInternalKeyMappings) Proxy.newProxyInstance(
			IInternalKeyMappings.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getCheatItemStack")) {
					return cheatItemStack;
				}
				return noMatch;
			}
		);
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
