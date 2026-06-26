package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class BookmarkExternalStorageSnapshots {
	private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

	private BookmarkExternalStorageSnapshots() {
	}

	public static AutoCloseable registerProvider(Provider provider) {
		PROVIDERS.add(provider);
		return () -> PROVIDERS.remove(provider);
	}

	public static Optional<BookmarkContainerStorageScanner.StorageSnapshot> scan(
		Object menu,
		Object screen,
		Function<ItemStack, Optional<BookmarkIngredientKey>> keyFactory
	) {
		for (Provider provider : PROVIDERS) {
			Optional<BookmarkContainerStorageScanner.StorageSnapshot> snapshot = provider.scan(menu, screen, keyFactory);
			if (snapshot.isPresent()) {
				return snapshot;
			}
		}
		return Optional.empty();
	}

	public static BookmarkContainerStorageScanner.StorageSnapshot createSnapshot(
		List<Entry> entries,
		Function<ItemStack, Optional<BookmarkIngredientKey>> keyFactory
	) {
		Map<BookmarkIngredientKey, Long> amounts = new LinkedHashMap<>();
		Map<BookmarkIngredientKey, ItemStack> representatives = new LinkedHashMap<>();
		for (Entry entry : entries) {
			if (entry.amount() <= 0 || entry.stack().isEmpty()) {
				continue;
			}
			ItemStack stack = normalized(entry.stack());
			keyFactory.apply(stack)
				.ifPresent(key -> {
					amounts.merge(key, entry.amount(), BookmarkExternalStorageSnapshots::saturatedAdd);
					representatives.putIfAbsent(key, stack);
				});
		}
		return new BookmarkContainerStorageScanner.StorageSnapshot(Map.copyOf(amounts), Map.copyOf(representatives));
	}

	private static ItemStack normalized(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.setCount(1);
		return copy;
	}

	private static long saturatedAdd(long first, long second) {
		try {
			return Math.addExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	public record Entry(ItemStack stack, long amount) {
		public Entry {
			stack = normalized(stack);
			amount = Math.max(0, amount);
		}
	}

	@FunctionalInterface
	public interface Provider {
		Optional<BookmarkContainerStorageScanner.StorageSnapshot> scan(
			Object menu,
			Object screen,
			Function<ItemStack, Optional<BookmarkIngredientKey>> keyFactory
		);
	}
}
