package mezz.jei.common.bookmarks;

import net.minecraft.world.item.ItemStack;

public record BookmarkPullTarget(ItemStack itemStack, int amount) {
	public BookmarkPullTarget {
		if (itemStack == null || itemStack.isEmpty() || amount <= 0) {
			itemStack = ItemStack.EMPTY;
			amount = 0;
		} else {
			itemStack = itemStack.copy();
			itemStack.setCount(1);
		}
	}

	public boolean isEmpty() {
		return itemStack.isEmpty() || amount <= 0;
	}
}
