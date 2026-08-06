package mezz.jei.common.network.packets;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.util.ServerCommandUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class PacketFastPickupItemStack extends PlayToServerPacket<PacketFastPickupItemStack> {
	public static final CustomPacketPayload.Type<PacketFastPickupItemStack> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "fast_pickup_item_stack"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFastPickupItemStack> STREAM_CODEC = StreamCodec.composite(
		ItemStack.STREAM_CODEC,
		p -> p.itemStack,
		PacketFastPickupItemStack::new
	);

	private final ItemStack itemStack;

	public PacketFastPickupItemStack(ItemStack itemStack) {
		this.itemStack = itemStack;
	}

	@Override
	public Type<PacketFastPickupItemStack> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketFastPickupItemStack> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ServerPacketContext context) {
		ServerCommandUtil.executeFastPickup(context, itemStack);
	}
}
