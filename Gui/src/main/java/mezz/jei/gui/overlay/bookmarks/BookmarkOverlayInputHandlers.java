package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyContext;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyMouseButton;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeySubject;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkScrollHandler;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import mezz.jei.common.input.interaction.MouseDrag;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.common.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay.FavoriteRecipeElementPanelSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkPanelProjection.GroupPanelSlot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Input handlers for the bookmark overlay: group panels, recipe collapse, and favorite recipe rows.
 */
public final class BookmarkOverlayInputHandlers {
	private final BookmarkOverlay overlay;

	public BookmarkOverlayInputHandlers(BookmarkOverlay overlay) {
		this.overlay = overlay;
	}

	public class FavoriteRecipeRowInputHandler implements IInputTarget {
		@Override
		public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
			if (!input.isMouseInput() || input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT ||
				InputModifiers.hasShift(input) || InputModifiers.hasControl(input) || InputModifiers.hasAlt(input)
			) {
				return Optional.empty();
			}
			return overlay.getFavoriteRecipeRowPanelSlotUnderMouse(input.getMouseX(), input.getMouseY())
				.map(slot -> InputAction.run(() -> {
						if (overlay.getFavoritePanelState().toggleRecipeRowCollapsed(slot.recipe())) {
							playClickSound();
						}
					})
					.within(slot.area()::contains).when(overlay::isFavoritePanelDisplayed));
		}

