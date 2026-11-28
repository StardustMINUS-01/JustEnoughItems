package mezz.jei.common.ingredients;

import net.minecraft.world.item.ItemStack;

/** Identifies items whose ingredient UID is independent of count and components. */
public interface IItemUidHelperInternal {
	boolean canUseItemAsUid(ItemStack stack);
}
