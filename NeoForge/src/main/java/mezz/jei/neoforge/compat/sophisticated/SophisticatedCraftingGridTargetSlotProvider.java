package mezz.jei.neoforge.compat.sophisticated;

import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import mezz.jei.neoforge.compat.CompatUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Optional;

import net.p3pp3rf1y.sophisticatedcore.common.gui.ICraftingContainer;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;

/**
 * Optional client provider that maps the Sophisticated crafting upgrade grid slots
 * to bookmark ghost overlay target slots. Loaded only when SophisticatedCore is installed.
 */
public class SophisticatedCraftingGridTargetSlotProvider implements BookmarkGhostOverlayTargetSlots.Provider {
	public static Optional<SophisticatedCraftingGridTargetSlotProvider> createIfLoaded() {
		return CompatUtil.createIfLoaded(
			"net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase",
			SophisticatedCraftingGridTargetSlotProvider::new
		);
	}

	private SophisticatedCraftingGridTargetSlotProvider() {
	}

	@Override
	public Optional<List<Slot>> getCraftingGridSlots(AbstractContainerMenu menu) {
		if (!(menu instanceof StorageContainerMenuBase<?> container)) {
			return Optional.empty();
		}
		// Only return slots while the crafting upgrade tab is open: when closed the slots sit off-screen
		// (x/y = -100) and the ghost overlay would draw at the wrong position.
		return container.getOpenContainer()
			.filter(c -> c instanceof ICraftingContainer)
			.map(c -> ((ICraftingContainer) c).getRecipeSlots());
	}
}