		@Override
		public boolean scroll(double mouseX, double mouseY, double scrollDeltaX, double scrollDelta) {
			if (!overlay.isFavoritePanelDisplayed() ||
				scrollDelta == 0 ||
				!Screen.hasShiftDown() ||
				Screen.hasControlDown() ||
				Screen.hasAltDown()
			) {
				return false;
			}
			Optional<FavoriteRecipeElementPanelSlot> slot = overlay.getFavoriteRecipeElementSlotUnderMouse(mouseX, mouseY);
			if (slot.isEmpty()) {
				return false;
			}
			FavoriteRecipeElement<?> element = slot.get().element();
			Optional<FavoriteRecipeStore.FavoriteSlotInput> slotInput = element.getFavoriteSlotInput();
			if (slotInput.isEmpty()) {
				return false;
			}
			long step = scrollDelta > 0 ? 1 : -1;
			if (overlay.getFavoriteRecipes().cycleFavoriteInputs(element.getFocusedRecipe(), slotInput.get(), step)) {
				playClickSound();
				return true;
			}
			return false;
		}

	}

	public class RecipeCollapseInputHandler implements IInputTarget {
		@Override
		public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
			if (!input.isMouseInput() || input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT ||
				!InputModifiers.hasAlt(input) || InputModifiers.hasShift(input)
			) {
				return Optional.empty();
			}
			return overlay.getPanelSlots().stream()
				.filter(slot -> slot.area().contains(input.getMouseX(), input.getMouseY()))
				.filter(slot -> slot.recipeKey() instanceof ResourceLocation)
				.filter(slot -> overlay.getBookmarkList().isGroupCraftingMode(slot.groupId()))
				.findFirst()
				.map(target -> InputAction.run(() -> {
						int groupId = target.groupId();
						boolean collapsed = overlay.getBookmarkList().getBookmarkGroups().stream()
							.filter(group -> group.id() == groupId).findFirst().map(BookmarkGroup::collapsed).orElse(false);
						boolean changed = collapsed ? overlay.getBookmarkList().toggleGroupCollapsed(groupId) : overlay.getBookmarkList().toggleGroupCollapsedRecipeId(groupId, (ResourceLocation) target.recipeKey());
						if (changed) {
							playClickSound();
						}
					})
					.within(target.area()::contains).when(overlay::isListDisplayed));
		}
	}

	public class GroupInputHandler implements IInputTarget {
		private static final long SCROLL_STEP = 1;

		@Override
		public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
			if (!input.isMouseInput()) {
				return Optional.empty();
			}
			int mouseButton = input.getKey().getValue();
			if (mouseButton != InputConstants.MOUSE_BUTTON_LEFT && mouseButton != InputConstants.MOUSE_BUTTON_RIGHT) {
				return Optional.empty();
			}
			if (overlay.getScrollStepArea().contains(input.getMouseX(), input.getMouseY())) {
				if (mouseButton != InputConstants.MOUSE_BUTTON_RIGHT) {
					return Optional.empty();
				}
				return Optional.of(InputAction.run(() -> {
						overlay.getScrollStep().reset();
						overlay.getScrollStepField().syncFromScrollStep();
						playClickSound();
					})
					.within((x, y) -> overlay.getScrollStepArea().contains(x, y)));
			}
			if (!overlay.isListDisplayed()) {
				return Optional.empty();
			}
			BookmarkHotkeyMouseButton button = mouseButton == InputConstants.MOUSE_BUTTON_LEFT ? BookmarkHotkeyMouseButton.LEFT : BookmarkHotkeyMouseButton.RIGHT;
			if (overlay.hasDefaultGroupBookmarks() && overlay.getDefaultGroupControlArea().contains(input.getMouseX(), input.getMouseY())) {
				return BookmarkHotkeyRouter.resolveGroupMouseAction(createDefaultGroupControlHotkeyContext(), button,
					InputModifiers.hasShift(input), InputModifiers.hasAlt(input), InputModifiers.hasControl(input))
					.map(action -> InputAction.run(() -> applyClick(BookmarkGroupManager.DEFAULT_GROUP_ID, action))
						.within((x, y) -> overlay.getDefaultGroupControlArea().contains(x, y)).when(overlay::isListDisplayed));
			}
			Optional<GroupPanelSlot> slot = overlay.getGroupPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
			if (slot.isEmpty()) {
				return Optional.empty();
			}
			Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveGroupMouseAction(
				createGroupPanelHotkeyContext(slot.get()), button,
				InputModifiers.hasShift(input), InputModifiers.hasAlt(input), InputModifiers.hasControl(input));
			if (action.isEmpty()) {
				return Optional.empty();
			}
			BookmarkHotkeyAction selected = action.get();
			if (selected == BookmarkHotkeyAction.GROUP_TOGGLE_COLLAPSED) {
				return Optional.of(InputAction.run(() -> applyClick(slot.get().groupId(), selected))
					.within((x, y) -> overlay.getGroupPanelSlotUnderMouse(x, y)
						.filter(current -> current.groupId() == slot.get().groupId()).isPresent())
					.when(overlay::isListDisplayed));
			}
			if (selected == BookmarkHotkeyAction.GROUP_TOGGLE_VIEW_MODE || selected == BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING ||
				selected == BookmarkHotkeyAction.GROUP_DROP_DRAG || shouldStartGroupPanelDrag(selected)
			) {
				BookmarkHotkeyAction resolved = selected == BookmarkHotkeyAction.GROUP_DROP_DRAG &&
					!overlay.canStartGroupDrop(slot.get().groupId()) ? BookmarkHotkeyAction.GROUP_TOGGLE_VIEW_MODE : selected;
				GroupPanelDrag drag = new GroupPanelDrag(overlay, slot.get(),
					resolved == BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING || resolved == BookmarkHotkeyAction.GROUP_EXCLUDE_DRAG,
					shouldStartGroupPanelDrag(resolved) ? null : resolved, resolved == BookmarkHotkeyAction.GROUP_DROP_DRAG);
				overlay.setGroupPanelDrag(drag);
				return Optional.of(MouseDrag.forIngredient(release -> {
					try {
						if (overlay.isListDisplayed()) {
							drag.complete(release);
						}
					} finally {
						overlay.setGroupPanelDrag(null);
					}
				}, () -> overlay.setGroupPanelDrag(null)));
			}
			return Optional.empty();
		}

		private void applyClick(int groupId, BookmarkHotkeyAction action) {
			if (applyGroupClickAction(groupId, action)) {
				playClickSound();
			}
		}

		@Override
		public boolean scroll(double mouseX, double mouseY, double scrollDeltaX, double scrollDelta) {
			var drag = overlay.getGroupPanelDrag();
			if (drag != null && !drag.isDropMode()) {
				drag.scroll(scrollDeltaX, scrollDelta, mouseY);
				return true;
			}
			if (overlay.getScrollStepArea().contains(mouseX, mouseY)) {
				if (scrollDelta == 0) {
					return false;
				}
				long direction = scrollDelta > 0 ? 1 : -1;
				long step = Screen.hasControlDown() ? 64 : 1;
				overlay.getScrollStep().add(direction * step);
				overlay.getScrollStepField().syncFromScrollStep();
				playClickSound();
				return true;
			}

			if (!overlay.isListDisplayed()) {
				return false;
			}
			Optional<GroupPanelSlot> groupSlot = overlay.getGroupPanelSlotUnderMouse(mouseX, mouseY);
			boolean defaultControl = overlay.hasDefaultGroupBookmarks() && overlay.getDefaultGroupControlArea().contains(mouseX, mouseY);
			if (!overlay.isMouseOver(mouseX, mouseY) && groupSlot.isEmpty() && !defaultControl) {
				return false;
			}
			boolean controlDown = Screen.hasControlDown();
			boolean altDown = Screen.hasAltDown();
			boolean shiftDown = Screen.hasShiftDown();
			if (!controlDown && !shiftDown && !altDown) {
				return false;
			}

			if (groupSlot.isPresent() || defaultControl) {
				int groupId = groupSlot.map(GroupPanelSlot::groupId).orElse(BookmarkGroupManager.DEFAULT_GROUP_ID);
				BookmarkHotkeyContext context = groupSlot
					.map(BookmarkOverlayInputHandlers::createGroupPanelHotkeyContext)
					.orElseGet(BookmarkOverlayInputHandlers::createDefaultGroupControlHotkeyContext);
				Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, controlDown, altDown, shiftDown);
				if (action.filter(a -> a == BookmarkHotkeyAction.SHIFT_AMOUNT || a == BookmarkHotkeyAction.SHIFT_AMOUNT_STEP).isPresent()) {
					long step = getScrollStep(scrollDelta, action.get());
					if (overlay.getBookmarkList().shiftGroupAmount(groupId, step)) {
						playClickSound();
						return true;
					}
				}
				return false;
			}

			Optional<IBookmark> bookmark = overlay.getBookmarkUnderMouse(mouseX, mouseY);
			if (bookmark.isPresent() && BookmarkScrollHandler.apply(
				overlay.getBookmarkList(), bookmark.get(), scrollDelta, controlDown, altDown, shiftDown, overlay.getScrollStep().getEffectiveStep(),
				step -> overlay.getBookmarkList().shiftBookmarkAmount(bookmark.get(), step))
			) {
				playClickSound();
				return true;
			}
			return false;
		}

		private long getScrollStep(double scrollDelta, BookmarkHotkeyAction action) {
			long direction = scrollDelta > 0 ? 1 : -1;
			long step = action == BookmarkHotkeyAction.SHIFT_AMOUNT_STEP ? overlay.getScrollStep().getEffectiveStep() : SCROLL_STEP;
			return direction * step;
		}
	}

	boolean applyGroupClickAction(int groupId, BookmarkHotkeyAction action) {
		BookmarkList bookmarkList = overlay.getBookmarkList();
		return switch (action) {
			case GROUP_TOGGLE_COLLAPSED -> bookmarkList.toggleGroupCollapsed(groupId);
			case GROUP_TOGGLE_VIEW_MODE -> bookmarkList.toggleGroupViewMode(groupId);
			case GROUP_TOGGLE_CRAFTING -> {
				bookmarkList.setGroupCraftingMode(groupId, !bookmarkList.isGroupCraftingMode(groupId));
				yield true;
			}
			default -> false;
		};
	}

	public static boolean shouldStartGroupPanelDrag(BookmarkHotkeyAction action) {
		return action == BookmarkHotkeyAction.GROUP_CREATE_OR_INCLUDE_DRAG ||
			action == BookmarkHotkeyAction.GROUP_EXCLUDE_DRAG;
	}

	private static void playClickSound() {
		JeiClientSoundUtil.playClickSound();
	}

	static BookmarkHotkeyContext createGroupPanelHotkeyContext(GroupPanelSlot slot) {
		boolean grouped = !(slot.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID);
		if (!grouped) {
			return BookmarkHotkeyContext.builder(BookmarkHotkeySubject.EMPTY_GROUP_PANEL)
				.build();
		}
		return BookmarkHotkeyContext.builder(BookmarkHotkeySubject.GROUP)
			.isGrouped(true)
			.build();
	}

	private static BookmarkHotkeyContext createDefaultGroupControlHotkeyContext() {
		return BookmarkHotkeyContext.builder(BookmarkHotkeySubject.DEFAULT_GROUP_CONTROL)
			.build();
	}

}
