package mezz.jei.common.bookmarks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface ICraftingGridFillExecutor {
	boolean canHandle(AbstractContainerMenu menu);

	int fill(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier);
}
