package mezz.jei.common.network.packets;

import mezz.jei.common.chat.SharedChatIngredient;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public final class PacketShareIngredient extends PacketJei {
	private final String snapshot;

	public PacketShareIngredient(String snapshot) {
		this.snapshot = snapshot;
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.SHARE_INGREDIENT;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeUtf(snapshot, SharedChatIngredient.MAX_LENGTH);
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		String snapshot = data.buf().readUtf(SharedChatIngredient.MAX_LENGTH);
		var player = data.context().player();
		return player.server.submit(() -> SharedChatIngredient.decode(snapshot).ifPresent(ingredient -> {
			try {
				Component link = ingredient.createLink(snapshot);
				Component.Serializer.toJson(link);
				data.context().connection().shareChatLink(player, link);
			} catch (RuntimeException e) {
				// Reject untrusted stack NBT that third-party display code cannot deserialize.
			}
		}));
	}
}
