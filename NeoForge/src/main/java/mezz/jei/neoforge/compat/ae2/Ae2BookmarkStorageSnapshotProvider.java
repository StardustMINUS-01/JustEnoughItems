package mezz.jei.neoforge.compat.ae2;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.chain.BookmarkContainerStorageScanner;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class Ae2BookmarkStorageSnapshotProvider implements BookmarkExternalStorageSnapshots.Provider {
	private static final Logger LOGGER = LogManager.getLogger();

	private final EntryReader entryReader;

	public Ae2BookmarkStorageSnapshotProvider(EntryReader entryReader) {
		this.entryReader = entryReader;
	}

	public static Optional<Ae2BookmarkStorageSnapshotProvider> createIfLoaded() {
		try {
			return Optional.of(new Ae2BookmarkStorageSnapshotProvider(new ReflectionEntryReader()));
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("Failed to initialize AE2 bookmark storage snapshot bridge", e);
			return Optional.empty();
		}
	}

	@Override
	public Optional<BookmarkContainerStorageScanner.StorageSnapshot> scan(
		Object menu,
		Object screen,
		Function<ItemStack, Optional<BookmarkIngredientKey>> keyFactory
	) {
		return entryReader.readEntries(menu)
			.map(entries -> BookmarkExternalStorageSnapshots.createSnapshot(entries, keyFactory));
	}

	@Override
	public Optional<List<BookmarkExternalStorageSnapshots.Entry>> readEntries(Object menu) {
		return entryReader.readEntries(menu);
	}

	@FunctionalInterface
	public interface EntryReader {
		Optional<List<BookmarkExternalStorageSnapshots.Entry>> readEntries(Object menu);
	}

	private static final class ReflectionEntryReader implements EntryReader {
		private static final String ME_STORAGE_MENU = "appeng.menu.me.common.MEStorageMenu";
		private static final String I_CLIENT_REPO = "appeng.menu.me.common.IClientRepo";
		private static final String GRID_INVENTORY_ENTRY = "appeng.menu.me.common.GridInventoryEntry";
		private static final String AE_ITEM_KEY = "appeng.api.stacks.AEItemKey";

		private final Class<?> menuClass;
		private final Class<?> itemKeyClass;
		private final Method getClientRepoMethod;
		private final Method getAllEntriesMethod;
		private final Method getWhatMethod;
		private final Method getStoredAmountMethod;
		private final Method toStackMethod;

		private ReflectionEntryReader() throws ReflectiveOperationException {
			this.menuClass = Class.forName(ME_STORAGE_MENU);
			Class<?> clientRepoClass = Class.forName(I_CLIENT_REPO);
			Class<?> entryClass = Class.forName(GRID_INVENTORY_ENTRY);
			this.itemKeyClass = Class.forName(AE_ITEM_KEY);

			this.getClientRepoMethod = menuClass.getMethod("getClientRepo");
			this.getAllEntriesMethod = clientRepoClass.getMethod("getAllEntries");
			this.getWhatMethod = entryClass.getMethod("getWhat");
			this.getStoredAmountMethod = entryClass.getMethod("getStoredAmount");
			this.toStackMethod = itemKeyClass.getMethod("toStack", int.class);
		}

		@Override
		public Optional<List<BookmarkExternalStorageSnapshots.Entry>> readEntries(Object menu) {
			if (!menuClass.isInstance(menu)) {
				return Optional.empty();
			}
			try {
				Object clientRepo = getClientRepoMethod.invoke(menu);
				if (clientRepo == null) {
					return Optional.of(List.of());
				}
				Object allEntries = getAllEntriesMethod.invoke(clientRepo);
				if (!(allEntries instanceof Collection<?> entries)) {
					return Optional.of(List.of());
				}
				return Optional.of(entries.stream()
					.map(this::readEntry)
					.flatMap(Optional::stream)
					.toList());
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.warn("Failed to read AE2 client storage snapshot for bookmark pull", e);
				return Optional.of(List.of());
			}
		}

		private Optional<BookmarkExternalStorageSnapshots.Entry> readEntry(Object entry) {
			try {
				Object what = getWhatMethod.invoke(entry);
				if (!itemKeyClass.isInstance(what)) {
					return Optional.empty();
				}
				long storedAmount = ((Number) getStoredAmountMethod.invoke(entry)).longValue();
				if (storedAmount <= 0) {
					return Optional.empty();
				}
				Object stack = toStackMethod.invoke(what, 1);
				if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
					return Optional.of(new BookmarkExternalStorageSnapshots.Entry(itemStack, storedAmount));
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.warn("Failed to read AE2 storage entry for bookmark pull", e);
			}
			return Optional.empty();
		}
	}
}
