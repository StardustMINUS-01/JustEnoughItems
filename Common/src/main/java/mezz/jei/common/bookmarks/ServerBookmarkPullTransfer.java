package mezz.jei.common.bookmarks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ServerBookmarkPullTransfer {
	private ServerBookmarkPullTransfer() {
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
		if (menu.containerId != containerId || targets.isEmpty()) {
			return 0;
		}

		int moved = 0;
		for (BookmarkPullTarget target : targets) {
			if (target.isEmpty()) {
				continue;
			}
			int remaining = target.amount();
			for (Slot sourceSlot : menu.slots) {
				if (remaining <= 0) {
					break;
				}
				if (sourceSlot.container == playerInventory) {
					continue;
				}
				if (player != null && !sourceSlot.mayPickup(player)) {
					continue;
				}
				ItemStack sourceStack = sourceSlot.getItem();
				if (sourceStack.isEmpty() || !CraftingStackMatcher.matchesExactStack(sourceStack, target.itemStack())) {
					continue;
				}
				int amount = Math.min(remaining, sourceStack.getCount());
				int inserted = insertIntoPlayerInventory(playerInventory, target.itemStack(), amount);
				if (inserted <= 0) {
					return finish(menu, playerInventory, moved);
				}
				sourceStack.shrink(inserted);
				sourceSlot.setChanged();
				remaining -= inserted;
				moved += inserted;
			}
		}
		return finish(menu, playerInventory, moved);
	}

	public static int getInsertableAmount(Container playerInventory, ItemStack targetStack, int amount) {
		int remaining = amount;
		for (int i = 0; i < playerInventory.getContainerSize() && remaining > 0; i++) {
			ItemStack inventoryStack = playerInventory.getItem(i);
			if (inventoryStack.isEmpty() || !CraftingStackMatcher.matchesExactStack(inventoryStack, targetStack)) {
				continue;
			}
			int limit = getStackLimit(playerInventory, inventoryStack);
			int room = limit - inventoryStack.getCount();
			if (room > 0) {
				remaining -= Math.min(remaining, room);
			}
		}

		for (int i = 0; i < playerInventory.getContainerSize() && remaining > 0; i++) {
			ItemStack inventoryStack = playerInventory.getItem(i);
			if (!inventoryStack.isEmpty()) {
				continue;
			}
			ItemStack insertedStack = targetStack.copy();
			if (!playerInventory.canPlaceItem(i, insertedStack)) {
				continue;
			}
			remaining -= Math.min(remaining, getStackLimit(playerInventory, insertedStack));
		}
		return amount - remaining;
	}

	public static int insertIntoPlayerInventory(Container playerInventory, ItemStack targetStack, int amount) {
		int remaining = amount;
		for (int i = 0; i < playerInventory.getContainerSize() && remaining > 0; i++) {
			ItemStack inventoryStack = playerInventory.getItem(i);
			if (inventoryStack.isEmpty() || !CraftingStackMatcher.matchesExactStack(inventoryStack, targetStack)) {
				continue;
			}
			int limit = getStackLimit(playerInventory, inventoryStack);
			int room = limit - inventoryStack.getCount();
			if (room <= 0) {
				continue;
			}
			int inserted = Math.min(remaining, room);
			inventoryStack.grow(inserted);
			playerInventory.setItem(i, inventoryStack);
			remaining -= inserted;
		}

		for (int i = 0; i < playerInventory.getContainerSize() && remaining > 0; i++) {
			ItemStack inventoryStack = playerInventory.getItem(i);
			if (!inventoryStack.isEmpty()) {
				continue;
			}
			ItemStack insertedStack = targetStack.copy();
			if (!playerInventory.canPlaceItem(i, insertedStack)) {
				continue;
			}
			int inserted = Math.min(remaining, getStackLimit(playerInventory, insertedStack));
			insertedStack.setCount(inserted);
			playerInventory.setItem(i, insertedStack);
			remaining -= inserted;
		}
		return amount - remaining;
	}

	private static int getStackLimit(Container playerInventory, ItemStack stack) {
		return Math.min(playerInventory.getMaxStackSize(), stack.getMaxStackSize());
	}

	private static int finish(AbstractContainerMenu menu, Container playerInventory, int moved) {
		if (moved > 0) {
			playerInventory.setChanged();
			menu.broadcastChanges();
		}
		return moved;
	}
}
