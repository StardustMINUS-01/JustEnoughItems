/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.hotkeys;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Equivalent lifecycle role to GTNH NEI's LayoutManager.overlayRenderer.
 */
public final class BookmarkGhostOverlayState {
	public static final BookmarkGhostOverlayState INSTANCE = new BookmarkGhostOverlayState();

	private int containerId = -1;
	private @Nullable BookmarkGhostOverlay active;

	public void setActive(AbstractContainerMenu menu, BookmarkGhostOverlay overlay) {
		setActive(menu.containerId, overlay);
	}

	public void setActive(int containerId, BookmarkGhostOverlay overlay) {
		this.containerId = containerId;
		this.active = overlay;
	}

	public Optional<BookmarkGhostOverlay> getActive(AbstractContainerMenu menu) {
		return getActive(menu.containerId);
	}

	public Optional<BookmarkGhostOverlay> getActive(int containerId) {
		if (this.containerId != containerId) {
			return Optional.empty();
		}
		return Optional.ofNullable(active);
	}

	public void clear() {
		this.containerId = -1;
		this.active = null;
	}
}
