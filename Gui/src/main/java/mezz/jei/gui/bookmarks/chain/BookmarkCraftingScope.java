package mezz.jei.gui.bookmarks.chain;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Session-scoped list of items used by the active bookmark recipe-chain craft task.
 * While non-empty, external storage snapshot readers only query these items instead of
 * iterating the full terminal repository. Cleared when the task ends and never persisted.
 */
public final class BookmarkCraftingScope {
	private static List<ItemStack> interests = List.of();

	private BookmarkCraftingScope() {
	}

	public static void setInterests(List<ItemStack> items) {
		interests = items.stream()
			.filter(stack -> !stack.isEmpty())
			.toList();
	}

	public static void clear() {
		interests = List.of();
	}

	public static List<ItemStack> getInterests() {
		return interests;
	}
}
