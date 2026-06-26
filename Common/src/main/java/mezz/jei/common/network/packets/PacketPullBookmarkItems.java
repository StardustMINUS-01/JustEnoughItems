package mezz.jei.common.network.packets;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransfers;
import mezz.jei.common.network.ServerPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PacketPullBookmarkItems extends PlayToServerPacket<PacketPullBookmarkItems> {
	private static final int MAX_TARGETS = 128;
	public static final CustomPacketPayload.Type<PacketPullBookmarkItems> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "pull_bookmark_items"));
	private static final StreamCodec<RegistryFriendlyByteBuf, BookmarkPullTarget> TARGET_STREAM_CODEC = StreamCodec.composite(
		ItemStack.STREAM_CODEC,
		BookmarkPullTarget::itemStack,
		ByteBufCodecs.VAR_INT,
		BookmarkPullTarget::amount,
		BookmarkPullTarget::new
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketPullBookmarkItems> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		p -> p.containerId,
		TARGET_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TARGETS)),
		p -> p.targets,
		PacketPullBookmarkItems::new
	);

	private final int containerId;
	private final List<BookmarkPullTarget> targets;

	public PacketPullBookmarkItems(int containerId, List<BookmarkPullTarget> targets) {
		this.containerId = containerId;
		this.targets = targets.stream()
			.filter(target -> !target.isEmpty())
			.limit(MAX_TARGETS)
			.toList();
	}

	@Override
	public Type<PacketPullBookmarkItems> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketPullBookmarkItems> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ServerPacketContext context) {
		ServerBookmarkPullTransfers.pull(context.player(), containerId, targets);
	}
}
