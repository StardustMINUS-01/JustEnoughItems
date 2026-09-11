package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkItemMetadata;

public record RecipeChainItem(
	int sourceIndex,
	BookmarkItemMetadata metadata,
	RecipeChainItemType type,
	long realAmount,
	long shiftAmount,
	long calculatedAmount,
	long realMultiplier,
	long calculatedMultiplier
) {
	public long requiredAmount() {
		return type == RecipeChainItemType.INGREDIENT ? shiftAmount : 0;
	}

	public long providedAmount() {
		return type == RecipeChainItemType.INGREDIENT ? 0 : shiftAmount;
	}
}
