package mezz.jei.test.neoforge.compat.ae2;

import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeRequest;
import mezz.jei.neoforge.compat.ae2.Ae2RecipeChainPatternEncodingBridge;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class Ae2RecipeChainPatternEncodingBridgeTest {
	@Test
	public void createIfLoadedReturnsEmptyWhenAe2StageAClassesAreMissing() {
		Assertions.assertTrue(Ae2RecipeChainPatternEncodingBridge.createIfLoaded().isEmpty());
	}

	@Test
	public void sendRequestsReturnsFalseForEmptyRequests() {
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(new TestAccess(true));

		Assertions.assertFalse(bridge.sendRequests(null, List.of()));
	}

	@Test
	public void sendRequestsReturnsFalseForNonTerminalMenu() {
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(new TestAccess(false));

		Assertions.assertFalse(bridge.sendRequests(null, List.of(request())));
	}

	@Test
	public void sendRequestsReturnsFalseWhenAllRequestConversionsFail() {
		TestAccess access = new TestAccess(true);
		access.failRequestConversion = true;
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(access);

		Assertions.assertFalse(bridge.sendRequests(null, List.of(request())));
		Assertions.assertEquals(0, access.sentPackets);
	}

	private static JeiPatternEncodeRequest request() {
		ResourceLocation recipeUid = ResourceLocation.fromNamespaceAndPath("test", "recipe");
		return new JeiPatternEncodeRequest(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			recipeUid,
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
		private boolean failRequestConversion;
		private int sentPackets;

		private TestAccess(boolean terminal) {
			this.terminal = terminal;
		}

		@Override
		public boolean isPatternEncodingTerminal(AbstractContainerMenu menu) {
			return terminal;
		}

		@Override
		public Object createRequest(JeiPatternEncodeRequest request) throws ReflectiveOperationException {
			if (failRequestConversion) {
				throw new ReflectiveOperationException("failed");
			}
			return new Object();
		}

		@Override
		public CustomPacketPayload createPacket(List<Object> requests) {
			sentPackets++;
			throw new UnsupportedOperationException("send should not be reached");
		}
	}

}
