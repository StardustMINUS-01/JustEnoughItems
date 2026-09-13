package mezz.jei.neoforge.network;

import mezz.jei.common.network.IConnectionToClient;
import mezz.jei.common.network.IChatConnection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import mezz.jei.common.network.packets.PlayToClientPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class ConnectionToClient implements IConnectionToClient, IChatConnection {
	@Override
	public <T extends PlayToClientPacket<T>> void sendPacketToClient(T packet, ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, packet);
	}

	@Override
	public void share(ServerPlayer sender, Component link, CustomPacketPayload.Type<?> channel) {
		Component plain = link.copy().withStyle(style -> style.withClickEvent(null));
		Component message = Component.translatable("chat.type.text", sender.getDisplayName(), link);
		Component plainMessage = Component.translatable("chat.type.text", sender.getDisplayName(), plain);
		for (ServerPlayer recipient : sender.server.getPlayerList().getPlayers()) {
			if (recipient.connection.hasChannel(channel)) {
				recipient.sendSystemMessage(message);
			} else {
				recipient.sendSystemMessage(plainMessage);
			}
		}
	}
}
