/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.hotkeys;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/**
 * Draws GTNH NEI DefaultOverlayRenderer-style ghost items in container-local coordinates.
 */
public final class BookmarkGhostOverlayRenderer {
	private static final int HOVER_COLOR = 0x66555555;
	private static final int SLOT_SIZE = 16;

	private BookmarkGhostOverlayRenderer() {
	}

	public static void render(GuiGraphics guiGraphics, BookmarkGhostOverlay overlay) {
		for (BookmarkGhostOverlay.Entry entry : overlay.entries()) {
			guiGraphics.renderFakeItem(entry.itemStack(), entry.x(), entry.y());
			guiGraphics.fill(
				RenderType.guiOverlay(),
				entry.x(),
				entry.y(),
				entry.x() + SLOT_SIZE,
				entry.y() + SLOT_SIZE,
				HOVER_COLOR
			);
		}
	}
}
