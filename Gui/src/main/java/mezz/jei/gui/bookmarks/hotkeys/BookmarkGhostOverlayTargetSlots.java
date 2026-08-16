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
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Forge 1.20.1 adapter for GTNH NEI's IStackPositioner target slots.
 */
public final class BookmarkGhostOverlayTargetSlots {
	private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

	private BookmarkGhostOverlayTargetSlots() {
	}

	@FunctionalInterface
	public interface Provider {
		Optional<List<Slot>> getCraftingGridSlots(AbstractContainerMenu menu);
	}

	public static void registerProvider(Provider provider) {
		PROVIDERS.add(provider);
	}

	public static List<BookmarkGhostOverlay.TargetSlot> fromMenu(AbstractContainerMenu menu) {
		return getTargetSlots(menu, 0, 0);
	}

	public static List<BookmarkGhostOverlay.TargetSlot> fromScreen(AbstractContainerScreen<?> screen) {
		int guiLeft = Services.PLATFORM.getScreenHelper().getGuiLeft(screen);
		int guiTop = Services.PLATFORM.getScreenHelper().getGuiTop(screen);
		return getTargetSlots(screen.getMenu(), guiLeft, guiTop);
	}

	private static List<BookmarkGhostOverlay.TargetSlot> getTargetSlots(
		AbstractContainerMenu menu,
		int offsetX,
		int offsetY
	) {
		for (Provider provider : PROVIDERS) {
			Optional<List<Slot>> slots = provider.getCraftingGridSlots(menu);
			if (slots.isPresent()) {
				return toTargetSlots(slots.get(), offsetX, offsetY);
			}
		}
		return toTargetSlots(menu, offsetX, offsetY);
	}

	private static List<BookmarkGhostOverlay.TargetSlot> toTargetSlots(
		AbstractContainerMenu menu,
		int offsetX,
		int offsetY
	) {
		return toTargetSlots(VanillaCraftingGridSlots.getCraftingSlots(menu), offsetX, offsetY);
	}

	private static List<BookmarkGhostOverlay.TargetSlot> toTargetSlots(
		List<Slot> slots,
		int offsetX,
		int offsetY
	) {
		return slots.stream()
			.map(slot -> new BookmarkGhostOverlay.TargetSlot(slot.x + offsetX, slot.y + offsetY, slot.getItem()))
			.toList();
	}
}
