package mezz.jei.common.network.packets;

import mezz.jei.common.chat.JeiChatItemLinks;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

public class PacketShareBookmarkGroup extends PacketJei {
	// 1.20.1 serverbound custom payloads must fit within 32767 bytes.
	public static final int MAX_SNAPSHOT_LENGTH = 30000;
	private final String snapshot;

	public PacketShareBookmarkGroup(String snapshot) {
		this.snapshot = snapshot;
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.SHARE_BOOKMARK_GROUP;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeUtf(snapshot, MAX_SNAPSHOT_LENGTH);
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		String snapshot = data.buf().readUtf(MAX_SNAPSHOT_LENGTH);
		ServerPlayer player = data.context().player();
		return player.server.submit(() -> JeiChatItemLinks.createBookmarkGroupLink(snapshot).ifPresent(link -> {
			for (ServerPlayer recipient : player.server.getPlayerList().getPlayers()) {
				Component visibleLink = data.context().connection().canShareBookmarkGroup(recipient) ? link : Component.literal(link.getString());
				recipient.sendSystemMessage(Component.translatable("chat.type.text", player.getDisplayName(), visibleLink));
			}
		}));
	}
}
