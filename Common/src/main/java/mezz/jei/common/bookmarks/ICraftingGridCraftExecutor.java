package mezz.jei.common.bookmarks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Optional server-side executor for crafting-grid auto-crafting in non-vanilla menus.
 * Registered implementations are consulted before the vanilla crafting path.
 */
public interface ICraftingGridCraftExecutor {
	boolean canHandle(AbstractContainerMenu menu);

	int craft(ServerPlayer player, int containerId, @Nullable ResourceLocation recipeId, List<ItemStack> targetStacks, int multiplier);
}
