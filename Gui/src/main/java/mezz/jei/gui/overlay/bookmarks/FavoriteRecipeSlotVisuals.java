package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.gui.favorites.FavoriteRecipeElement;

import java.util.Optional;
import java.util.OptionalInt;

public final class FavoriteRecipeSlotVisuals {
	private FavoriteRecipeSlotVisuals() {
	}

	public static Optional<BookmarkSlotVisuals> create(FavoriteRecipeElement<?> element) {
		return element.getAmountText()
			.map(amountText -> new BookmarkSlotVisuals(
				OptionalInt.empty(),
				OptionalInt.empty(),
				Optional.of(amountText),
				OptionalInt.empty(),
				Optional.empty(),
				OptionalInt.empty(),
				Optional.empty(),
				OptionalInt.empty(),
				Optional.empty()
			));
	}
}
