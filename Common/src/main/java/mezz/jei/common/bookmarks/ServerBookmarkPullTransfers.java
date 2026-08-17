package mezz.jei.common.bookmarks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerBookmarkPullTransfers {
	private static final List<ServerBookmarkPullTransferHandler> HANDLERS = new CopyOnWriteArrayList<>();

	private ServerBookmarkPullTransfers() {
	}

	public static AutoCloseable registerHandler(ServerBookmarkPullTransferHandler handler) {
		HANDLERS.add(handler);
		return () -> HANDLERS.remove(handler);
	}

	public static int pull(ServerPlayer player, int containerId, List<BookmarkPullTarget> targets) {
		return pull(player.containerMenu, containerId, player.getInventory(), player, targets);
	}

	public static int pull(
		AbstractContainerMenu menu,
		int containerId,
		Container playerInventory,
		@Nullable ServerPlayer player,
		List<BookmarkPullTarget> targets
	) {
		for (ServerBookmarkPullTransferHandler handler : HANDLERS) {
			OptionalInt moved = handler.pull(menu, containerId, playerInventory, player, targets);
			if (moved.isPresent()) {
				return moved.getAsInt();
			}
		}
		return ServerBookmarkPullTransfer.pull(menu, containerId, playerInventory, player, targets);
	}
}
