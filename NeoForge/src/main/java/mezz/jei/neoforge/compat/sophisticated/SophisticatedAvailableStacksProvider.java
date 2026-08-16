package mezz.jei.neoforge.compat.sophisticated;

import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import mezz.jei.neoforge.compat.CompatUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;

/**
 * Optional client provider that exposes the open Sophisticated backpack / storage contents
 * to bookmark chain crafting material calculation and result tracking.
 * Loaded only when SophisticatedCore is installed.
 */
public class SophisticatedAvailableStacksProvider implements BookmarkAvailableStacksProviders.Provider {
	public static Optional<SophisticatedAvailableStacksProvider> createIfLoaded() {
		return CompatUtil.createIfLoaded(
			"net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase",
			SophisticatedAvailableStacksProvider::new
		);
	}

	private SophisticatedAvailableStacksProvider() {
	}

	@Override
	public Optional<List<ItemStack>> getAvailableStacks(AbstractContainerMenu menu) {
		if (!(menu instanceof StorageContainerMenuBase<?> container)) {
			return Optional.empty();
		}
		List<ItemStack> stacks = new ArrayList<>();
		for (Slot slot : container.slots) {
			if (slot.container instanceof Inventory) {
				// Player inventory slots are already included by the caller.
				continue;
			}
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}
		return Optional.of(stacks);
	}
}
