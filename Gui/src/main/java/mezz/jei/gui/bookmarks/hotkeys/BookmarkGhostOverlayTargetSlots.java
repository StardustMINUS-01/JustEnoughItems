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
import mezz.jei.common.platform.Services;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * Forge 1.20.1 adapter for GTNH NEI's IStackPositioner target slots.
 */
public final class BookmarkGhostOverlayTargetSlots {
	private BookmarkGhostOverlayTargetSlots() {
	}

	public static List<BookmarkGhostOverlay.TargetSlot> fromMenu(AbstractContainerMenu menu) {
		return toTargetSlots(menu, 0, 0);
	}

	public static List<BookmarkGhostOverlay.TargetSlot> fromScreen(AbstractContainerScreen<?> screen) {
		int guiLeft = Services.PLATFORM.getScreenHelper().getGuiLeft(screen);
		int guiTop = Services.PLATFORM.getScreenHelper().getGuiTop(screen);
		return toTargetSlots(screen.getMenu(), guiLeft, guiTop);
	}

	private static List<BookmarkGhostOverlay.TargetSlot> toTargetSlots(
		AbstractContainerMenu menu,
		int offsetX,
		int offsetY
	) {
		return VanillaCraftingGridSlots.getCraftingSlots(menu).stream()
			.map(slot -> new BookmarkGhostOverlay.TargetSlot(slot.x + offsetX, slot.y + offsetY, slot.getItem()))
			.toList();
	}
}
