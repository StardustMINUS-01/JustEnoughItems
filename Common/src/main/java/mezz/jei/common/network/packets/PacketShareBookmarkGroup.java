package mezz.jei.common.network.packets;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.chat.JeiChatItemLinks;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.network.IChatConnection;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
		if (context.connection() instanceof IChatConnection connection) {
			JeiChatItemLinks.createBookmarkGroupLink(snapshot)
				.ifPresent(link -> connection.share(context.player(), link, TYPE));
		}
	}
}
