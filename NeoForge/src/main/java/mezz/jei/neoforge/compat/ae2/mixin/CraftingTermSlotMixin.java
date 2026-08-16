package mezz.jei.neoforge.compat.ae2.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.api.config.Actionable;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import mezz.jei.neoforge.compat.ae2.IJeiCraftingTermSlotExtension;
import net.minecraft.world.entity.player.Inventory;
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
	public int jei$craftBatch(AbstractContainerMenu menu, Player player, ItemStack result, int times) {
		try {
			if (result.isEmpty() || times <= 0) {
				return 0;
			}

			ICraftingGridMenu craftingMenu = (ICraftingGridMenu) menu;
			InternalInventory grid = craftingMenu.getCraftingMatrix();
			int actualTimes = times;
			for (int i = 0; i < grid.size(); i++) {
				int count = grid.getStackInSlot(i).getCount();
				if (count > 0) {
					actualTimes = Math.min(actualTimes, count);
				}
			}
		if (actualTimes <= 0) {
			return 0;
		}

		// Simulate placement before consuming anything: only craft as many results as the player
		// inventory and the ME network can fully hold. Creative-only networks accept nothing that is
		// not configured, so this clamps the batch instead of failing it (and never duplicates items).
		int totalCount = result.getCount() * actualTimes;
		ItemStack totalResult = result.copyWithCount(totalCount);
		int playerRoom = getInsertableCount(player.getInventory(), totalResult);
		long networkRoom = getNetworkRoom(menu, totalResult, totalCount - playerRoom);
		int maxTimes = (int) Math.min(actualTimes, (playerRoom + networkRoom) / result.getCount());
		if (maxTimes <= 0) {
			return 0;
		}
		actualTimes = maxTimes;
		totalCount = result.getCount() * actualTimes;
		totalResult = result.copyWithCount(totalCount);
		playerRoom = getInsertableCount(player.getInventory(), totalResult);
		int remainderCount = totalCount - playerRoom;

		List<ItemStack> consumed = new ArrayList<>(grid.size());
		for (int i = 0; i < grid.size(); i++) {
			if (grid.getStackInSlot(i).isEmpty()) {
					consumed.add(ItemStack.EMPTY);
					continue;
				}
				ItemStack extracted = grid.extractItem(i, actualTimes, false);
				if (extracted.getCount() < actualTimes) {
					restoreConsumed(grid, consumed);
					return 0;
				}
			consumed.add(extracted);
		}

		// Insert the network portion before touching the player inventory, so a network failure
		// can restore the grid without leaving duplicated items behind.
		if (remainderCount > 0) {
			long inserted = insertIntoNetwork(menu, totalResult.copyWithCount(remainderCount), remainderCount);
			if (inserted < remainderCount) {
				restoreConsumed(grid, consumed);
				return 0;
			}
		}
		if (playerRoom > 0) {
			player.getInventory().add(totalResult.copyWithCount(playerRoom));
		}
		if (menu instanceof CraftingTermMenu craftingTermMenu) {
			craftingTermMenu.slotsChanged(grid.toContainer());
			}
			return actualTimes;
		} catch (RuntimeException e) {
			return 0;
		}
	}

	private static long getNetworkRoom(AbstractContainerMenu menu, ItemStack totalResult, int requested) {
		if (requested <= 0 || !(menu instanceof CraftingTermMenu craftingTermMenu) || !craftingTermMenu.getLinkStatus().connected()) {
			return 0;
		}
		MEStorage storage = craftingTermMenu.getHost().getInventory();
		return storage.insert(AEItemKey.of(totalResult), requested, Actionable.SIMULATE, craftingTermMenu.getActionSource());
	}

	private static long insertIntoNetwork(AbstractContainerMenu menu, ItemStack remainder, int amount) {
		if (!(menu instanceof CraftingTermMenu craftingTermMenu) || !craftingTermMenu.getLinkStatus().connected()) {
			return 0;
		}
		MEStorage storage = craftingTermMenu.getHost().getInventory();
		return storage.insert(AEItemKey.of(remainder), amount, Actionable.MODULATE, craftingTermMenu.getActionSource());
	}

	private static void restoreConsumed(InternalInventory grid, List<ItemStack> consumed) {
		for (int i = 0; i < consumed.size(); i++) {
			ItemStack stack = consumed.get(i);
			if (!stack.isEmpty()) {
				grid.setItemDirect(i, stack);
			}
		}
	}

	private static int getInsertableCount(Inventory inventory, ItemStack stack) {
		int remaining = stack.getCount();
		for (ItemStack slotStack : inventory.items) {
			if (remaining <= 0) {
				break;
			}
			if (slotStack.isEmpty()) {
				remaining = Math.max(0, remaining - stack.getMaxStackSize());
			} else if (ItemStack.isSameItemSameComponents(slotStack, stack) && slotStack.getCount() < slotStack.getMaxStackSize()) {
				remaining = Math.max(0, remaining - (slotStack.getMaxStackSize() - slotStack.getCount()));
			}
		}
		return Math.max(0, stack.getCount() - remaining);
	}
}
