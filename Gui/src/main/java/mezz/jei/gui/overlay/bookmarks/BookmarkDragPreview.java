package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;

public record BookmarkDragPreview(
	ImmutableRect2i area,
	boolean rejected
) {
}
