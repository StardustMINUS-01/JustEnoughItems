package mezz.jei.forge.compat.tconstruct;

import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Optional;

/**
 * Client-side provider that maps Tinkers workstation grids to bookmark ghost overlay target slots.
 * Loaded only when Tinkers' Construct is installed.
 */
public class TinkerCraftingGridTargetSlotProvider implements BookmarkGhostOverlayTargetSlots.Provider {

	public static Optional<TinkerCraftingGridTargetSlotProvider> createIfLoaded() {
		return mezz.jei.forge.compat.CompatUtil.createIfLoaded(
			"slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu",
			TinkerCraftingGridTargetSlotProvider::new
		);
	}

	@Override
	public Optional<List<Slot>> getCraftingGridSlots(AbstractContainerMenu menu) {
		return TinkerCraftingGridAccess.find(menu)
			.map(TinkerCraftingGridAccess::craftingSlots);
	}
}
