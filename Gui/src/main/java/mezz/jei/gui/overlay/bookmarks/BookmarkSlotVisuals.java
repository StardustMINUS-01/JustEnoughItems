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
	public BookmarkSlotVisuals(
		OptionalInt backgroundColor,
		OptionalInt markerBackgroundColor,
		Optional<String> amountText,
		OptionalInt amountTextColor,
		Optional<String> multiplierText,
		Optional<String> recipeMarkerText
	) {
		this(backgroundColor, markerBackgroundColor, amountText, amountTextColor, multiplierText, OptionalInt.empty(), recipeMarkerText, OptionalInt.empty(), Optional.empty());
	}

	public BookmarkSlotVisuals(
		OptionalInt backgroundColor,
		OptionalInt markerBackgroundColor,
		Optional<String> amountText,
		Optional<String> multiplierText,
		Optional<String> recipeMarkerText
	) {
		this(backgroundColor, markerBackgroundColor, amountText, OptionalInt.empty(), multiplierText, OptionalInt.empty(), recipeMarkerText, OptionalInt.empty(), Optional.empty());
	}

	public BookmarkSlotVisuals {
		backgroundColor = backgroundColor == null ? OptionalInt.empty() : backgroundColor;
		markerBackgroundColor = markerBackgroundColor == null ? OptionalInt.empty() : markerBackgroundColor;
		amountText = amountText == null ? Optional.empty() : amountText;
		amountTextColor = amountTextColor == null ? OptionalInt.empty() : amountTextColor;
		multiplierText = multiplierText == null ? Optional.empty() : multiplierText;
		multiplierTextColor = multiplierTextColor == null ? OptionalInt.empty() : multiplierTextColor;
		recipeMarkerText = recipeMarkerText == null ? Optional.empty() : recipeMarkerText;
		recipeMarkerTextColor = recipeMarkerTextColor == null ? OptionalInt.empty() : recipeMarkerTextColor;
		border = border == null ? Optional.empty() : border;
	}

	public static BookmarkSlotVisuals empty() {
		return new BookmarkSlotVisuals(
			OptionalInt.empty(),
			OptionalInt.empty(),
			Optional.empty(),
			OptionalInt.empty(),
			Optional.empty(),
			OptionalInt.empty(),
			Optional.empty(),
			OptionalInt.empty(),
			Optional.empty()
		);
	}
}
