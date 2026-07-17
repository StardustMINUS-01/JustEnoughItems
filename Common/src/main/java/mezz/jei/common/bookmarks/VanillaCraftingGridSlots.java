/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package mezz.jei.common.bookmarks;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public final class VanillaCraftingGridSlots {
	private static final int FIRST_SLOT = 1;
	private static final int INVENTORY_GRID_SIZE = 4;
	private static final int CRAFTING_TABLE_GRID_SIZE = 9;

	private VanillaCraftingGridSlots() {
	}

	public static List<Slot> getCraftingSlots(AbstractContainerMenu menu) {
		if (menu instanceof InventoryMenu) {
			return getSlots(menu, INVENTORY_GRID_SIZE);
		}
		if (menu instanceof CraftingMenu) {
			return getSlots(menu, CRAFTING_TABLE_GRID_SIZE);
		}
		return List.of();
	}

	private static List<Slot> getSlots(AbstractContainerMenu menu, int slotCount) {
		if (FIRST_SLOT >= menu.slots.size()) {
			return List.of();
		}
		return menu.slots.subList(FIRST_SLOT, Math.min(menu.slots.size(), FIRST_SLOT + slotCount));
	}
}
