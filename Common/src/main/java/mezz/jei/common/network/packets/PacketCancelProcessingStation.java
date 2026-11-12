package mezz.jei.common.network.packets;

import mezz.jei.common.bookmarks.ServerProcessingStationCraft;
import mezz.jei.common.network.ServerPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class PacketCancelProcessingStation extends PlayToServerPacket<PacketCancelProcessingStation> {
	public static final Type<PacketCancelProcessingStation> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jei", "cancel_processing_station"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketCancelProcessingStation> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, p -> p.containerId, PacketCancelProcessingStation::new);
	private final int containerId;
	public PacketCancelProcessingStation(int containerId) { this.containerId = containerId; }
	@Override
	public Type<PacketCancelProcessingStation> type() { return TYPE; }
	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketCancelProcessingStation> streamCodec() { return STREAM_CODEC; }
	@Override
	public void process(ServerPacketContext context) {
		if (context.player().containerMenu.containerId == containerId)
			ServerProcessingStationCraft.cancel(context.player());
	}
}
