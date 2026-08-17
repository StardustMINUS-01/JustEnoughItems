package mezz.jei.common.network.packets;

import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransfers;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.network.ServerPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PacketPullBookmarkItems extends PacketJei {
	private static final int MAX_TARGETS = 128;

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
	public IPacketId getPacketId() {
		return PacketIdServer.PULL_BOOKMARK_ITEMS;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeVarInt(containerId);
		buf.writeVarInt(targets.size());
		for (BookmarkPullTarget target : targets) {
			buf.writeItem(target.itemStack());
			buf.writeVarInt(target.amount());
		}
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		FriendlyByteBuf buf = data.buf();
		int containerId = buf.readVarInt();
		int targetCount = buf.readVarInt();
		List<BookmarkPullTarget> targets = new ArrayList<>();
		for (int i = 0; i < targetCount; i++) {
			ItemStack itemStack = buf.readItem();
			int amount = buf.readVarInt();
			targets.add(new BookmarkPullTarget(itemStack, amount));
		}
		ServerPacketContext context = data.context();
		ServerPlayer player = context.player();
		MinecraftServer server = player.server;
		return server.submit(() -> {
			ServerBookmarkPullTransfers.pull(player, containerId, targets);
		});
	}
}
