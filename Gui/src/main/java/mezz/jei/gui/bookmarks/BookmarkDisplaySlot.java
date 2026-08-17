package mezz.jei.gui.bookmarks;

public record BookmarkDisplaySlot<T>(
	int slotIndex,
	BookmarkDisplayEntry<T> entry,
	boolean shadow,
	boolean firstOutput
) {
}
