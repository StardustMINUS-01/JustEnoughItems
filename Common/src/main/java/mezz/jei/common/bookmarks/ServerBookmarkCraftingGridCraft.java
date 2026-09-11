/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.common.bookmarks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server-side vanilla crafting executor derived from GTNH NEI DefaultOverlayHandler.craft().
 */
public final class ServerBookmarkCraftingGridCraft {
	private static final int RESULT_SLOT = 0;
	private static final int MAX_MULTIPLIER = 64;

	private ServerBookmarkCraftingGridCraft() {
	}

	public static int craft(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
		AbstractContainerMenu menu = player.containerMenu;
		Container playerInventory = player.getInventory();
		if (menu.containerId != containerId || targetStacks.isEmpty() || VanillaCraftingGridSlots.getCraftingSlots(menu).isEmpty()) {
			return 0;
		}
		int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
		int crafted = 0;
		while (remaining > 0) {
			int filled = ServerBookmarkCraftingGridFill.fill(
				menu,
				containerId,
				playerInventory,
				player,
				targetStacks,
				remaining
			);
			if (filled <= 0) {
				break;
			}
			menu.slotsChanged(playerInventory);

			if (!takeResult(menu, playerInventory, player)) {
				break;
			}
			crafted += filled;
			remaining -= filled;
		}

		if (crafted > 0) {
			playerInventory.setChanged();
			menu.broadcastChanges();
		}
		return crafted;
	}

	private static boolean takeResult(AbstractContainerMenu menu, Container playerInventory, ServerPlayer player) {
		Slot resultSlot = menu.getSlot(RESULT_SLOT);
		ItemStack result = resultSlot.getItem();
		if (result.isEmpty()) {
			return false;
		}
		if (!resultSlot.mayPickup(player)) {
			return false;
		}

		List<Slot> playerSlots = ServerBookmarkCraftingGridFill.getPlayerSlots(menu, playerInventory);
		if (!ServerBookmarkCraftingGridFill.canInsert(playerSlots, result)) {
			return false;
		}

		menu.clicked(RESULT_SLOT, 0, ClickType.QUICK_MOVE, player);
		return true;
	}

}
