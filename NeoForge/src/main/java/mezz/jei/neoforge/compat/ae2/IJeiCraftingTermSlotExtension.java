package mezz.jei.neoforge.compat.ae2;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Added to AE2's CraftingTermSlot by mixin. Only invoked by our auto-crafting executor,
 * so normal AE2 terminal behavior is never changed.
 */
public interface IJeiCraftingTermSlotExtension {
	boolean jei$craftOnce(AbstractContainerMenu menu, Player player, ItemStack result);
}
