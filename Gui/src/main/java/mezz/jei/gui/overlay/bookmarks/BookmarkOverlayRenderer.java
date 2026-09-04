package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.BookmarkHotkeyTooltipUtil;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.network.packets.PacketShareBookmarkGroup;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipSectionType;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridge;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridgeRegistry;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlayLayout.GroupPanelSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Renders the bookmark panel: group panels, favorite recipe rows, floating drag previews and tooltips.
 */
public class BookmarkOverlayRenderer {
	private final BookmarkOverlay overlay;
	private final BookmarkList bookmarkList;
	private final int groupPanelWidth;
	private final int groupPanelHoverColor;
	private final int groupNoneColor;
	private final int groupChainColor;
	private final int favoriteRecipeRowColor;
	private final int groupPlaceholderColor;
	private boolean recipeChainTooltipShiftDown;
	private long recipeChainTooltipShiftVersion;
	private @Nullable RecipeChainHoverTooltip recipeChainHoverTooltip;

	public BookmarkOverlayRenderer(
		BookmarkOverlay overlay,
		BookmarkList bookmarkList,
		int groupPanelWidth,
		int groupPanelHoverColor,
		int groupNoneColor,
		int groupChainColor,
		int favoriteRecipeRowColor,
		int groupPlaceholderColor
	) {
		this.overlay = overlay;
		this.bookmarkList = bookmarkList;
		this.groupPanelWidth = groupPanelWidth;
		this.groupPanelHoverColor = groupPanelHoverColor;
		this.groupNoneColor = groupNoneColor;
		this.groupChainColor = groupChainColor;
		this.favoriteRecipeRowColor = favoriteRecipeRowColor;
		this.groupPlaceholderColor = groupPlaceholderColor;
	}

