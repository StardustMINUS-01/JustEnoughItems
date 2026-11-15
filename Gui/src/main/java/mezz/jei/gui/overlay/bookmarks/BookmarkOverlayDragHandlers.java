package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.Internal;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.MouseDrag;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay.FavoriteRecipeElementPanelSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay.FavoriteRecipeRowPanelSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkPanelProjection.GroupPanelSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

/**
 * Drag handlers for sorting bookmark groups and favorite recipe rows.
 */
public final class BookmarkOverlayDragHandlers {
	private static final int GROUP_PANEL_DRAG_THRESHOLD_MS = 250;
	private final BookmarkOverlay overlay;
	private final BookmarkDragScroll dragScroll = new BookmarkDragScroll(System::nanoTime);
	private final PageFlipHover pageFlipHover = new PageFlipHover(System::currentTimeMillis);

	public BookmarkOverlayDragHandlers(BookmarkOverlay overlay) {
		this.overlay = overlay;
	}

	void updateSortDrag(int mouseX, int mouseY) {
		BookmarkSortDragState state = overlay.getSortDragState();
		if (state != null) {
			state.update(overlay.getBookmarkList(), overlay.getPanelSlots(), overlay.getProjectedPanelSlots(), mouseX, mouseY, System.currentTimeMillis());
			if (state.isActive()) {
				overlay.scrollDuringDrag(dragScroll, mouseX, mouseY);
				PageFlipHover.Direction hovered = overlay.getHoveredPageEdge(mouseX, mouseY);
				PageFlipHover.Direction flip = pageFlipHover.update(hovered);
				if (flip != null) {
					switch (flip) {
						case NEXT -> overlay.getPageDelegate().nextPage();
						case PREVIOUS -> overlay.getPageDelegate().previousPage();
					}
				}
				overlay.setPageButtonsForcePressed(hovered == PageFlipHover.Direction.NEXT, hovered == PageFlipHover.Direction.PREVIOUS);
				overlay.getContents().updateLayout(false);
				state.updateProjectedSlotOverlayAreas(overlay.getProjectedPanelSlots());
			}
		}
	}

	void updateFavoriteSortDrag(int mouseX, int mouseY) {
		FavoriteRecipeSortDragState state = overlay.getFavoriteSortDragState();
		if (state != null) {
			boolean changed = state.update(overlay.getFavoriteRecipes(), overlay.getFavoritePanelState(),
				overlay.getFavoriteRecipeElementPanelSlots(), mouseX, mouseY, System.currentTimeMillis());
			if (changed || state.isActive()) {
				overlay.getFavoriteContents().updateLayout(false);
				state.updateProjectedSlotOverlayAreas(overlay.getFavoriteRecipeElementPanelSlots());
			}
		}
	}

	Optional<IInputInteraction> beginSortDrag(Screen screen, UserInput input) {
		return beginSortDrag(input, true);
	}

	Optional<IInputInteraction> beginPlainSortDrag(Screen screen, UserInput input) {
		return beginSortDrag(input, false);
	}

	private Optional<IInputInteraction> beginSortDrag(UserInput input, boolean shiftRequired) {
		if (!input.isMouseInput() || input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT ||
			InputModifiers.hasShift(input) != shiftRequired || overlay.getGroupPanelDrag() != null
		) {
			return Optional.empty();
		}
		var config = Internal.getClientConfigs().getClientConfig();
		if (!shiftRequired) {
			var player = Minecraft.getInstance().player;
			if (!config.dragToRearrangeBookmarksEnabled().get() || InputModifiers.hasControl(input) || InputModifiers.hasAlt(input) ||
				player == null || !player.containerMenu.getCarried().isEmpty()
			) {
				return Optional.empty();
			}
		}
		Optional<GroupPanelSlot> slot = overlay.getGroupPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
		if (shiftRequired && slot.filter(group -> group.groupId() != BookmarkGroupManager.DEFAULT_GROUP_ID).isPresent()) {
			overlay.setSortDragState(BookmarkSortDragState.group(slot.get().groupId(), slot.get().area(), input.getMouseX(), input.getMouseY()));
			return Optional.of(MouseDrag.forIngredient(ignored -> stopSortDrag(), this::stopSortDrag));
		}
		return overlay.getDraggableIngredientUnderMouse(input.getMouseX(), input.getMouseY())
			.findFirst()
			.flatMap(clicked -> clicked.getElement().getBookmark().map(bookmark -> {
				if (shiftRequired) {
					overlay.setSortDragState(BookmarkSortDragState.item(bookmark, clicked.getArea(),
						input.getMouseX(), input.getMouseY(), System.currentTimeMillis(), GROUP_PANEL_DRAG_THRESHOLD_MS));
				} else {
					overlay.setSortDragState(BookmarkSortDragState.delayedItem(bookmark, clicked.getArea(),
						input.getMouseX(), input.getMouseY(), System.currentTimeMillis(), config.dragDelayMs().get()));
				}
				return MouseDrag.forIngredient(ignored -> stopSortDrag(), this::stopSortDrag);
			}));
	}

