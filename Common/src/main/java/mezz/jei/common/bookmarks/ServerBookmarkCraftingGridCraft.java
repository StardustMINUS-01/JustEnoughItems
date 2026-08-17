/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.common.bookmarks;

import mezz.jei.common.config.DebugConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side vanilla crafting executor derived from GTNH NEI DefaultOverlayHandler.craft().
 */
public final class ServerBookmarkCraftingGridCraft {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int RESULT_SLOT = 0;
	private static final int MAX_MULTIPLIER = 64;

	private ServerBookmarkCraftingGridCraft() {
	}

	public static int craft(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
		return craft(player.containerMenu, containerId, player.getInventory(), player, targetStacks, multiplier);
	}

	public static int craft(
		AbstractContainerMenu menu,
		int containerId,
		Container playerInventory,
		@Nullable ServerPlayer player,
		List<ItemStack> targetStacks,
		int multiplier
	) {
		if (menu.containerId != containerId || targetStacks.isEmpty()) {
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] SERVER-REJECT-CONTAINER menuContainerId={} packetContainerId={} targetStacks={}",
					menu.containerId, containerId, targetStacks.size());
			}
			return 0;
		}
		if (VanillaCraftingGridSlots.getCraftingSlots(menu).isEmpty() || menu.slots.size() <= RESULT_SLOT) {
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] SERVER-REJECT-NO-SLOTS menuSlots={} hasResultSlot={}",
					menu.slots.size(), menu.slots.size() > RESULT_SLOT);
			}
			return 0;
		}
		int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
		int crafted = 0;
		while (remaining > 0) {
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] SERVER-CRAFT-ROUND containerId={} menu={} remaining={} crafted={}", containerId, menu.getClass().getSimpleName(), remaining, crafted);
			}
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
				if (DebugConfig.isDebugModeEnabled()) {
					LOGGER.info("[Bug6] SERVER-CRAFT-TAKE-FAIL containerId={} filled={}", containerId, filled);
				}
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

	private static boolean takeResult(AbstractContainerMenu menu, Container playerInventory, @Nullable ServerPlayer player) {
		Slot resultSlot = menu.getSlot(RESULT_SLOT);
		ItemStack result = resultSlot.getItem();
		if (result.isEmpty()) {
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] SERVER-TAKE-EMPTY");
			}
			return false;
		}
		if (player != null && !resultSlot.mayPickup(player)) {
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] SERVER-TAKE-MAYPICKUP-FAIL result={}", result);
			}
			return false;
		}

		List<Slot> playerSlots = ServerBookmarkCraftingGridFill.getPlayerSlots(menu, playerInventory);
		if (!ServerBookmarkCraftingGridFill.canInsert(playerSlots, result)) {
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] SERVER-TAKE-CANNOT-INSERT result={}", result);
			}
			return false;
		}

		if (player == null) {
			ItemStack crafted = menu.quickMoveStack(null, RESULT_SLOT);
			boolean moved = !crafted.isEmpty();
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] SERVER-TAKE-RESULT result={} moved={} quickMoveReturned={} resultSlotAfter={}",
					result, moved, crafted, resultSlot.getItem());
			}
			return moved;
		}

		// The result slot ItemStack is a live reference: QUICK_MOVE empties it in
		// place during quickMoveStack, so counting with it after the click always
		// sees an empty stack (before=68 after=0) and reports moved=false even when
		// the crafted output was really moved into the player inventory. Use an
		// immutable snapshot for the before/after comparison instead.
		ItemStack resultSnapshot = result.copy();
		int before = countInInventory(playerSlots, resultSnapshot);
		menu.clicked(RESULT_SLOT, 0, ClickType.QUICK_MOVE, player);
		int after = countInInventory(playerSlots, resultSnapshot);
		boolean moved = after > before;
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] SERVER-TAKE-RESULT result={} before={} after={} moved={} resultSlotAfter={}",
				resultSnapshot, before, after, moved, resultSlot.getItem());
		}
		return moved;
	}

	private static int countInInventory(List<Slot> playerSlots, ItemStack target) {
		int count = 0;
		for (Slot slot : playerSlots) {
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && CraftingStackMatcher.matchesExactStack(target, stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

}
