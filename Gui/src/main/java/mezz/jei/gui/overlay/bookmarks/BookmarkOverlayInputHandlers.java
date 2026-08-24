package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.Internal;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.packets.PacketRequestCheatPermission;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyContext;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyMouseButton;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeySubject;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay.FavoriteRecipeElementPanelSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay.FavoriteRecipeRowPanelSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlayLayout.GroupPanelSlot;
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

	public class FavoriteRecipeRowInputHandler implements IUserInputHandler {
		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			if (input.getKey().getType() != InputConstants.Type.MOUSE ||
				input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT ||
				InputModifiers.hasShift(input) ||
				InputModifiers.hasControl(input) ||
				InputModifiers.hasAlt(input)) {
				return Optional.empty();
			}
			Optional<FavoriteRecipeRowPanelSlot> slot = overlay.getFavoriteRecipeRowPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
			if (slot.isEmpty()) {
				return Optional.empty();
			}
			if (input.getInputType() == InputType.EXECUTE && overlay.getFavoritePanelState().toggleRecipeRowCollapsed(slot.get().recipe())) {
				playClickSound();
			}
			return Optional.of(this);
		}

		@Override
		public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDelta) {
			if (!overlay.isFavoritePanelDisplayed() ||
				scrollDelta == 0 ||
				!Screen.hasShiftDown() ||
				Screen.hasControlDown() ||
				Screen.hasAltDown()) {
				return Optional.empty();
			}
			Optional<FavoriteRecipeElementPanelSlot> slot = overlay.getFavoriteRecipeElementSlotUnderMouse(mouseX, mouseY);
			if (slot.isEmpty()) {
				return Optional.empty();
			}
			FavoriteRecipeElement<?> element = slot.get().element();
			Optional<FavoriteRecipeStore.FavoriteSlotInput> slotInput = element.getFavoriteSlotInput();
			if (slotInput.isEmpty()) {
				return Optional.empty();
			}
			long step = scrollDelta > 0 ? 1 : -1;
			if (overlay.getFavoriteRecipes().cycleFavoriteInputs(element.getFocusedRecipe(), slotInput.get(), step)) {
				playClickSound();
				return Optional.of(this);
			}
			return Optional.empty();
		}
	}

	public class RecipeCollapseInputHandler implements IUserInputHandler {
		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			if (input.getKey().getType() != InputConstants.Type.MOUSE ||
				input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT ||
				!InputModifiers.hasAlt(input) ||
				InputModifiers.hasShift(input)) {
				return Optional.empty();
			}
			Optional<BookmarkPanelLayout.PanelSlot<IBookmark>> target = overlay.getPanelSlots().stream()
				.filter(slot -> slot.area().contains(input.getMouseX(), input.getMouseY()))
				.filter(slot -> slot.recipeKey() instanceof ResourceLocation)
				.filter(slot -> overlay.getBookmarkList().isGroupCraftingMode(slot.groupId()))
				.findFirst();
			if (target.isEmpty()) {
				return Optional.empty();
			}
			if (input.getInputType() == InputType.EXECUTE) {
				String groupId = target.get().groupId();
				boolean collapsed = overlay.getBookmarkList().getBookmarkGroups().stream()
					.filter(group -> group.id().equals(groupId))
					.findFirst()
					.map(group -> group.viewMode() == BookmarkViewMode.COLLAPSED)
					.orElse(false);
				if (collapsed) {
					overlay.getBookmarkList().toggleGroupCollapsed(groupId);
					playClickSound();
					return Optional.of(this);
				}
				ResourceLocation recipeUid = (ResourceLocation) target.get().recipeKey();
				if (overlay.getBookmarkList().toggleGroupCollapsedRecipeId(groupId, recipeUid)) {
					playClickSound();
				}
			}
			return Optional.of(this);
		}
	}

	public class GroupInputHandler implements IUserInputHandler {
		private static final long SCROLL_STEP = 1;

		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			if (input.getKey().getType() != InputConstants.Type.MOUSE) {
				return Optional.empty();
			}

			int mouseButton = input.getKey().getValue();
			if (mouseButton != InputConstants.MOUSE_BUTTON_LEFT && mouseButton != InputConstants.MOUSE_BUTTON_RIGHT) {
				return Optional.empty();
			}

			if (overlay.getScrollStepArea().contains(input.getMouseX(), input.getMouseY())) {
				boolean ctrlLeftClick = InputModifiers.hasControl(input) && mouseButton == InputConstants.MOUSE_BUTTON_LEFT;
				boolean rightClick = mouseButton == InputConstants.MOUSE_BUTTON_RIGHT;
				if (!ctrlLeftClick && !rightClick) {
					return Optional.empty();
				}
				if (input.getInputType() == InputType.EXECUTE) {
					if (ctrlLeftClick) {
						toggleFastPickup();
					} else {
						overlay.getScrollStep().reset();
						overlay.getScrollStepField().syncFromScrollStep();
					}
					playClickSound();
				}
				return Optional.of(this);
			}

			BookmarkHotkeyMouseButton hotkeyMouseButton = mouseButton == InputConstants.MOUSE_BUTTON_LEFT ?
				BookmarkHotkeyMouseButton.LEFT :
				BookmarkHotkeyMouseButton.RIGHT;
			if (input.getInputType() == InputType.EXECUTE && overlay.getGroupPanelDrag() != null) {
				boolean handled = overlay.getGroupPanelDrag().complete(input);
				overlay.setGroupPanelDrag(null);
				return handled ? Optional.of(this) : Optional.empty();
			}

			if (overlay.hasDefaultGroupBookmarks() && overlay.getDefaultGroupControlArea().contains(input.getMouseX(), input.getMouseY())) {
				BookmarkHotkeyContext context = createDefaultGroupControlHotkeyContext();
				Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveGroupMouseAction(
					context,
					hotkeyMouseButton,
					InputModifiers.hasShift(input),
					InputModifiers.hasAlt(input),
					InputModifiers.hasControl(input)
				);
				if (action.isEmpty()) {
					return Optional.empty();
				}
				if (input.getInputType() == InputType.EXECUTE) {
					if (applyGroupClickAction(BookmarkGroupManager.DEFAULT_GROUP_ID, action.get())) {
						playClickSound();
					}
				}
				return Optional.of(this);
			}

			Optional<GroupPanelSlot> slot = overlay.getGroupPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
			if (slot.isEmpty()) {
				return Optional.empty();
			}

			BookmarkHotkeyContext context = createGroupPanelHotkeyContext(slot.get());
			Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveGroupMouseAction(
				context,
				hotkeyMouseButton,
				InputModifiers.hasShift(input),
				InputModifiers.hasAlt(input),
				InputModifiers.hasControl(input)
			);
			if (action.isEmpty()) {
				return Optional.empty();
			}

			if (action.get() == BookmarkHotkeyAction.GROUP_TOGGLE_COLLAPSED) {
				if (input.getInputType() == InputType.EXECUTE) {
					if (applyGroupClickAction(slot.get().groupId(), action.get())) {
						playClickSound();
					}
				}
				return Optional.of(this);
			}

			if (action.get() == BookmarkHotkeyAction.GROUP_TOGGLE_VIEW_MODE ||
				action.get() == BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING ||
				action.get() == BookmarkHotkeyAction.GROUP_DROP_DRAG) {
				if (input.getInputType() == InputType.SIMULATE) {
					BookmarkHotkeyAction resolvedAction = action.get();
					if (resolvedAction == BookmarkHotkeyAction.GROUP_DROP_DRAG && !overlay.canStartGroupDrop(slot.get().groupId())) {
						resolvedAction = BookmarkHotkeyAction.GROUP_TOGGLE_VIEW_MODE;
					}
					overlay.setGroupPanelDrag(new GroupPanelDrag(
						overlay,
						slot.get(),
						resolvedAction == BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING,
						resolvedAction,
						resolvedAction == BookmarkHotkeyAction.GROUP_DROP_DRAG
					));
					return Optional.of(this);
				}
				if (input.getInputType() == InputType.EXECUTE && applyGroupClickAction(slot.get().groupId(), action.get())) {
					playClickSound();
					return Optional.of(this);
				}
				return Optional.empty();
			}

			if (input.getInputType() == InputType.SIMULATE && shouldStartGroupPanelDrag(action.get())) {
				overlay.setGroupPanelDrag(new GroupPanelDrag(
					overlay,
					slot.get(),
					action.get() == BookmarkHotkeyAction.GROUP_EXCLUDE_DRAG,
					null
				));
				return Optional.of(this);
			}
			return Optional.empty();
		}

		@Override
		public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDelta) {
			if (overlay.getScrollStepArea().contains(mouseX, mouseY)) {
				if (scrollDelta == 0) {
					return Optional.empty();
				}
				long direction = scrollDelta > 0 ? 1 : -1;
				long step = Screen.hasControlDown() ? 64 : 1;
				overlay.getScrollStep().add(direction * step);
				overlay.getScrollStepField().syncFromScrollStep();
				playClickSound();
				return Optional.of(this);
			}

			Optional<GroupPanelSlot> groupSlot = overlay.getGroupPanelSlotUnderMouse(mouseX, mouseY);
			boolean defaultControl = overlay.hasDefaultGroupBookmarks() && overlay.getDefaultGroupControlArea().contains(mouseX, mouseY);
			if (!overlay.isMouseOver(mouseX, mouseY) && groupSlot.isEmpty() && !defaultControl) {
				return Optional.empty();
			}
			boolean controlDown = Screen.hasControlDown();
			boolean altDown = Screen.hasAltDown();
			boolean shiftDown = Screen.hasShiftDown();
			if (!controlDown && !shiftDown && !altDown) {
				return Optional.empty();
			}

			if (groupSlot.isPresent() || defaultControl) {
				String groupId = groupSlot.map(GroupPanelSlot::groupId).orElse(BookmarkGroupManager.DEFAULT_GROUP_ID);
				BookmarkHotkeyContext context = groupSlot
					.map(BookmarkOverlayInputHandlers::createGroupPanelHotkeyContext)
					.orElseGet(BookmarkOverlayInputHandlers::createDefaultGroupControlHotkeyContext);
				Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, controlDown, altDown, shiftDown);
				if (action.filter(a -> a == BookmarkHotkeyAction.SHIFT_AMOUNT || a == BookmarkHotkeyAction.SHIFT_AMOUNT_STEP).isPresent()) {
					long step = getScrollStep(scrollDelta, action.get());
					if (overlay.getBookmarkList().shiftGroupAmount(groupId, step)) {
						playClickSound();
						return Optional.of(this);
					}
				}
				return Optional.empty();
			}

			Optional<IBookmark> bookmark = overlay.getBookmarkUnderMouse(mouseX, mouseY);
			if (bookmark.isPresent()) {
				BookmarkHotkeyContext context = createBookmarkHotkeyContext(bookmark.get());
				Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, controlDown, altDown, shiftDown);
				if (action.filter(a -> a == BookmarkHotkeyAction.SHIFT_AMOUNT || a == BookmarkHotkeyAction.SHIFT_AMOUNT_STEP).isPresent()) {
					long step = getScrollStep(scrollDelta, action.get());
					if (overlay.getBookmarkList().shiftBookmarkAmount(bookmark.get(), step)) {
						playClickSound();
						return Optional.of(this);
					}
				} else if (action.filter(a -> a == BookmarkHotkeyAction.CYCLE_PERMUTATION).isPresent()) {
					long step = getScrollStep(scrollDelta, action.get());
					if (overlay.getBookmarkList().cycleBookmarkPermutation(bookmark.get(), step)) {
						playClickSound();
						return Optional.of(this);
					}
				} else if (action.filter(a -> a == BookmarkHotkeyAction.TOGGLE_INPUT_CATALYST).isPresent()) {
					if (overlay.getBookmarkList().toggleBookmarkInputCatalyst(bookmark.get())) {
						playClickSound();
						return Optional.of(this);
					}
				}
			}
			return Optional.empty();
		}

		private long getScrollStep(double scrollDelta, BookmarkHotkeyAction action) {
			long direction = scrollDelta > 0 ? 1 : -1;
			long step = action == BookmarkHotkeyAction.SHIFT_AMOUNT_STEP ? overlay.getScrollStep().getEffectiveStep() : SCROLL_STEP;
			return direction * step;
		}
	}

	private void toggleFastPickup() {
		overlay.getToggleState().toggleFastPickupEnabled();
		overlay.getScrollStepField().syncFromScrollStep();
		if (overlay.getToggleState().isFastPickupEnabled()) {
			Internal.getServerConnection().sendPacketToServer(PacketRequestCheatPermission.INSTANCE);
		}
	}

	boolean applyGroupClickAction(String groupId, BookmarkHotkeyAction action) {
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
		boolean grouped = !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(slot.groupId());
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

	private BookmarkHotkeyContext createBookmarkHotkeyContext(IBookmark bookmark) {
		BookmarkItemMetadata metadata = overlay.getBookmarkList().getBookmarkMetadata(bookmark);
		BookmarkHotkeySubject subject = metadata.recipeUid() == null ? BookmarkHotkeySubject.ITEM_BOOKMARK : BookmarkHotkeySubject.RECIPE_BOOKMARK;
		return BookmarkHotkeyContext.builder(subject)
			.hasIngredient(true)
			.hasRecipe(metadata.recipeUid() != null)
			.isBookmarkSlot(true)
			.build();
	}
}
