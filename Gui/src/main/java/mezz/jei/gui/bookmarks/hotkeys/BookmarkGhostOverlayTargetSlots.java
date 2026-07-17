/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.common.bookmarks.VanillaCraftingGridSlots;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * Forge 1.20.1 adapter for GTNH NEI's IStackPositioner target slots.
 */
public final class BookmarkGhostOverlayTargetSlots {
	private BookmarkGhostOverlayTargetSlots() {
	}

	public static List<BookmarkGhostOverlay.TargetSlot> fromMenu(AbstractContainerMenu menu) {
		return VanillaCraftingGridSlots.getCraftingSlots(menu).stream()
			.map(slot -> new BookmarkGhostOverlay.TargetSlot(slot.x, slot.y, slot.getItem()))
			.toList();
	}
}
