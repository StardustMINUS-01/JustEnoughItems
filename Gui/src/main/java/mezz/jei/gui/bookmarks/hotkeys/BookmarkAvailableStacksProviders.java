package mezz.jei.gui.bookmarks.hotkeys;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Optional providers of extra available stacks (e.g. an open AE2 terminal's network contents
 * or a Sophisticated backpack) for bookmark auto-crafting material calculation.
 * Ported from JEI 1.21.1 (branch 1.21.1).
 */
public final class BookmarkAvailableStacksProviders {
	private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

	private BookmarkAvailableStacksProviders() {
	}

	@FunctionalInterface
	public interface Provider {
		Optional<List<ItemStack>> getAvailableStacks(AbstractContainerMenu menu);
	}

	public static void registerProvider(Provider provider) {
		PROVIDERS.add(provider);
	}

	public static List<ItemStack> getAvailableStacks(AbstractContainerMenu menu) {
		for (Provider provider : PROVIDERS) {
			Optional<List<ItemStack>> stacks = provider.getAvailableStacks(menu);
			if (stacks.isPresent()) {
				return stacks.get();
			}
		}
		return List.of();
	}
}
