package mezz.jei.neoforge.compat.ae2;

import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import mezz.jei.neoforge.compat.CompatUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Optional;

import appeng.menu.SlotSemantics;
import appeng.menu.me.items.CraftingTermMenu;

/**
 * Optional client provider that maps the AE2 crafting grid slots to bookmark ghost overlay target slots.
 * Loaded only when AE2 is installed.
 */
public class Ae2CraftingGridTargetSlotProvider implements BookmarkGhostOverlayTargetSlots.Provider {
	public static Optional<Ae2CraftingGridTargetSlotProvider> createIfLoaded() {
		return CompatUtil.createIfLoaded(
			"appeng.menu.me.items.CraftingTermMenu",
			Ae2CraftingGridTargetSlotProvider::new
		);
	}

	private Ae2CraftingGridTargetSlotProvider() {
	}

	@Override
	public Optional<List<Slot>> getCraftingGridSlots(AbstractContainerMenu menu) {
		return DirectTargetSlotAccess.getCraftingGridSlots(menu);
	}

	private static final class DirectTargetSlotAccess {
		private static Optional<List<Slot>> getCraftingGridSlots(AbstractContainerMenu menu) {
			if (!(menu instanceof CraftingTermMenu craftingMenu)) {
				return Optional.empty();
			}
			return Optional.of(craftingMenu.getSlots(SlotSemantics.CRAFTING_GRID));
		}
	}
}