	void stopSortDrag() {
		BookmarkSortDragState state = overlay.getSortDragState();
		if (state != null) {
			state.stop();
			overlay.setSortDragState(null);
			overlay.getContents().updateLayout(false);
		}
		pageFlipHover.update(null);
		dragScroll.update(mezz.jei.common.util.ImmutableRect2i.EMPTY, false, false, 0, 0);
		overlay.setPageButtonsForcePressed(false, false);
	}

	Optional<IInputInteraction> beginFavoriteSortDrag(Screen screen, UserInput input) {
		if (overlay.getFavoritePanelState().displayMode() != FavoriteRecipePanelState.DisplayMode.RECIPE_ROWS ||
			!input.isMouseInput() || input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT || !InputModifiers.hasShift(input)
		) {
			return Optional.empty();
		}
		Optional<FavoriteRecipeRowPanelSlot> row = overlay.getFavoriteRecipeRowPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
		if (row.isPresent()) {
			overlay.setFavoriteSortDragState(FavoriteRecipeSortDragState.recipe(row.get().recipe(), row.get().area(),
				input.getMouseX(), input.getMouseY(), System.currentTimeMillis(), GROUP_PANEL_DRAG_THRESHOLD_MS));
			return Optional.of(MouseDrag.forIngredient(ignored -> stopFavoriteSortDrag(), this::stopFavoriteSortDrag));
		}
		Optional<FavoriteRecipeElementPanelSlot> slot = overlay.getFavoriteRecipeElementSlotUnderMouse(input.getMouseX(), input.getMouseY());
		if (slot.isEmpty()) {
			return Optional.empty();
		}
		FavoriteRecipeElement<?> element = slot.get().element();
		if (element.isFavoriteTarget()) {
			overlay.setFavoriteSortDragState(FavoriteRecipeSortDragState.recipe(element.getFocusedRecipe(), slot.get().area(),
				input.getMouseX(), input.getMouseY(), System.currentTimeMillis(), GROUP_PANEL_DRAG_THRESHOLD_MS));
			return Optional.of(MouseDrag.forIngredient(ignored -> stopFavoriteSortDrag(), this::stopFavoriteSortDrag));
		}
		return element.getRecipeInputKey().map(inputKey -> {
			overlay.setFavoriteSortDragState(FavoriteRecipeSortDragState.input(element.getFocusedRecipe(), inputKey, slot.get().area(),
				input.getMouseX(), input.getMouseY(), System.currentTimeMillis(), GROUP_PANEL_DRAG_THRESHOLD_MS));
			return MouseDrag.forIngredient(ignored -> stopFavoriteSortDrag(), this::stopFavoriteSortDrag);
		});
	}

	void stopFavoriteSortDrag() {
		FavoriteRecipeSortDragState state = overlay.getFavoriteSortDragState();
		if (state != null) {
			state.stop();
			overlay.setFavoriteSortDragState(null);
			overlay.getFavoritePanelState().clearSortDragHiddenElements();
			overlay.getFavoriteContents().updateLayout(false);
		}
	}
}
