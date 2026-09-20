package mezz.jei.gui.bookmarks.chain;

import mezz.jei.common.util.SaturatedMath;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class BookmarkContainerStorageScanner {
	private BookmarkContainerStorageScanner() {
	}

	public static StorageSnapshot scan(
		AbstractContainerMenu menu,
		Container playerInventory,
		Function<ItemStack, Optional<BookmarkIngredientKey>> keyFactory
	) {
		Map<BookmarkIngredientKey, Long> amounts = new LinkedHashMap<>();
		Map<BookmarkIngredientKey, ItemStack> representatives = new LinkedHashMap<>();
		for (Slot slot : menu.slots) {
			if (slot.container == playerInventory) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			keyFactory.apply(normalized(stack))
				.ifPresent(key -> {
					amounts.merge(key, (long) stack.getCount(), SaturatedMath::add);
					representatives.putIfAbsent(key, normalized(stack));
				});
		}
		return new StorageSnapshot(Map.copyOf(amounts), Map.copyOf(representatives));
	}

	private static ItemStack normalized(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.setCount(1);
		return copy;
	}

	public record StorageSnapshot(
		Map<BookmarkIngredientKey, Long> amounts,
		Map<BookmarkIngredientKey, ItemStack> representatives
	) {
	}
}
