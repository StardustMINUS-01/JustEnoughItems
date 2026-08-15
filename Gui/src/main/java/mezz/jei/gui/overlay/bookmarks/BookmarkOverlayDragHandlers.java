package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.input.IDragHandler;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay.FavoriteRecipeElementPanelSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay.FavoriteRecipeRowPanelSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlayLayout.GroupPanelSlot;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

/**
 * Drag handlers for sorting bookmark groups and favorite recipe rows.
 */
public final class BookmarkOverlayDragHandlers {
	private static final int GROUP_PANEL_DRAG_THRESHOLD_MS = 250;

	private final BookmarkOverlay overlay;

	public BookmarkOverlayDragHandlers(BookmarkOverlay overlay) {
		this.overlay = overlay;
	}

	void updateSortDrag(int mouseX, int mouseY) {
		if (overlay.getSortDragState() != null) {
			BookmarkSortDragState sortDragState = overlay.getSortDragState();
			sortDragState.update(overlay.getBookmarkList(), overlay.getPanelSlots(), overlay.getProjectedPanelSlots(), mouseX, mouseY, System.currentTimeMillis());
			if (sortDragState.isActive()) {
				overlay.getContents().updateLayout(false);
				sortDragState.updateProjectedSlotOverlayAreas(overlay.getProjectedPanelSlots());
			}
		}
	}

	void updateFavoriteSortDrag(int mouseX, int mouseY) {
		if (overlay.getFavoriteSortDragState() != null) {
			FavoriteRecipeSortDragState favoriteSortDragState = overlay.getFavoriteSortDragState();
			boolean changed = favoriteSortDragState.update(
				overlay.getFavoriteRecipes(),
				overlay.getFavoritePanelState(),
				overlay.getFavoriteRecipeElementPanelSlots(),
				mouseX,
				mouseY,
				System.currentTimeMillis()
			);
			if (changed || favoriteSortDragState.isActive()) {
				overlay.getFavoriteContents().updateLayout(false);
				favoriteSortDragState.updateProjectedSlotOverlayAreas(overlay.getFavoriteRecipeElementPanelSlots());
			}
		}
	}

	IDragHandler createSortDragHandler() {
		return new IDragHandler() {
			@Override
			public Optional<IDragHandler> handleDragStart(Screen screen, UserInput input) {
				if (input.getKey().getType() != InputConstants.Type.MOUSE) {
					return Optional.empty();
				}
				int mouseButton = input.getKey().getValue();
				if (mouseButton != InputConstants.MOUSE_BUTTON_LEFT || !InputModifiers.hasShift(input)) {
					return Optional.empty();
				}
				Optional<GroupPanelSlot> slot = overlay.getGroupPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
				if (slot.filter(groupSlot -> !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupSlot.groupId())).isPresent()) {
					overlay.setSortDragState(BookmarkSortDragState.group(
						slot.get().groupId(),
						slot.get().area(),
						input.getMouseX(),
						input.getMouseY()
					));
					return Optional.of(this);
				}

				return overlay.getDraggableIngredientUnderMouse(input.getMouseX(), input.getMouseY())
					.findFirst()
					.flatMap(clicked -> clicked.getElement()
						.getBookmark()
						.map(bookmark -> {
							overlay.setSortDragState(BookmarkSortDragState.item(
								bookmark,
								clicked.getArea(),
								input.getMouseX(),
								input.getMouseY(),
								System.currentTimeMillis(),
								GROUP_PANEL_DRAG_THRESHOLD_MS
							));
							return this;
						}));
			}

			@Override
			public boolean handleDragComplete(Screen screen, UserInput input) {
				BookmarkSortDragState sortDragState = overlay.getSortDragState();
				if (sortDragState == null) {
					return false;
				}
				boolean handled = sortDragState.isActive();
				sortDragState.stop();
				overlay.setSortDragState(null);
				overlay.getContents().updateLayout(false);
				return handled;
			}

			@Override
			public void handleDragCanceled() {
				BookmarkSortDragState sortDragState = overlay.getSortDragState();
				if (sortDragState != null) {
					sortDragState.stop();
					overlay.setSortDragState(null);
					overlay.getContents().updateLayout(false);
				}
			}
		};
	}

	IDragHandler createFavoriteSortDragHandler() {
		return new IDragHandler() {
			@Override
			public Optional<IDragHandler> handleDragStart(Screen screen, UserInput input) {
				if (overlay.getFavoritePanelState().displayMode() != FavoriteRecipePanelState.DisplayMode.RECIPE_ROWS) {
					return Optional.empty();
				}
				if (input.getKey().getType() != InputConstants.Type.MOUSE) {
					return Optional.empty();
				}
				int mouseButton = input.getKey().getValue();
				if (mouseButton != InputConstants.MOUSE_BUTTON_LEFT || !InputModifiers.hasShift(input)) {
					return Optional.empty();
				}

				Optional<FavoriteRecipeRowPanelSlot> rowPanelSlot = overlay.getFavoriteRecipeRowPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
				if (rowPanelSlot.isPresent()) {
					overlay.setFavoriteSortDragState(FavoriteRecipeSortDragState.recipe(
						rowPanelSlot.get().recipe(),
						rowPanelSlot.get().area(),
						input.getMouseX(),
						input.getMouseY(),
						System.currentTimeMillis(),
						GROUP_PANEL_DRAG_THRESHOLD_MS
					));
					return Optional.of(this);
				}

				Optional<FavoriteRecipeElementPanelSlot> elementSlot = overlay.getFavoriteRecipeElementSlotUnderMouse(input.getMouseX(), input.getMouseY());
				if (elementSlot.isEmpty()) {
					return Optional.empty();
				}
				FavoriteRecipeElement<?> element = elementSlot.get().element();
				if (element.isFavoriteTarget()) {
					overlay.setFavoriteSortDragState(FavoriteRecipeSortDragState.recipe(
						element.getFocusedRecipe(),
						elementSlot.get().area(),
						input.getMouseX(),
						input.getMouseY(),
						System.currentTimeMillis(),
						GROUP_PANEL_DRAG_THRESHOLD_MS
					));
					return Optional.of(this);
				}
				return element.getRecipeInputKey()
					.map(inputKey -> {
						overlay.setFavoriteSortDragState(FavoriteRecipeSortDragState.input(
							element.getFocusedRecipe(),
							inputKey,
							elementSlot.get().area(),
							input.getMouseX(),
							input.getMouseY(),
							System.currentTimeMillis(),
							GROUP_PANEL_DRAG_THRESHOLD_MS
						));
						return this;
					});
			}

			@Override
			public boolean handleDragComplete(Screen screen, UserInput input) {
				FavoriteRecipeSortDragState favoriteSortDragState = overlay.getFavoriteSortDragState();
				if (favoriteSortDragState == null) {
					return false;
				}
				boolean handled = favoriteSortDragState.isActive();
				favoriteSortDragState.stop();
				overlay.setFavoriteSortDragState(null);
				overlay.getFavoritePanelState().clearSortDragHiddenElements();
				overlay.getFavoriteContents().updateLayout(false);
				return handled;
			}

			@Override
			public void handleDragCanceled() {
				FavoriteRecipeSortDragState favoriteSortDragState = overlay.getFavoriteSortDragState();
				if (favoriteSortDragState != null) {
					favoriteSortDragState.stop();
					overlay.setFavoriteSortDragState(null);
					overlay.getFavoritePanelState().clearSortDragHiddenElements();
					overlay.getFavoriteContents().updateLayout(false);
				}
			}
		};
	}
}
