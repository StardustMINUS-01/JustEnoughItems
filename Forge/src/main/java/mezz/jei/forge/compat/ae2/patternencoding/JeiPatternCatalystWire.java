package mezz.jei.forge.compat.ae2.patternencoding;

import appeng.api.stacks.GenericStack;
import net.minecraft.network.FriendlyByteBuf;

public record JeiPatternCatalystWire(
	int sourceSlot,
	GenericStack stack
) {
	public static void write(FriendlyByteBuf buffer, JeiPatternCatalystWire catalyst) {
		buffer.writeVarInt(catalyst.sourceSlot());
		GenericStack.writeBuffer(catalyst.stack(), buffer);
	}

	public static JeiPatternCatalystWire read(FriendlyByteBuf buffer) {
		int sourceSlot = buffer.readVarInt();
		GenericStack stack = GenericStack.readBuffer(buffer);
		return new JeiPatternCatalystWire(sourceSlot, stack);
	}
}
