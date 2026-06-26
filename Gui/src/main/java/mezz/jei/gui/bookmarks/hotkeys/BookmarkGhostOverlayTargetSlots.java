/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.hotkeys;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Forge 1.20.1 adapter for GTNH NEI's IStackPositioner target slots.
 */
public final class BookmarkGhostOverlayTargetSlots {
	private static final int VANILLA_CRAFTING_FIRST_SLOT = 1;
	private static final int PLAYER_INVENTORY_CRAFTING_SLOT_COUNT = 4;
	private static final int CRAFTING_TABLE_SLOT_COUNT = 9;

	private BookmarkGhostOverlayTargetSlots() {
	}

	public static List<BookmarkGhostOverlay.TargetSlot> fromMenu(AbstractContainerMenu menu) {
		if (menu instanceof InventoryMenu) {
			return fromSlots(menu.slots, VANILLA_CRAFTING_FIRST_SLOT, PLAYER_INVENTORY_CRAFTING_SLOT_COUNT);
		}
		if (menu instanceof CraftingMenu) {
			return fromSlots(menu.slots, VANILLA_CRAFTING_FIRST_SLOT, CRAFTING_TABLE_SLOT_COUNT);
		}
		return List.of();
	}

	public static List<BookmarkGhostOverlay.TargetSlot> fromSlots(List<Slot> slots, int firstSlot, int slotCount) {
		if (firstSlot < 0 || slotCount <= 0 || firstSlot >= slots.size()) {
			return List.of();
		}
		int endSlot = Math.min(slots.size(), firstSlot + slotCount);
		return slots.subList(firstSlot, endSlot).stream()
			.map(slot -> new BookmarkGhostOverlay.TargetSlot(slot.x, slot.y, slot.getItem()))
			.toList();
	}

}
