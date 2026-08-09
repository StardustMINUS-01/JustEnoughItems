package mezz.jei.neoforge.compat.ae2.patternencoding;

import appeng.api.stacks.GenericStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record JeiPatternCatalystWire(
	int sourceSlot,
	GenericStack stack
) {
	public static final StreamCodec<RegistryFriendlyByteBuf, JeiPatternCatalystWire> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		JeiPatternCatalystWire::sourceSlot,
		GenericStack.STREAM_CODEC,
		JeiPatternCatalystWire::stack,
		JeiPatternCatalystWire::new
	);
}