	void drawBookmarkGroupPanels(
		GuiGraphics guiGraphics,
		int mouseX,
		int mouseY,
		@Nullable GroupPanelDrag groupPanelDrag,
		@Nullable BookmarkSortDragState sortDragState
	) {
		BookmarkOverlayLayout.PanelSnapshot panelSnapshot = overlay.getGroupPanelSnapshotForRendering();
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlotsForPreview = panelSnapshot.panelSlots();
		List<GroupPanelSlot> sourcePanelSlots = panelSnapshot.groupPanelSlots();
		List<GroupPanelSlot> panelSlots = sourcePanelSlots;
		if (groupPanelDrag != null) {
			panelSlots = groupPanelDrag.getPreviewGroupPanelSlots(panelSlotsForPreview, panelSlots);
		}
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots = BookmarkOverlayLayout.toRowSlots(panelSlots);
		boolean hasDragPreview = panelSlots != sourcePanelSlots || sortDragState != null && sortDragState.isActive();
		BookmarkOverlayLayout.BoundaryConnections boundaryConnections = !hasDragPreview ?
			panelSnapshot.boundaryConnections() :
			BookmarkOverlayLayout.BoundaryConnections.NONE;
		for (int i = 0; i < panelSlots.size(); i++) {
			GroupPanelSlot slot = panelSlots.get(i);
			boolean connectedToPrevious = BookmarkPanelLayout.isConnectedToPreviousRow(rowSlots, i) ||
				i == 0 && boundaryConnections.connectedToPrevious();
			boolean connectedToNext = BookmarkPanelLayout.isConnectedToNextRow(rowSlots, i) ||
				i == panelSlots.size() - 1 && boundaryConnections.connectedToNext();
			if (sortDragState != null &&
				sortDragState.getGroupPanelRenderMode(slot.groupId()) == BookmarkSortDragState.GroupPanelRenderMode.DRAG_PLACEHOLDER) {
				ImmutableRect2i area = overlay.getGroupPanelArea(slot.area());
				guiGraphics.fill(
					area.getX(),
					area.getY(),
					area.getX() + area.getWidth(),
					area.getY() + area.getHeight(),
					BookmarkSortDragState.getDragOverlayColor()
				);
				drawGroupPanelPlaceholderLine(
					guiGraphics,
					slot.area(),
					connectedToPrevious,
					connectedToNext
				);
				continue;
			}
			if ((slot.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
				drawGroupPanelPlaceholderLine(guiGraphics, slot.area());
			} else {
				drawGroupPanelLine(
					guiGraphics,
					slot.area(),
					getGroupPanelColor(slot.groupId()),
					connectedToPrevious,
					connectedToNext
				);
			}
		}

		if (groupPanelDrag == null && overlay.isMouseOverVisibleGroupPanelArea(mouseX, mouseY)) {
			BookmarkPanelLayout.findRowUnderMouse(rowSlots, mouseX, mouseY, groupPanelWidth)
				.ifPresent(slot -> {
					ImmutableRect2i area = overlay.getGroupPanelArea(slot.area());
					guiGraphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), groupPanelHoverColor);
				});
		}
	}

	void drawFavoriteRecipeRowPanels(GuiGraphics guiGraphics, int mouseX, int mouseY, FavoriteRecipePanelState favoritePanelState) {
		if (favoritePanelState.displayMode() != FavoriteRecipePanelState.DisplayMode.RECIPE_ROWS) {
			return;
		}
		List<BookmarkPanelLayout.RowSlot<FocusedRecipe>> rowSlots = overlay.toFavoriteRecipeRowSlots();
		for (int i = 0; i < rowSlots.size(); i++) {
			BookmarkPanelLayout.RowSlot<FocusedRecipe> slot = rowSlots.get(i);
			drawGroupPanelLine(
				guiGraphics,
				slot.area(),
				favoriteRecipeRowColor,
				BookmarkPanelLayout.isConnectedToPreviousRow(rowSlots, i),
				BookmarkPanelLayout.isConnectedToNextRow(rowSlots, i)
			);
		}
		overlay.getFavoriteRecipeRowPanelSlotUnderMouse(mouseX, mouseY)
			.ifPresent(slot -> {
				ImmutableRect2i area = overlay.getGroupPanelArea(slot.area());
				guiGraphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), groupPanelHoverColor);
			});
	}

	int getGroupPanelColor(int groupId) {
		return bookmarkList.getRecipeChainDetails(groupId).isPresent() ? groupChainColor : groupNoneColor;
	}

	boolean drawFloatingGroupPanel(
		GuiGraphics guiGraphics,
		BookmarkSortDragState sortDragState,
		int mouseX,
		int mouseY
	) {
		List<BookmarkSortDragState.FloatingGroupPanelSlot> slots = sortDragState.getFloatingGroupPanelSlots(mouseX, mouseY);
		if (slots.isEmpty()) {
			return false;
		}
		int color = getGroupPanelColor(sortDragState.getSourceGroupId());
		for (BookmarkSortDragState.FloatingGroupPanelSlot slot : slots) {
			drawGroupPanelLine(
				guiGraphics,
				slot.slotArea(),
				color,
				slot.connectedToPrevious(),
				slot.connectedToNext()
			);
		}
		return true;
	}

	boolean drawFloatingFavoriteRecipeRowPanel(
		GuiGraphics guiGraphics,
		FavoriteRecipeSortDragState sortDragState,
		int mouseX,
		int mouseY
	) {
		List<BookmarkSortDragState.FloatingGroupPanelSlot> slots = sortDragState.getFloatingGroupPanelSlots(mouseX, mouseY);
		if (slots.isEmpty()) {
			return false;
		}
		for (BookmarkSortDragState.FloatingGroupPanelSlot slot : slots) {
			drawGroupPanelLine(
				guiGraphics,
				slot.slotArea(),
				favoriteRecipeRowColor,
				slot.connectedToPrevious(),
				slot.connectedToNext()
			);
		}
		return true;
	}

	void drawGroupPanelLine(
		GuiGraphics guiGraphics,
		ImmutableRect2i slotArea,
		int color,
		boolean connectedToPrevious,
		boolean connectedToNext
	) {
		int halfWidth = groupPanelWidth / 2;
		int heightPadding = slotArea.getHeight() / 4;
		int x = slotArea.getX() - halfWidth - 1;
		int top = connectedToPrevious ? slotArea.getY() : slotArea.getY() + heightPadding + 1;
		int topCap = slotArea.getY() + heightPadding;
		int bottom = connectedToNext ? slotArea.getY() + slotArea.getHeight() : slotArea.getY() + slotArea.getHeight() - heightPadding;

		guiGraphics.fill(x, top, x + 1, bottom, color);
		if (!connectedToPrevious) {
			guiGraphics.fill(x, topCap, x + halfWidth + 1, topCap + 1, color);
		}
		if (!connectedToNext) {
			guiGraphics.fill(x, bottom, x + halfWidth + 1, bottom + 1, color);
		}
	}

	private void drawGroupPanelPlaceholderLine(GuiGraphics guiGraphics, ImmutableRect2i slotArea) {
		drawGroupPanelPlaceholderLine(guiGraphics, slotArea, false, false);
	}

	private void drawGroupPanelPlaceholderLine(
		GuiGraphics guiGraphics,
		ImmutableRect2i slotArea,
		boolean connectedToPrevious,
		boolean connectedToNext
	) {
		ImmutableRect2i area = BookmarkPanelLayout.getGroupPanelPlaceholderLineArea(
			slotArea,
			connectedToPrevious,
			connectedToNext,
			groupPanelWidth
		);
		guiGraphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), groupPlaceholderColor);
	}

	void drawDefaultGroupControlIndicator(
		Minecraft minecraft,
		GuiGraphics guiGraphics,
		ImmutableRect2i controlArea,
		IPaged paged
	) {
		if (
			controlArea.isEmpty() ||
			!overlay.hasDefaultGroupBookmarks() ||
			!bookmarkList.isGroupCraftingMode(BookmarkGroupManager.DEFAULT_GROUP_ID)
		) {
			return;
		}
		String pageNumber = String.format("%d/%d", paged.getPageNumber() + 1, paged.getPageCount());
		Font font = minecraft.font;
		ImmutableRect2i textArea = MathUtil.centerTextArea(controlArea, font, pageNumber);
		int bracketWidth = font.width("[");
		int leftX = textArea.getX() - bracketWidth;
		int rightX = textArea.getX() + textArea.getWidth();
		if (leftX < controlArea.getX() || rightX + bracketWidth > controlArea.getX() + controlArea.getWidth()) {
			return;
		}
		guiGraphics.drawString(font, "[", leftX, textArea.getY(), groupChainColor);
		guiGraphics.drawString(font, "]", rightX, textArea.getY(), groupChainColor);
	}

	boolean drawGroupHotkeyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (overlay.getDefaultGroupControlArea().contains(mouseX, mouseY)) {
			if (!overlay.hasDefaultGroupBookmarks()) {
				return false;
			}
			int groupId = BookmarkGroupManager.DEFAULT_GROUP_ID;
			boolean craftingMode = bookmarkList.isGroupCraftingMode(groupId);
			JeiTooltip tooltip = new JeiTooltip();
			addRecipeChainTooltip(tooltip, groupId);
			BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(
				tooltip,
				overlay.getKeyBindings(),
				Screen.hasAltDown(),
				craftingMode,
				canPullDefaultGroupItems(),
				canShareBookmarkGroups()
			);
			tooltip.draw(guiGraphics, mouseX, mouseY);
			return true;
		}

		Optional<GroupPanelSlot> slot = overlay.getGroupPanelSlotUnderMouse(mouseX, mouseY);
		if (slot.isEmpty()) {
			return false;
		}

		int groupId = slot.get().groupId();
		boolean grouped = !(groupId == BookmarkGroupManager.DEFAULT_GROUP_ID);
		boolean craftingMode = bookmarkList.isGroupCraftingMode(groupId);
		JeiTooltip tooltip = new JeiTooltip();
		addRecipeChainTooltip(tooltip, groupId);
		BookmarkHotkeyTooltipUtil.addGroupHotkeys(
			tooltip,
			overlay.getKeyBindings(),
			Screen.hasAltDown(),
			grouped,
			craftingMode,
			canEncodeAe2Patterns(),
			overlay.canStartGroupDrop(groupId),
			canShareBookmarkGroups()
		);
		tooltip.draw(guiGraphics, mouseX, mouseY);
		return true;
	}

	boolean drawFavoriteRecipeRowHotkeyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (overlay.getFavoriteRecipeRowPanelSlotUnderMouse(mouseX, mouseY).isEmpty()) {
			return false;
		}
		JeiTooltip tooltip = new JeiTooltip();
		BookmarkHotkeyTooltipUtil.addFavoriteRecipeRowHotkeys(tooltip);
		tooltip.draw(guiGraphics, mouseX, mouseY);
		return true;
	}

	private static boolean canEncodeAe2Patterns() {
		if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return false;
		}
		Ae2RecipeChainPatternEncodingBridge bridge = Ae2RecipeChainPatternEncodingBridgeRegistry.getBridge();
		return bridge.isAvailable() && bridge.isPatternEncodingTerminal(containerScreen.getMenu());
	}

	private static boolean canPullDefaultGroupItems() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.screen instanceof AbstractContainerScreen<?>;
	}

	private static boolean canShareBookmarkGroups() {
		return Internal.getServerConnection().canSendPacket(PacketShareBookmarkGroup.TYPE);
	}

	private void addRecipeChainTooltip(JeiTooltip tooltip, int groupId) {
		if (!bookmarkList.isGroupCraftingMode(groupId)) {
			return;
		}
		tooltip.add(Component.translatable("jei.tooltip.bookmarks.group.recipe_chain").withStyle(ChatFormatting.AQUA));
		boolean shiftDown = Screen.hasShiftDown();
		boolean controlDown = Screen.hasControlDown();
		long bookmarkVersion = bookmarkList.getChangeVersion();
		long shiftVersion = updateRecipeChainTooltipShiftVersion(shiftDown);
		RecipeChainHoverTooltip hoverTooltip = recipeChainHoverTooltip;
		if (
			hoverTooltip == null ||
				!hoverTooltip.matches(groupId, bookmarkVersion, shiftVersion, shiftDown, controlDown)
		) {
			IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
			Optional<RecipeChainDetails> baseDetails = bookmarkList.getRecipeChainDetails(groupId);
			List<RecipeChainInput> groupInputs = baseDetails.isPresent() ?
				bookmarkList.getRecipeChainTooltipInputs(groupId) :
				bookmarkList.getRecipeChainInputs(groupId);
			RecipeChainTooltipModel model = RecipeChainTooltipModel.create(
				groupInputs,
				baseDetails,
				bookmarkList.getCollapsedRecipeIds(groupId),
				shiftDown ? getRecipeChainTooltipInventoryInputs(groupId) : List.of(),
				shiftDown,
				controlDown,
				ingredientManager
			);
			hoverTooltip = RecipeChainHoverTooltip.create(
				groupId,
				bookmarkVersion,
				shiftVersion,
				shiftDown,
				controlDown,
				model
			);
			recipeChainHoverTooltip = hoverTooltip;
		}
		for (RecipeChainTooltipSection section : hoverTooltip.sections()) {
			tooltip.add(Component.translatable(getRecipeChainTooltipLabel(section.type())).withStyle(getRecipeChainTooltipColor(section.type())));
			tooltip.add(section.component());
		}
	}

	private long updateRecipeChainTooltipShiftVersion(boolean shiftDown) {
		if (!shiftDown) {
			recipeChainTooltipShiftDown = false;
			return 0;
		}
		if (!recipeChainTooltipShiftDown) {
			recipeChainTooltipShiftDown = true;
			recipeChainTooltipShiftVersion++;
		}
		return recipeChainTooltipShiftVersion;
	}

	private static List<RecipeChainInput> getRecipeChainTooltipInventoryInputs(int groupId) {
		Minecraft minecraft = Minecraft.getInstance();
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		PlayerInventoryRecipeChainTooltipInventoryProvider inventoryProvider = new PlayerInventoryRecipeChainTooltipInventoryProvider(minecraft, ingredientManager);
		return inventoryProvider.getTooltipInventoryInputs(groupId);
	}

	private static String getRecipeChainTooltipLabel(RecipeChainTooltipSectionType type) {
		return switch (type) {
			case OUTPUT -> "jei.tooltip.bookmarks.group.recipe_chain.output";
			case INPUT -> "jei.tooltip.bookmarks.group.recipe_chain.input";
			case MISSING -> "jei.tooltip.bookmarks.group.recipe_chain.missing_items";
			case NEEDED -> "jei.tooltip.bookmarks.group.recipe_chain.needed";
			case AVAILABLE -> "jei.tooltip.bookmarks.group.recipe_chain.available";
			case REMAINDER -> "jei.tooltip.bookmarks.group.recipe_chain.remainder";
		};
	}

	private static ChatFormatting getRecipeChainTooltipColor(RecipeChainTooltipSectionType type) {
		return switch (type) {
			case MISSING -> ChatFormatting.RED;
			case NEEDED -> ChatFormatting.BLUE;
			case AVAILABLE -> ChatFormatting.GREEN;
			default -> ChatFormatting.GRAY;
		};
	}

	private record RecipeChainHoverTooltip(
		int groupId,
		long bookmarkVersion,
		long shiftVersion,
		boolean shiftDown,
		boolean controlDown,
		List<RecipeChainTooltipSection> sections
	) {
		public static RecipeChainHoverTooltip create(
			int groupId,
			long bookmarkVersion,
			long shiftVersion,
			boolean shiftDown,
			boolean controlDown,
			RecipeChainTooltipModel model
		) {
			List<RecipeChainTooltipSection> sections = model.sections().stream()
				.map(RecipeChainTooltipSection::create)
				.flatMap(Optional::stream)
				.toList();
			return new RecipeChainHoverTooltip(groupId, bookmarkVersion, shiftVersion, shiftDown, controlDown, sections);
		}

		public boolean matches(
			int groupId,
			long bookmarkVersion,
			long shiftVersion,
			boolean shiftDown,
			boolean controlDown
		) {
			return this.groupId == groupId &&
				this.bookmarkVersion == bookmarkVersion &&
				this.shiftVersion == shiftVersion &&
				this.shiftDown == shiftDown &&
				this.controlDown == controlDown;
		}
	}

	private record RecipeChainTooltipSection(
		RecipeChainTooltipSectionType type,
		RecipeChainPreviewTooltipComponent component
	) {
		public static Optional<RecipeChainTooltipSection> create(RecipeChainTooltipModel.Section section) {
			RecipeChainPreviewTooltipComponent component = new RecipeChainPreviewTooltipComponent(section.items());
			if (component.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(new RecipeChainTooltipSection(section.type(), component));
		}
	}
}
