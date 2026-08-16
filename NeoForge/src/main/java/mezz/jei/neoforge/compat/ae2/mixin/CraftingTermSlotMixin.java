package mezz.jei.neoforge.compat.ae2.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.api.config.Actionable;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import mezz.jei.neoforge.compat.ae2.IJeiCraftingTermSlotExtension;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds {@link IJeiCraftingTermSlotExtension} to AE2's crafting result slot without changing any existing behavior.
 */
@Mixin(targets = "appeng.menu.slot.CraftingTermSlot")
public abstract class CraftingTermSlotMixin implements IJeiCraftingTermSlotExtension {
	@Override
	public boolean jei$craftOnce(AbstractContainerMenu menu, Player player, ItemStack result) {
		try {
			if (result.isEmpty()) {
				return false;
			}

			ICraftingGridMenu craftingMenu = (ICraftingGridMenu) menu;
			InternalInventory grid = craftingMenu.getCraftingMatrix();
			List<ItemStack> consumed = new ArrayList<>(grid.size());
			for (int i = 0; i < grid.size(); i++) {
				if (grid.getStackInSlot(i).isEmpty()) {
					consumed.add(ItemStack.EMPTY);
					continue;
				}
				ItemStack extracted = grid.extractItem(i, 1, false);
				if (extracted.isEmpty()) {
					restoreConsumed(grid, consumed);
					return false;
				}
				consumed.add(extracted);
			}

			ItemStack resultCopy = result.copy();
			boolean added = player.getInventory().add(resultCopy);
			if (menu instanceof CraftingTermMenu craftingTermMenu) {
				if (!added && !resultCopy.isEmpty() && craftingTermMenu.getLinkStatus().connected()) {
					// Player inventory is full: put the remainder into the ME network instead.
					MEStorage storage = craftingTermMenu.getHost().getInventory();
					long inserted = storage.insert(
						AEItemKey.of(resultCopy),
						resultCopy.getCount(),
						Actionable.MODULATE,
						craftingTermMenu.getActionSource()
					);
					if (inserted >= resultCopy.getCount()) {
						added = true;
					}
				}
				if (added) {
					craftingTermMenu.slotsChanged(grid.toContainer());
				}
			}
			if (!added) {
				restoreConsumed(grid, consumed);
				return false;
			}
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static void restoreConsumed(InternalInventory grid, List<ItemStack> consumed) {
		for (int i = 0; i < consumed.size(); i++) {
			ItemStack stack = consumed.get(i);
			if (!stack.isEmpty()) {
				grid.setItemDirect(i, stack);
			}
		}
	}
}
