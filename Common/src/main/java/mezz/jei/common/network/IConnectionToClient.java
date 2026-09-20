package mezz.jei.common.network;

import net.minecraft.network.chat.Component;
import mezz.jei.common.network.packets.PacketJei;
import net.minecraft.server.level.ServerPlayer;

public interface IConnectionToClient {
	default boolean canShareChat(ServerPlayer player) {
		return false;
	}

	default void shareChatLink(ServerPlayer sender, Component link) {
		for (ServerPlayer recipient : sender.server.getPlayerList().getPlayers()) {
			var visible = canShareChat(recipient) ? link : Component.literal(link.getString());
			recipient.sendSystemMessage(Component.translatable("chat.type.text", sender.getDisplayName(), visible));
		}
	}

	void sendPacketToClient(PacketJei packet, ServerPlayer player);

	default boolean canShareBookmarkGroup(ServerPlayer player) {
		return false;
	}
}
