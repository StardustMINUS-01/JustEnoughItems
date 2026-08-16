package mezz.jei.test.gui.bookmarks.hotkeys;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Temporary reproduction for: auto-crafting works only once until the container screen is reopened.
 * <p>
 * In-game flow: the first press claims the auto-craft key, but no platform event ever calls
 * {@code ClientInputHandler.onKeyboardKeyReleased} (the NeoForge/Fabric wiring is missing),
 * so the claim is only cleared by {@code clearAutoCraftingInputs()} on screen init.
 */
public class BookmarkAutoCraftingActivatorClaimLifecycleTest {
	private static final InputConstants.Key KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_C);

	@BeforeEach
	public void resetClaims() {
		BookmarkAutoCraftingActivator.clearAutoCraftingInputs();
	}

	@Test
	public void secondPressIsBlockedUntilScreenReopenWithoutKeyRelease() {
		IJeiKeyMapping craftItemsKey = new TestKeyMapping(KEY);
		UserInput press = new UserInput(KEY, 0, 0, GLFW.GLFW_MOD_SHIFT, InputType.EXECUTE);

		// first press: claim succeeds and activation proceeds
		assertTrue(BookmarkAutoCraftingActivator.claimAutoCraftingInput(press, craftItemsKey));

		// second press while the same screen is open (key release never delivered):
		// claim is rejected, matching the reported "only crafts once" symptom
		assertFalse(BookmarkAutoCraftingActivator.claimAutoCraftingInput(press, craftItemsKey));

		// reopening the screen clears the claim, which is why the bug disappears after reopening
		BookmarkAutoCraftingActivator.clearAutoCraftingInputs();
		assertTrue(BookmarkAutoCraftingActivator.claimAutoCraftingInput(press, craftItemsKey));
	}

	@Test
	public void keyReleaseRestoresClaim() {
		IJeiKeyMapping craftItemsKey = new TestKeyMapping(KEY);
		UserInput press = new UserInput(KEY, 0, 0, GLFW.GLFW_MOD_SHIFT, InputType.EXECUTE);

		assertTrue(BookmarkAutoCraftingActivator.claimAutoCraftingInput(press, craftItemsKey));

		// the missing wiring: ClientInputHandler.onKeyboardKeyReleased -> releaseAutoCraftingInput
		BookmarkAutoCraftingActivator.releaseAutoCraftingInput(KEY);
		assertTrue(BookmarkAutoCraftingActivator.claimAutoCraftingInput(press, craftItemsKey));
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
