package mezz.jei.neoforge.compat.ae2;

import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.gui.bookmarks.chain.BookmarkCraftingScope;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import mezz.jei.neoforge.compat.CompatUtil;
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
	private static final long REFRESH_INTERVAL_MILLIS = 200;

	private @Nullable AbstractContainerMenu cachedMenu;
	private @Nullable List<ItemStack> cachedStacks;
	private long cachedAtMillis;

	public static Optional<Ae2AvailableStacksProvider> createIfLoaded() {
		return CompatUtil.createIfLoaded(
			"appeng.menu.me.items.CraftingTermMenu",
			Ae2AvailableStacksProvider::new
		);
	}

	private Ae2AvailableStacksProvider() {
	}

	@Override
	public Optional<List<ItemStack>> getAvailableStacks(AbstractContainerMenu menu) {
		boolean scoped = !BookmarkCraftingScope.getInterests().isEmpty();
		if (!scoped && cachedMenu == menu && cachedStacks != null &&
			System.currentTimeMillis() - cachedAtMillis < REFRESH_INTERVAL_MILLIS) {
			return Optional.of(cachedStacks);
		}
		Optional<List<ItemStack>> stacks = DirectAvailableStacksAccess.getAvailableStacks(menu);
		if (!scoped) {
			stacks.ifPresent(result -> {
				cachedMenu = menu;
				cachedStacks = result;
				cachedAtMillis = System.currentTimeMillis();
			});
		}
		return stacks;
	}

	private static final class DirectAvailableStacksAccess {
		private static Optional<List<ItemStack>> getAvailableStacks(AbstractContainerMenu menu) {
			if (!(menu instanceof CraftingTermMenu)) {
				return Optional.empty();
			}
			return BookmarkExternalStorageSnapshots.readEntries(menu)
				.map(BookmarkExternalStorageSnapshots::toAvailableStacks);
		}
	}
}
