package mezz.jei.test.neoforge.compat.ae2;

import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeRequest;
import mezz.jei.neoforge.compat.ae2.Ae2RecipeChainPatternEncodingBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class Ae2RecipeChainPatternEncodingBridgeTest {
	@Test
	public void sendRequestsReturnsFalseForEmptyRequests() {
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(new TestAccess(true, true));

		Assertions.assertFalse(bridge.sendRequests(null, List.of()));
	}

	@Test
	public void sendRequestsReturnsFalseForNonTerminalMenu() {
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(new TestAccess(false, true));

		Assertions.assertFalse(bridge.sendRequests(null, List.of(request())));
	}

	@Test
	public void sendRequestsReturnsFalseWhenSendFails() {
		TestAccess access = new TestAccess(true, false);
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(access);

		Assertions.assertFalse(bridge.sendRequests(null, List.of(request())));
		Assertions.assertEquals(0, access.sentRequests);
	}

	@Test
	public void sendRequestsReturnsTrueWhenSent() {
		TestAccess access = new TestAccess(true, true);
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(access);

		Assertions.assertTrue(bridge.sendRequests(null, List.of(request())));
		Assertions.assertEquals(1, access.sentRequests);
	}

	private static JeiPatternEncodeRequest request() {
		return new JeiPatternEncodeRequest(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.PROCESSING,
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			null,
			false,
			false
		);
	}

	private static final class TestAccess implements Ae2RecipeChainPatternEncodingBridge.Access {
		private final boolean terminal;
		private final boolean sendSucceeds;
		private int sentRequests;

		private TestAccess(boolean terminal, boolean sendSucceeds) {
			this.terminal = terminal;
			this.sendSucceeds = sendSucceeds;
		}

		@Override
		public boolean isPatternEncodingTerminal(AbstractContainerMenu menu) {
			return terminal;
		}

		@Override
		public boolean sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests) {
			if (!sendSucceeds) {
				return false;
			}
			sentRequests++;
			return true;
		}
	}
}
