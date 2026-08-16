package mezz.jei.common.bookmarks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CraftingGridCraftExecutors {
	private static final List<ICraftingGridCraftExecutor> EXECUTORS = new CopyOnWriteArrayList<>();

	private CraftingGridCraftExecutors() {
	}

	public static AutoCloseable registerExecutor(ICraftingGridCraftExecutor executor) {
		EXECUTORS.add(executor);
		return () -> EXECUTORS.remove(executor);
	}

	public static int craft(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
		AbstractContainerMenu menu = player.containerMenu;
		for (ICraftingGridCraftExecutor executor : EXECUTORS) {
			if (executor.canHandle(menu)) {
				return executor.craft(player, containerId, targetStacks, multiplier);
			}
		}
		return ServerBookmarkCraftingGridCraft.craft(player, containerId, targetStacks, multiplier);
	}
}
