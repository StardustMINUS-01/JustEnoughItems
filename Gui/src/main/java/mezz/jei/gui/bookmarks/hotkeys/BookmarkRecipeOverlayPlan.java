/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.hotkeys;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Mirrors GTNH NEI's split between overlay renderer and crafting-grid fill.
 */
public record BookmarkRecipeOverlayPlan(
	Kind kind,
	OptionalInt multiplier
) {
	public enum Kind {
		GHOST_OVERLAY,
		FILL_CRAFTING_GRID
	}

	public static Optional<BookmarkRecipeOverlayPlan> fromHotkeyAction(
		BookmarkHotkeyAction action,
		OptionalInt bookmarkQuantity
	) {
		return switch (action) {
			case OVERLAY_RECIPE -> Optional.of(new BookmarkRecipeOverlayPlan(Kind.GHOST_OVERLAY, OptionalInt.empty()));
			case FILL_CRAFTING_GRID -> Optional.of(new BookmarkRecipeOverlayPlan(Kind.FILL_CRAFTING_GRID, OptionalInt.of(0)));
			case FILL_CRAFTING_GRID_QUANTITY -> Optional.of(new BookmarkRecipeOverlayPlan(
				Kind.FILL_CRAFTING_GRID,
				OptionalInt.of(bookmarkQuantity.stream()
					.map(quantity -> Math.min(64, Math.max(1, quantity)))
					.findFirst()
					.orElse(1))
			));
			default -> Optional.empty();
		};
	}

	public boolean movesItems() {
		return kind == Kind.FILL_CRAFTING_GRID;
	}
}
