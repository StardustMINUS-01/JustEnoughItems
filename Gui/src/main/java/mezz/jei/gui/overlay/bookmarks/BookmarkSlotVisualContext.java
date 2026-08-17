package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.config.BookmarkRecipeMarkerMode;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;

import java.util.Optional;

public record BookmarkSlotVisualContext(
	BookmarkSlotDisplayMode requestedMode,
	Optional<BookmarkDisplayEntry<?>> hoveredEntry,
	int rowIndex,
	int hoveredRowIndex,
	BookmarkRecipeMarkerMode recipeMarkerMode
) {
	public BookmarkSlotVisualContext {
		requestedMode = requestedMode == null ? BookmarkSlotDisplayMode.DEFAULT : requestedMode;
		hoveredEntry = hoveredEntry == null ? Optional.empty() : hoveredEntry;
		recipeMarkerMode = recipeMarkerMode == null ? BookmarkRecipeMarkerMode.NONE : recipeMarkerMode;
	}

	public BookmarkSlotVisualContext(
		BookmarkSlotDisplayMode requestedMode,
		Optional<BookmarkDisplayEntry<?>> hoveredEntry,
		int rowIndex,
		int hoveredRowIndex
	) {
		this(requestedMode, hoveredEntry, rowIndex, hoveredRowIndex, BookmarkRecipeMarkerMode.NONE);
	}

	public BookmarkSlotVisualContext(
		BookmarkSlotDisplayMode requestedMode,
		Optional<BookmarkDisplayEntry<?>> hoveredEntry
	) {
		this(requestedMode, hoveredEntry, -1, -1, BookmarkRecipeMarkerMode.NONE);
	}

	public static BookmarkSlotVisualContext none() {
		return new BookmarkSlotVisualContext(BookmarkSlotDisplayMode.DEFAULT, Optional.empty(), -1, -1, BookmarkRecipeMarkerMode.NONE);
	}
}
