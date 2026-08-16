package mezz.jei.neoforge.compat.ae2;

import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import appeng.menu.me.items.CraftingTermMenu;

/**
 * Optional client provider that adds the open AE2 terminal's network contents to the
 * available stacks used by bookmark auto-crafting material calculation.
 */
public class Ae2AvailableStacksProvider implements BookmarkAvailableStacksProviders.Provider {
	private static final long REFRESH_INTERVAL_MILLIS = 500;

	private final AvailableStacksAccess availableStacksAccess;
	private @Nullable AbstractContainerMenu cachedMenu;
	private @Nullable List<ItemStack> cachedStacks;
	private long cachedAtMillis;

	public static Optional<Ae2AvailableStacksProvider> createIfLoaded() {
		return Ae2CompatUtil.createIfLoaded(
			"appeng.menu.me.items.CraftingTermMenu",
			() -> new Ae2AvailableStacksProvider(new DirectAvailableStacksAccess())
		);
	}

	private Ae2AvailableStacksProvider(AvailableStacksAccess availableStacksAccess) {
		this.availableStacksAccess = availableStacksAccess;
	}

	@Override
	public Optional<List<ItemStack>> getAvailableStacks(AbstractContainerMenu menu) {
		if (cachedMenu == menu && cachedStacks != null &&
			System.currentTimeMillis() - cachedAtMillis < REFRESH_INTERVAL_MILLIS) {
			return Optional.of(cachedStacks);
		}
		Optional<List<ItemStack>> stacks = availableStacksAccess.getAvailableStacks(menu);
		stacks.ifPresent(result -> {
			cachedMenu = menu;
			cachedStacks = result;
			cachedAtMillis = System.currentTimeMillis();
		});
		return stacks;
	}

	private interface AvailableStacksAccess {
		Optional<List<ItemStack>> getAvailableStacks(AbstractContainerMenu menu);
	}

	private static final class DirectAvailableStacksAccess implements AvailableStacksAccess {
		@Override
		public Optional<List<ItemStack>> getAvailableStacks(AbstractContainerMenu menu) {
			if (!(menu instanceof CraftingTermMenu)) {
				return Optional.empty();
			}
			return BookmarkExternalStorageSnapshots.readEntries(menu)
				.map(BookmarkExternalStorageSnapshots::toAvailableStacks);
		}
	}
}
