package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkSlotBorder;

import java.util.Optional;
import java.util.OptionalInt;

public record BookmarkSlotVisuals(
	OptionalInt backgroundColor,
	OptionalInt markerBackgroundColor,
	Optional<String> amountText,
	OptionalInt amountTextColor,
	Optional<String> multiplierText,
	OptionalInt multiplierTextColor,
	Optional<String> recipeMarkerText,
	OptionalInt recipeMarkerTextColor,
	Optional<BookmarkSlotBorder> border
) {
}
