package mezz.jei.common.bookmarks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CraftingGridFillExecutors {
	private static final List<ICraftingGridFillExecutor> EXECUTORS = new CopyOnWriteArrayList<>();

	private CraftingGridFillExecutors() {
	}

	public static AutoCloseable registerExecutor(ICraftingGridFillExecutor executor) {
		EXECUTORS.add(executor);
		return () -> EXECUTORS.remove(executor);
	}

	public static int fill(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
		AbstractContainerMenu menu = player.containerMenu;
		for (ICraftingGridFillExecutor executor : EXECUTORS) {
			if (executor.canHandle(menu)) {
				return executor.fill(player, containerId, targetStacks, multiplier);
			}
		}
		return ServerBookmarkCraftingGridFill.fill(player, containerId, targetStacks, multiplier);
	}
}
