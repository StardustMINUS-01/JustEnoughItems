package mezz.jei.common.network.packets;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.chat.JeiChatItemLinks;
import mezz.jei.common.network.ServerPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class PacketShareBookmarkGroup extends PlayToServerPacket<PacketShareBookmarkGroup> {
	public static final CustomPacketPayload.Type<PacketShareBookmarkGroup> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "share_bookmark_group"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketShareBookmarkGroup> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(JeiChatItemLinks.MAX_BOOKMARK_GROUP_LINK_LENGTH),
		packet -> packet.snapshot,
		PacketShareBookmarkGroup::new
	);

	private final String snapshot;

	public PacketShareBookmarkGroup(String snapshot) {
		this.snapshot = snapshot;
	}

	@Override
	public Type<PacketShareBookmarkGroup> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketShareBookmarkGroup> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ServerPacketContext context) {
		if (!JeiChatItemLinks.isValidBookmarkGroupSnapshot(snapshot)) {
			return;
		}
		String marker = JeiChatItemLinks.createBookmarkGroupLinkMarker(snapshot).trim();
		Component message = Component.translatable("chat.type.text", context.player().getDisplayName(), marker);
		context.player().server.getPlayerList().broadcastSystemMessage(message, false);
	}
}
