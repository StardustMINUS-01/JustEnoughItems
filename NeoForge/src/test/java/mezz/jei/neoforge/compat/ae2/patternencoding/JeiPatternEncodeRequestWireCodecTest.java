package mezz.jei.neoforge.compat.ae2.patternencoding;

import appeng.api.stacks.GenericStack;
import io.netty.buffer.Unpooled;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JeiPatternEncodeRequestWireCodecTest {
	@Test
	public void roundTripsNullOnlyRequest() {
		JeiPatternEncodeRequestWire original = new JeiPatternEncodeRequestWire(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.PROCESSING,
			Arrays.asList(null, null),
			Collections.singletonList(null),
			List.of(),
			List.of(),
			null,
			false,
			true
		);

		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(new FriendlyByteBuf(Unpooled.buffer()), RegistryAccess.EMPTY);
		JeiPatternEncodeRequestWire.STREAM_CODEC.encode(buffer, original);
		JeiPatternEncodeRequestWire decoded = JeiPatternEncodeRequestWire.STREAM_CODEC.decode(buffer);

		Assertions.assertEquals(original, decoded);
	}

	@Test
	public void rejectsOversizedStackLists() {
		List<GenericStack> oversized = new ArrayList<>();
		for (int i = 0; i <= JeiPatternEncodeRequestWire.MAX_STACKS_PER_SIDE; i++) {
			oversized.add(null);
		}
		JeiPatternEncodeRequestWire request = new JeiPatternEncodeRequestWire(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.PROCESSING,
			oversized,
			List.of(),
			List.of(),
			List.of(),
			null,
			false,
			false
		);

		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(new FriendlyByteBuf(Unpooled.buffer()), RegistryAccess.EMPTY);
		Assertions.assertThrows(IllegalArgumentException.class, () -> JeiPatternEncodeRequestWire.STREAM_CODEC.encode(buffer, request));
	}
}
