package mezz.jei.common.network;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public interface IChatConnection {
	void share(ServerPlayer sender, Component link, CustomPacketPayload.Type<?> channel);
}
