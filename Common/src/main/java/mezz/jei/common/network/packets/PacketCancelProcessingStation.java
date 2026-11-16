package mezz.jei.common.network.packets;

import mezz.jei.common.bookmarks.ServerProcessingStationCraft;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

public final class PacketCancelProcessingStation extends PacketJei {
	private final int containerId;

	public PacketCancelProcessingStation(int containerId) {
		this.containerId = containerId;
	}

	@Override
	public ResourceLocation getChannelId() {
		return PacketProcessingStationCraft.CHANNEL;
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.CANCEL_PROCESSING_STATION;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buffer) {
		buffer.writeVarInt(containerId);
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		int containerId = data.buf().readVarInt();
		var player = data.context().player();
		return player.server.submit(() -> {
			if (player.containerMenu.containerId == containerId) {
				ServerProcessingStationCraft.cancel(player);
			}
		});
	}
}
