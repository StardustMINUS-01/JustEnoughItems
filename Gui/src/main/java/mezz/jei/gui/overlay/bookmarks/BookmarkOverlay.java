package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.Internal;
import mezz.jei.common.config.HistoryDisplaySide;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.config.file.IConfigListener;
import mezz.jei.common.gui.BookmarkHotkeyTooltipUtil;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutablePoint2i;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkDisplaySlot;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyContext;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyMouseButton;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeySubject;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkMoveSelection;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipInventoryProvider;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipSectionType;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IDragHandler;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.FocusedRecipeCandidate;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.MouseUtil;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedDragHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.NullDragHandler;
import mezz.jei.gui.input.handlers.ProxyDragHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridge;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridgeRegistry;
import mezz.jei.gui.overlay.GuiPropertiesCache;
import mezz.jei.gui.overlay.IScreenPropertiesUpdater;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryButton;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BookmarkOverlay implements IRecipeFocusSource, IBookmarkOverlay {
	private static final int BORDER_MARGIN = 6;
	private static final int INNER_PADDING = 2;
	private static final int BUTTON_SIZE = 20;
	private static final int GROUP_PANEL_WIDTH = 7;
	private static final int GROUP_PANEL_DRAG_THRESHOLD_MS = 250;
	private static final int GROUP_PANEL_HOVER_COLOR = 0x66555555;
	private static final int GROUP_NONE_COLOR = 0xFF666666;
	private static final int GROUP_CHAIN_COLOR = 0xFF4FA3FF;
	private static final int FAVORITE_RECIPE_ROW_COLOR = 0x6645DA75;
	private static final int GROUP_PLACEHOLDER_COLOR = 0x66222222;
	private static final String GROUPING_PREVIEW_GROUP_ID = "__nei_grouping_preview__";

	public record LayoutAreas(
		ImmutableRect2i contentsLayoutArea,
		Optional<ImmutableRect2i> historyArea,
		OptionalInt contentsBottomLimit
	) {}

	// input
	private final BookmarkDragManager bookmarkDragManager;
	private @Nullable GroupPanelDrag groupPanelDrag;
	private @Nullable BookmarkSortDragState sortDragState;
	private @Nullable FavoriteRecipeSortDragState favoriteSortDragState;

	// areas
	private final GuiPropertiesCache<Screen> guiPropertiesCache;

	// display elements
	private final IngredientGridWithNavigation contents;
	private final IngredientGridWithNavigation favoriteContents;
	private final LookupHistoryOverlay lookupHistoryOverlay;
	private final GuiIconToggleButton bookmarkButton;
	private final GuiIconToggleButton favoriteButton;
	private final GuiIconToggleButton historyButton;

	// data
	private final BookmarkList bookmarkList;
	private final FavoriteRecipeStore favoriteRecipes;
	private final FavoriteRecipePanelState favoritePanelState;
	private final IClientToggleState toggleState;
	private final IClientConfig clientConfig;
	private final IInternalKeyMappings keyBindings;
	private @Nullable PanelSnapshotKey panelSnapshotKey;
	private @Nullable PanelSnapshot panelSnapshot;
	private boolean recipeChainTooltipShiftDown;
	private long recipeChainTooltipShiftVersion;
	private @Nullable RecipeChainHoverTooltip recipeChainHoverTooltip;

	// these need to be stored as strong references here because listeners are weakly stored elsewhere
	@SuppressWarnings("FieldCanBeLocal")
	private final IConfigListener<Boolean> lookupHistoryEnabledListener;
	@SuppressWarnings("FieldCanBeLocal")
	private final IConfigListener<HistoryDisplaySide> lookupHistoryViewSideListener;
	private boolean screenPropertiesDirty;

	public BookmarkOverlay(
		BookmarkList bookmarkList,
		IngredientGridWithNavigation contents,
		FavoriteRecipeStore favoriteRecipes,
		FavoriteRecipePanelState favoritePanelState,
		IngredientGridWithNavigation favoriteContents,
		LookupHistoryOverlay lookupHistoryOverlay,
		IClientToggleState toggleState,
		IClientConfig clientConfig,
		IIngredientGridConfig bookmarkListConfig,
		IScreenHelper screenHelper,
		IInternalKeyMappings keyBindings
	) {
		this.bookmarkList = bookmarkList;
		this.favoriteRecipes = favoriteRecipes;
		this.favoritePanelState = favoritePanelState;
		this.toggleState = toggleState;
		this.clientConfig = clientConfig;
		this.keyBindings = keyBindings;
		this.bookmarkButton = BookmarkButton.create(this, keyBindings);
		this.favoriteButton = FavoriteRecipePanelButton.create(this, favoriteRecipes);
		this.historyButton = LookupHistoryButton.create(clientConfig);
		this.contents = contents;
		this.favoriteContents = favoriteContents;
		this.lookupHistoryOverlay = lookupHistoryOverlay;
		this.guiPropertiesCache = new GuiPropertiesCache<>(
			screen -> screenHelper.getGuiProperties(screen)
				.orElse(null)
		);
		this.bookmarkDragManager = new BookmarkDragManager(this);
		bookmarkList.addSourceListChangedListener(() -> {
			clearPanelSnapshot();
			toggleState.setBookmarkEnabled(!bookmarkList.isEmpty());
			markScreenPropertiesDirty();
		});

		favoriteRecipes.addSourceListChangedListener(() -> {
			favoritePanelState.updateFavoritePanelAvailability(!favoriteRecipes.isEmpty());
			markScreenPropertiesDirty();
		});

		lookupHistoryOverlay.getLookupHistory().addSourceListChangedListener(() -> {
			markScreenPropertiesDirty();
		});
		lookupHistoryOverlay.getLookupHistory().addSourceListChangedListener(this::markScreenPropertiesDirty);

		this.lookupHistoryEnabledListener = v -> markScreenPropertiesDirty();
		this.lookupHistoryViewSideListener = v -> markScreenPropertiesDirty();
		clientConfig.lookupHistoryEnabled().addListener(this.lookupHistoryEnabledListener::onConfigValueChanged);
		clientConfig.maxLookupHistoryRows().addListener(v -> markScreenPropertiesDirty());
		clientConfig.lookupHistoryDisplaySide().addListener(this.lookupHistoryViewSideListener::onConfigValueChanged);
		addGridConfigListeners(bookmarkListConfig);
	}

	public boolean isListDisplayed() {
		if (!favoritePanelState.isBookmarkPanelVisible()) {
			return false;
		}
		updateScreenPropertiesIfDirty();
		return toggleState.isBookmarkOverlayEnabled() &&
			guiPropertiesCache.hasValidScreen() &&
			contents.hasRoom() &&
			!bookmarkList.isEmpty();
	}

	public boolean isFavoritePanelDisplayed() {
		return favoritePanelState.isFavoritePanelVisible() &&
			guiPropertiesCache.hasValidScreen() &&
			favoriteContents.hasRoom() &&
			!favoriteContents.isEmpty();
	}

	public boolean isFavoritePanelSelected() {
		return favoritePanelState.isFavoritePanelVisible();
	}

	public boolean hasBookmarkPanelRoom() {
		return contents.hasRoom();
	}

	public boolean hasFavoritePanelRoom() {
		return favoriteContents.hasRoom();
	}

	public boolean toggleBookmarkPanel(boolean simulate) {
		if (bookmarkList.isEmpty() || !hasBookmarkPanelRoom()) {
			return false;
		}
		if (!simulate) {
			if (isFavoritePanelSelected()) {
				favoritePanelState.showBookmarkPanel();
				toggleState.setBookmarkEnabled(true);
			} else if (isListDisplayed()) {
				toggleState.setBookmarkEnabled(false);
			} else {
				favoritePanelState.showBookmarkPanel();
				toggleState.setBookmarkEnabled(true);
			}
		}
		return true;
	}

	public boolean toggleFavoritePanel(boolean simulate) {
		if (favoriteContents.isEmpty() || !hasFavoritePanelRoom()) {
			return false;
		}
		if (!simulate) {
			boolean wasFavoritePanelDisplayed = isFavoritePanelDisplayed();
			favoritePanelState.handleFavoritePanelButtonClick(false);
			if (wasFavoritePanelDisplayed) {
				toggleState.setBookmarkEnabled(false);
			}
		}
		return true;
	}

	public boolean cycleFavoritePanelDisplayMode(boolean simulate) {
		if (!isFavoritePanelDisplayed()) {
			return false;
		}
		if (!simulate) {
			return favoritePanelState.handleFavoritePanelButtonClick(true);
		}
		return true;
	}

	public void showFavoritePanel() {
		if (hasFavoritePanelRoom()) {
			favoritePanelState.showFavoritePanel();
		}
	}

	public void showBookmarkPanel() {
		if (hasBookmarkPanelRoom()) {
			favoritePanelState.showBookmarkPanel();
			toggleState.setBookmarkEnabled(true);
		}
	}

	public IScreenPropertiesUpdater getScreenPropertiesUpdater() {
		return this.guiPropertiesCache.createUpdater(this::onGuiPropertiesChanged);
	}

	private void onGuiPropertiesChanged() {
		clearPanelSnapshot();
		IGuiProperties guiProperties = this.guiPropertiesCache.getGuiProperties();
		if (guiProperties == null) {
			this.contents.close();
			this.favoriteContents.close();
			this.lookupHistoryOverlay.close();
			return;
		}
		updateBounds(guiProperties);
	}

	private void updateBounds(IGuiProperties guiProperties) {
		ImmutableRect2i displayArea = getDisplayArea(guiProperties);
		Set<ImmutableRect2i> guiExclusionAreas = this.guiPropertiesCache.getGuiExclusionAreas();
		ImmutablePoint2i mouseExclusionArea = this.guiPropertiesCache.getMouseExclusionArea();

		LayoutAreas layoutAreas = calculateLayoutAreas(
			displayArea,
			clientConfig.lookupHistoryEnabled().getValue() && lookupHistoryOverlay.isDisplayedOnThisSide(),
			clientConfig.maxLookupHistoryRows().getValue()
		);
		layoutAreas.historyArea().ifPresent(historyArea -> {
			this.lookupHistoryOverlay.updateBounds(historyArea, guiExclusionAreas, mouseExclusionArea);
			this.lookupHistoryOverlay.updateLayout();
		});
		this.contents.updateBounds(layoutAreas.contentsLayoutArea(), layoutAreas.contentsBottomLimit(), guiExclusionAreas, mouseExclusionArea);
		this.contents.updateLayout(false);

		this.favoriteContents.updateBounds(layoutAreas.contentsLayoutArea(), layoutAreas.contentsBottomLimit(), guiExclusionAreas, mouseExclusionArea);
		this.favoriteContents.updateLayout(false);

		if (contents.hasRoom()) {
			ImmutableRect2i contentsArea = this.contents.getBackgroundArea();
			ImmutableRect2i bookmarkButtonArea = displayArea
				.insetBy(BORDER_MARGIN)
				.matchWidthAndX(contentsArea)
				.keepBottom(BUTTON_SIZE)
				.keepLeft(BUTTON_SIZE);
			this.bookmarkButton.updateBounds(bookmarkButtonArea);
			ImmutableRect2i historyButtonArea  = bookmarkButtonArea.moveRight(2 + BUTTON_SIZE);
			this.historyButton.updateBounds(historyButtonArea);
			this.favoriteButton.updateBounds(calculateFavoritePanelButtonArea(historyButtonArea));
		} else {
			ImmutableRect2i bookmarkButtonArea = displayArea
				.insetBy(BORDER_MARGIN)
				.keepBottom(BUTTON_SIZE)
				.keepLeft(BUTTON_SIZE);
			this.bookmarkButton.updateBounds(bookmarkButtonArea);
			ImmutableRect2i historyButtonArea  = bookmarkButtonArea.moveRight(2 + BUTTON_SIZE);
			this.historyButton.updateBounds(historyButtonArea);
			this.favoriteButton.updateBounds(calculateFavoritePanelButtonArea(historyButtonArea));
		}
	}

	public boolean hasRoom() {
		updateScreenPropertiesIfDirty();
		return contents.hasRoom();
	}

	private void markScreenPropertiesDirty() {
		this.screenPropertiesDirty = true;
	}

	private void addGridConfigListeners(IIngredientGridConfig gridConfig) {
		gridConfig.maxColumns().addListener(v -> markScreenPropertiesDirty());
		gridConfig.maxRows().addListener(v -> markScreenPropertiesDirty());
		gridConfig.drawBackground().addListener(v -> markScreenPropertiesDirty());
		gridConfig.horizontalAlignment().addListener(v -> markScreenPropertiesDirty());
		gridConfig.verticalAlignment().addListener(v -> markScreenPropertiesDirty());
		gridConfig.navigationVisibility().addListener(v -> markScreenPropertiesDirty());
	}

	private void updateScreenPropertiesIfDirty() {
		if (this.screenPropertiesDirty) {
			this.screenPropertiesDirty = false;
			Minecraft minecraft = Minecraft.getInstance();
		this.getScreenPropertiesUpdater()
			.updateScreen(minecraft.screen)
			.forceUpdate();
		}
	}

	public static ImmutableRect2i calculateFavoritePanelButtonArea(ImmutableRect2i historyButtonArea) {
		return historyButtonArea.moveRight(BUTTON_SIZE + INNER_PADDING);
	}

	public static LayoutAreas calculateLayoutAreas(ImmutableRect2i displayArea, boolean lookupHistoryOnSide, int historyRows) {
		ImmutableRect2i contentsLayoutArea = displayArea
			.cropBottom(BUTTON_SIZE + INNER_PADDING)
			.cropLeft(GROUP_PANEL_WIDTH);
		Optional<ImmutableRect2i> historyArea = Optional.empty();
		OptionalInt contentsBottomLimit = OptionalInt.empty();
		if (lookupHistoryOnSide) {
			ImmutableRect2i calculatedHistoryArea = displayArea
				.insetBy(BORDER_MARGIN)
				.moveUp(BUTTON_SIZE + INNER_PADDING)
				.keepBottom(historyRows * LookupHistoryOverlay.SLOT_HEIGHT);
			historyArea = Optional.of(calculatedHistoryArea);
			contentsBottomLimit = OptionalInt.of(calculatedHistoryArea.getY() - INNER_PADDING);
		}
		return new LayoutAreas(contentsLayoutArea, historyArea, contentsBottomLimit);
	}

	private static ImmutableRect2i getDisplayArea(IGuiProperties guiProperties) {
		int width = guiProperties.getGuiLeft();
		if (width <= 0) {
			width = 0;
		}
		int screenHeight = guiProperties.getScreenHeight();
		return new ImmutableRect2i(0, 0, width, screenHeight);
	}

	public void drawScreen(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		drawBackground(guiGraphics);
		drawForeground(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
	}

	public void drawBackground(GuiGraphics guiGraphics) {
		if (isListDisplayed()) {
			this.contents.drawBackground(guiGraphics);
		}
		if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.drawBackground(guiGraphics);
		}
	}

	public void drawForeground(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		if (isListDisplayed()) {
			updateSortDrag(mouseX, mouseY);
			this.bookmarkDragManager.updateDrag(mouseX, mouseY);
			this.contents.drawForeground(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
			if (sortDragState != null) {
				sortDragState.drawTargetSlotOverlays(guiGraphics);
				sortDragState.drawSourceSlotOverlays(guiGraphics, getPanelSlots());
			}
			drawBookmarkGroupPanels(guiGraphics, mouseX, mouseY);
		}
		if (isFavoritePanelDisplayed()) {
			updateFavoriteSortDrag(mouseX, mouseY);
			this.favoriteContents.drawForeground(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
			if (favoriteSortDragState != null) {
				favoriteSortDragState.drawTargetSlotOverlays(guiGraphics);
				favoriteSortDragState.drawSourceSlotOverlays(guiGraphics);
			}
			drawFavoriteRecipeRowPanels(guiGraphics, mouseX, mouseY);
		}
		if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.draw(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
		}
		if (this.guiPropertiesCache.hasValidScreen()) {
			this.bookmarkButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			this.favoriteButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			this.historyButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
		}
	}

	public void drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (sortDragState != null) {
			boolean drewSortDrag = sortDragState.drawDraggedItems(guiGraphics, mouseX, mouseY);
			drewSortDrag = drawFloatingGroupPanel(guiGraphics, sortDragState, mouseX, mouseY) || drewSortDrag;
			if (drewSortDrag || sortDragState.isActive()) {
				return;
			}
		}
		if (favoriteSortDragState != null) {
			boolean drewSortDrag = favoriteSortDragState.drawDraggedItems(guiGraphics, mouseX, mouseY);
			drewSortDrag = drawFloatingFavoriteRecipeRowPanel(guiGraphics, favoriteSortDragState, mouseX, mouseY) || drewSortDrag;
			if (drewSortDrag || favoriteSortDragState.isActive()) {
				return;
			}
		}
		updateScreenPropertiesIfDirty();
		if (!this.bookmarkDragManager.drawDraggedItem(guiGraphics, mouseX, mouseY)) {
			if (isListDisplayed()) {
				if (drawGroupHotkeyTooltip(guiGraphics, mouseX, mouseY)) {
					return;
				}
				this.contents.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
			}
			if (isFavoritePanelDisplayed()) {
				if (drawFavoriteRecipeRowHotkeyTooltip(guiGraphics, mouseX, mouseY)) {
					return;
				}
				this.favoriteContents.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
			}
			if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
				this.lookupHistoryOverlay.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
			}
		}
		if (this.guiPropertiesCache.hasValidScreen()) {
			bookmarkButton.drawTooltips(guiGraphics, mouseX, mouseY);
			favoriteButton.drawTooltips(guiGraphics, mouseX, mouseY);
			historyButton.drawTooltips(guiGraphics, mouseX, mouseY);
		}
	}

	private boolean drawGroupHotkeyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (getDefaultGroupControlArea().contains(mouseX, mouseY)) {
			JeiTooltip tooltip = new JeiTooltip();
			BookmarkHotkeyTooltipUtil.addDefaultGroupControlHotkeys(tooltip, keyBindings, Screen.hasAltDown());
			tooltip.draw(guiGraphics, mouseX, mouseY);
			return true;
		}

		Optional<GroupPanelSlot> slot = getGroupPanelSlotUnderMouse(mouseX, mouseY);
		if (slot.isEmpty()) {
			return false;
		}

		String groupId = slot.get().groupId();
		boolean grouped = !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId);
		boolean craftingMode = bookmarkList.isGroupCraftingMode(groupId);
		JeiTooltip tooltip = new JeiTooltip();
		addRecipeChainTooltip(tooltip, groupId);
		BookmarkHotkeyTooltipUtil.addGroupHotkeys(tooltip, keyBindings, Screen.hasAltDown(), grouped, craftingMode, canEncodeAe2Patterns());
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

	private boolean drawFavoriteRecipeRowHotkeyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (getFavoriteRecipeRowPanelSlotUnderMouse(mouseX, mouseY).isEmpty()) {
			return false;
		}
		JeiTooltip tooltip = new JeiTooltip();
		BookmarkHotkeyTooltipUtil.addFavoriteRecipeRowHotkeys(tooltip, Screen.hasAltDown());
		tooltip.draw(guiGraphics, mouseX, mouseY);
		return true;
	}

	private void addRecipeChainTooltip(JeiTooltip tooltip, String groupId) {
		if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
			return;
		}
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
			RecipeChainTooltipModel model;
			Map<BookmarkIngredientKey, ITypedIngredient<?>> resolvedIngredients = bookmarkList.getRecipeChainTooltipIngredients(groupId);
			Optional<RecipeChainDetails> baseDetails = bookmarkList.getRecipeChainDetails(groupId);
			if (baseDetails.isPresent()) {
				model = RecipeChainTooltipModel.create(
					bookmarkList.getRecipeChainTooltipInputs(groupId),
					baseDetails.get(),
					bookmarkList.getCollapsedRecipeIds(groupId),
					shiftDown ? getRecipeChainTooltipInventoryInputs(groupId) : List.of(),
					shiftDown,
					controlDown
				);
			} else {
				model = RecipeChainTooltipModel.create(
					bookmarkList.getRecipeChainInputs(groupId),
					bookmarkList.getCollapsedRecipeIds(groupId),
					shiftDown ? getRecipeChainTooltipInventoryInputs(groupId) : List.of(),
					shiftDown,
					controlDown
				);
			}
			hoverTooltip = RecipeChainHoverTooltip.create(
				groupId,
				bookmarkVersion,
				shiftVersion,
				shiftDown,
				controlDown,
				model,
				resolvedIngredients
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

	private static List<RecipeChainInput> getRecipeChainTooltipInventoryInputs(String groupId) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof RecipesGui) {
			return List.of();
		}
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		RecipeChainTooltipInventoryProvider inventoryProvider = new PlayerInventoryRecipeChainTooltipInventoryProvider(minecraft, ingredientManager);
		return inventoryProvider.getInventoryInputs(groupId, -1);
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

	public void tick() {
		if (isListDisplayed()) {
			this.contents.tick();
		}
		if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.tick();
		}
	}

	@Override
	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		updateScreenPropertiesIfDirty();
		if (isListDisplayed()) {
			return Stream.concat(this.contents.getIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getIngredientUnderMouse(mouseX, mouseY));
		}
		if (isFavoritePanelDisplayed()) {
			return Stream.concat(this.favoriteContents.getIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getIngredientUnderMouse(mouseX, mouseY));
		}
		if (this.lookupHistoryOverlay.isListDisplayed()) {
			return this.lookupHistoryOverlay.getIngredientUnderMouse(mouseX, mouseY);
		}
		return Stream.empty();
	}

	@Override
	public Stream<IDraggableIngredientInternal<?>> getDraggableIngredientUnderMouse(double mouseX, double mouseY) {
		updateScreenPropertiesIfDirty();
		if (isListDisplayed()) {
			return Stream.concat(this.contents.getDraggableIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getDraggableIngredientUnderMouse(mouseX, mouseY));
		}
		if (isFavoritePanelDisplayed()) {
			return Stream.concat(this.favoriteContents.getDraggableIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getDraggableIngredientUnderMouse(mouseX, mouseY));
		}
		if (this.lookupHistoryOverlay.isListDisplayed()) {
			return this.lookupHistoryOverlay.getDraggableIngredientUnderMouse(mouseX, mouseY);
		}
		return Stream.empty();
	}

	@Override
	public Optional<ITypedIngredient<?>> getIngredientUnderMouse() {
		double mouseX = MouseUtil.getX();
		double mouseY = MouseUtil.getY();
		return getIngredientUnderMouse(mouseX, mouseY)
			.<ITypedIngredient<?>>map(IClickableIngredientInternal::getTypedIngredient)
			.findFirst();
	}

	@Override
	public boolean addBookmark(ITypedIngredient<?> ingredient) {
		boolean added = bookmarkList.addIngredientBookmark(ingredient, true);
		if (added) {
			showBookmarkPanel();
		}
		return added;
	}

	@Nullable
	@Override
	public <T> T getIngredientUnderMouse(IIngredientType<T> ingredientType) {
		double mouseX = MouseUtil.getX();
		double mouseY = MouseUtil.getY();
		return getIngredientUnderMouse(mouseX, mouseY)
			.map(IClickableIngredientInternal::getTypedIngredient)
			.map(i -> i.getIngredient(ingredientType))
			.flatMap(Optional::stream)
			.findFirst()
			.orElse(null);
	}

	public IUserInputHandler createInputHandler() {
		final IUserInputHandler bookmarkButtonInputHandler = this.bookmarkButton.createInputHandler();
		final IUserInputHandler favoriteButtonInputHandler = this.favoriteButton.createInputHandler();
		final IUserInputHandler historyButtonInputHandler = this.historyButton.createInputHandler();
		final IUserInputHandler recipeCollapseInputHandler = new RecipeCollapseInputHandler();
		final IUserInputHandler favoriteRecipeRowInputHandler = new FavoriteRecipeRowInputHandler();
		final IUserInputHandler groupInputHandler = new GroupInputHandler();

		final IUserInputHandler buttonInputHandler = new CombinedInputHandler(
			"BookmarkOverlayButton",
			bookmarkButtonInputHandler,
			favoriteButtonInputHandler,
			historyButtonInputHandler
		);

		final IUserInputHandler displayedInputHandler = new CombinedInputHandler(
			"BookmarkOverlay",
			recipeCollapseInputHandler,
			groupInputHandler,
			this.contents.createInputHandler(),
			buttonInputHandler
		);
		final IUserInputHandler favoriteDisplayedInputHandler = new CombinedInputHandler(
			"FavoriteRecipeOverlay",
			favoriteRecipeRowInputHandler,
			this.favoriteContents.createInputHandler(),
			buttonInputHandler
		);

		return new ProxyInputHandler(() -> {
			if (isFavoritePanelDisplayed()) {
				return favoriteDisplayedInputHandler;
			}
			if (isListDisplayed()) {
				return displayedInputHandler;
			}
			return buttonInputHandler;
		});
	}

	public IDragHandler createDragHandler() {
		final IDragHandler historyDragHandler = this.lookupHistoryOverlay.createDragHandler();
		final IDragHandler favoriteDragHandler = new CombinedDragHandler(
			createFavoriteSortDragHandler(),
			this.favoriteContents.createDragHandler()
		);
		final IDragHandler sortDragHandler = createSortDragHandler();
		final IDragHandler combinedDragHandlers = new CombinedDragHandler(
			sortDragHandler,
			this.contents.createDragHandler(),
			historyDragHandler,
			this.bookmarkDragManager.createDragHandler()
		);

		return new ProxyDragHandler(() -> {
			if (isFavoritePanelDisplayed()) {
				return favoriteDragHandler;
			}
			if (isListDisplayed()) {
				return combinedDragHandlers;
			}
			if (lookupHistoryOverlay.isListDisplayed()) {
				return historyDragHandler;
			}
			return NullDragHandler.INSTANCE;
		});
	}

	public void drawOnForeground(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		updateScreenPropertiesIfDirty();
		if (isListDisplayed()) {
			this.contents.drawOnForeground(guiGraphics, mouseX, mouseY);
		}
		if (isFavoritePanelDisplayed()) {
			this.favoriteContents.drawOnForeground(guiGraphics, mouseX, mouseY);
		}
		this.lookupHistoryOverlay.drawOnForeground(guiGraphics, mouseX, mouseY);
	}

	private void drawBookmarkGroupPanels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlotsForPreview = getPanelSlots();
		List<GroupPanelSlot> panelSlots = getGroupPanelSlots();
		if (groupPanelDrag != null) {
			panelSlots = groupPanelDrag.getPreviewGroupPanelSlots(panelSlotsForPreview, panelSlots);
		}
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots = toRowSlots(panelSlots);
		for (int i = 0; i < panelSlots.size(); i++) {
			GroupPanelSlot slot = panelSlots.get(i);
			if (sortDragState != null &&
				sortDragState.getGroupPanelRenderMode(slot.groupId()) == BookmarkSortDragState.GroupPanelRenderMode.DRAG_PLACEHOLDER) {
				ImmutableRect2i area = getGroupPanelArea(slot.area());
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
					BookmarkPanelLayout.isConnectedToPreviousRow(rowSlots, i),
					BookmarkPanelLayout.isConnectedToNextRow(rowSlots, i)
				);
				continue;
			}
			if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(slot.groupId())) {
				drawGroupPanelPlaceholderLine(guiGraphics, slot.area());
			} else {
				drawGroupPanelLine(
					guiGraphics,
					slot.area(),
					getGroupPanelColor(slot.groupId()),
					BookmarkPanelLayout.isConnectedToPreviousRow(rowSlots, i),
					BookmarkPanelLayout.isConnectedToNextRow(rowSlots, i)
				);
			}
		}

		if (groupPanelDrag == null) {
			getGroupPanelSlotUnderMouse(mouseX, mouseY)
				.ifPresent(slot -> {
					ImmutableRect2i area = getGroupPanelArea(slot.area());
					guiGraphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), GROUP_PANEL_HOVER_COLOR);
				});
		}
	}

	private void drawFavoriteRecipeRowPanels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (favoritePanelState.displayMode() != FavoriteRecipePanelState.DisplayMode.RECIPE_ROWS) {
			return;
		}
		List<BookmarkPanelLayout.RowSlot<FocusedRecipe>> rowSlots = toFavoriteRecipeRowSlots();
		for (int i = 0; i < rowSlots.size(); i++) {
			BookmarkPanelLayout.RowSlot<FocusedRecipe> slot = rowSlots.get(i);
			drawGroupPanelLine(
				guiGraphics,
				slot.area(),
				FAVORITE_RECIPE_ROW_COLOR,
				BookmarkPanelLayout.isConnectedToPreviousRow(rowSlots, i),
				BookmarkPanelLayout.isConnectedToNextRow(rowSlots, i)
			);
		}
		getFavoriteRecipeRowPanelSlotUnderMouse(mouseX, mouseY)
			.ifPresent(slot -> {
				ImmutableRect2i area = getGroupPanelArea(slot.area());
				guiGraphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), GROUP_PANEL_HOVER_COLOR);
			});
	}

	private int getGroupPanelColor(String groupId) {
		return bookmarkList.getRecipeChainDetails(groupId).isPresent() ? GROUP_CHAIN_COLOR : GROUP_NONE_COLOR;
	}

	private boolean drawFloatingGroupPanel(
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

	private boolean drawFloatingFavoriteRecipeRowPanel(
		GuiGraphics guiGraphics,
		FavoriteRecipeSortDragState sortDragState,
		int mouseX,
		int mouseY
	) {
		List<FavoriteRecipeSortDragState.FloatingGroupPanelSlot> slots = sortDragState.getFloatingGroupPanelSlots(mouseX, mouseY);
		if (slots.isEmpty()) {
			return false;
		}
		for (FavoriteRecipeSortDragState.FloatingGroupPanelSlot slot : slots) {
			drawGroupPanelLine(
				guiGraphics,
				slot.slotArea(),
				FAVORITE_RECIPE_ROW_COLOR,
				slot.connectedToPrevious(),
				slot.connectedToNext()
			);
		}
		return true;
	}

	private static void drawGroupPanelLine(
		GuiGraphics guiGraphics,
		ImmutableRect2i slotArea,
		int color,
		boolean connectedToPrevious,
		boolean connectedToNext
	) {
		int halfWidth = GROUP_PANEL_WIDTH / 2;
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

	private static void drawGroupPanelPlaceholderLine(GuiGraphics guiGraphics, ImmutableRect2i slotArea) {
		drawGroupPanelPlaceholderLine(guiGraphics, slotArea, false, false);
	}

	private static void drawGroupPanelPlaceholderLine(
		GuiGraphics guiGraphics,
		ImmutableRect2i slotArea,
		boolean connectedToPrevious,
		boolean connectedToNext
	) {
		ImmutableRect2i area = BookmarkPanelLayout.getGroupPanelPlaceholderLineArea(
			slotArea,
			connectedToPrevious,
			connectedToNext,
			GROUP_PANEL_WIDTH
		);
		guiGraphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), GROUP_PLACEHOLDER_COLOR);
	}

	private List<GroupPanelSlot> getGroupPanelSlots() {
		return getPanelSnapshot().groupPanelSlots();
	}

	BookmarkList getBookmarkList() {
		return bookmarkList;
	}

	List<BookmarkPanelLayout.PanelSlot<IBookmark>> getPanelSlots() {
		return getPanelSnapshot().panelSlots();
	}

	private List<BookmarkPanelLayout.PanelSlot<IBookmark>> getProjectedPanelSlots() {
		return getPanelSnapshot().projectedPanelSlots();
	}

	private List<BookmarkPanelLayout.RowSlot<IBookmark>> getGroupPanelRowSlots() {
		return getPanelSnapshot().rowSlots();
	}

	private PanelSnapshot getPanelSnapshot() {
		List<IngredientListSlot> visibleSlots = this.contents.getSlots().toList();
		List<ImmutableRect2i> pageAreas = visibleSlots.stream()
			.map(IngredientListSlot::getArea)
			.toList();
		List<VisibleSlotKey> visibleContentKeys = visibleSlots.stream()
			.map(BookmarkOverlay::createVisibleSlotKey)
			.toList();
		IPaged pageDelegate = this.contents.getPageDelegate();
		PanelSnapshotKey key = new PanelSnapshotKey(
			bookmarkList.getChangeVersion(),
			this.contents.getUsableColumnCount(),
			pageDelegate.getPageNumber(),
			this.contents.size(),
			pageAreas,
			visibleContentKeys
		);
		if (!key.equals(panelSnapshotKey) || panelSnapshot == null) {
			panelSnapshotKey = key;
			panelSnapshot = createPanelSnapshot(visibleSlots, pageAreas, key);
		}
		return panelSnapshot;
	}

	private PanelSnapshot createPanelSnapshot(
		List<IngredientListSlot> visibleSlots,
		List<ImmutableRect2i> pageAreas,
		PanelSnapshotKey key
	) {
		List<BookmarkDisplaySlot<IBookmark>> displaySlots = this.bookmarkList.getDisplaySlots(this.contents.getUsableColumnCount());
		int firstDisplaySlotIndex = key.pageNumber() * key.pageSize();
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> projectedPanelSlots = BookmarkPanelLayout.createPagePanelSlots(
			displaySlots,
			pageAreas,
			firstDisplaySlotIndex
		);
		int gridLeftX = pageAreas.stream()
			.mapToInt(ImmutableRect2i::getX)
			.min()
			.orElseGet(() -> projectedPanelSlots.stream()
				.map(BookmarkPanelLayout.PanelSlot::area)
				.mapToInt(ImmutableRect2i::getX)
				.min()
				.orElse(0));
		List<GroupPanelSlot> groupPanelSlots = BookmarkPanelLayout.createRowSlots(projectedPanelSlots, gridLeftX).stream()
			.map(slot -> new GroupPanelSlot(slot.item(), slot.groupId(), slot.area()))
			.toList();
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots = createPanelSlots(visibleSlots, displaySlots, firstDisplaySlotIndex);
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots = toRowSlots(groupPanelSlots);
		return new PanelSnapshot(panelSlots, projectedPanelSlots, groupPanelSlots, rowSlots);
	}

	private void clearPanelSnapshot() {
		panelSnapshotKey = null;
		panelSnapshot = null;
	}

	private List<BookmarkPanelLayout.PanelSlot<IBookmark>> createPanelSlots(
		List<IngredientListSlot> visibleSlots,
		List<BookmarkDisplaySlot<IBookmark>> displaySlots,
		int firstDisplaySlotIndex
	) {
		Map<Integer, BookmarkDisplaySlot<IBookmark>> displaySlotByIndex = new HashMap<>();
		Map<IBookmark, BookmarkDisplaySlot<IBookmark>> displaySlotByBookmark = new HashMap<>();
		for (BookmarkDisplaySlot<IBookmark> displaySlot : displaySlots) {
			displaySlotByIndex.put(displaySlot.slotIndex(), displaySlot);
			displaySlotByBookmark.putIfAbsent(displaySlot.entry().item(), displaySlot);
		}
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots = new ArrayList<>();
		for (int i = 0; i < visibleSlots.size(); i++) {
			IngredientListSlot visibleSlot = visibleSlots.get(i);
			Optional<IBookmark> bookmark = visibleSlot.getOptionalElement()
				.flatMap(IElement::getBookmark);
			if (bookmark.isEmpty()) {
				continue;
			}
			Optional<BookmarkDisplaySlot<IBookmark>> displaySlot = getDisplaySlot(
				displaySlotByIndex,
				displaySlotByBookmark,
				firstDisplaySlotIndex + i,
				bookmark.get()
			);
			String groupId = displaySlot
				.map(slot -> slot.entry().metadata().groupId())
				.orElseGet(() -> this.bookmarkList.getBookmarkGroupId(bookmark.get()));
			boolean shadow = displaySlot
				.map(BookmarkDisplaySlot::shadow)
				.orElse(false);
			Object recipeKey = displaySlot
				.map(slot -> getRecipeKey(slot.entry()))
				.orElseGet(() -> getRecipeKey(this.bookmarkList.getBookmarkMetadata(bookmark.get())));
			panelSlots.add(new BookmarkPanelLayout.PanelSlot<>(bookmark.get(), groupId, visibleSlot.getArea(), shadow, recipeKey));
		}
		return panelSlots;
	}

	private static @Nullable ResourceLocation getRecipeKey(BookmarkDisplayEntry<IBookmark> entry) {
		if (!entry.metadata().type().isRecipeAssociated()) {
			return null;
		}
		return entry.displayRecipeUid()
			.orElse(entry.metadata().recipeUid());
	}

	private static @Nullable ResourceLocation getRecipeKey(BookmarkItemMetadata metadata) {
		if (!metadata.type().isRecipeAssociated()) {
			return null;
		}
		return metadata.recipeUid();
	}

	private static Optional<BookmarkDisplaySlot<IBookmark>> getDisplaySlot(
		Map<Integer, BookmarkDisplaySlot<IBookmark>> displaySlotByIndex,
		Map<IBookmark, BookmarkDisplaySlot<IBookmark>> displaySlotByBookmark,
		int displaySlotIndex,
		IBookmark bookmark
	) {
		return Optional.ofNullable(displaySlotByIndex.get(displaySlotIndex))
			.filter(displaySlot -> displaySlot.entry().item().equals(bookmark))
			.or(() -> Optional.ofNullable(displaySlotByBookmark.get(bookmark)));
	}

	private static VisibleSlotKey createVisibleSlotKey(IngredientListSlot slot) {
		int bookmarkIdentity = slot.getOptionalElement()
			.flatMap(IElement::getBookmark)
			.map(System::identityHashCode)
			.orElse(0);
		return new VisibleSlotKey(slot.getArea(), bookmarkIdentity);
	}

	private record GroupPanelSlot(IBookmark bookmark, String groupId, ImmutableRect2i area) {
	}

	private record VisibleSlotKey(ImmutableRect2i area, int bookmarkIdentity) {
	}

	private record PanelSnapshotKey(
		long sourceVersion,
		int columns,
		int pageNumber,
		int pageSize,
		List<ImmutableRect2i> slotAreas,
		List<VisibleSlotKey> visibleContentKeys
	) {
	}

	private record PanelSnapshot(
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> projectedPanelSlots,
		List<GroupPanelSlot> groupPanelSlots,
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots
	) {
	}

	private record RecipeChainHoverTooltip(
		String groupId,
		long bookmarkVersion,
		long shiftVersion,
		boolean shiftDown,
		boolean controlDown,
		List<RecipeChainTooltipSection> sections
	) {
		public static RecipeChainHoverTooltip create(
			String groupId,
			long bookmarkVersion,
			long shiftVersion,
			boolean shiftDown,
			boolean controlDown,
			RecipeChainTooltipModel model,
			Map<BookmarkIngredientKey, ITypedIngredient<?>> resolvedIngredients
		) {
			List<RecipeChainTooltipSection> sections = model.sections().stream()
				.map(section -> RecipeChainTooltipSection.create(section, resolvedIngredients))
				.flatMap(Optional::stream)
				.toList();
			return new RecipeChainHoverTooltip(groupId, bookmarkVersion, shiftVersion, shiftDown, controlDown, sections);
		}

		public boolean matches(
			String groupId,
			long bookmarkVersion,
			long shiftVersion,
			boolean shiftDown,
			boolean controlDown
		) {
			return this.groupId.equals(groupId) &&
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
		public static Optional<RecipeChainTooltipSection> create(
			RecipeChainTooltipModel.Section section,
			Map<BookmarkIngredientKey, ITypedIngredient<?>> resolvedIngredients
		) {
			RecipeChainPreviewTooltipComponent component = new RecipeChainPreviewTooltipComponent(section.items(), resolvedIngredients);
			if (component.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(new RecipeChainTooltipSection(section.type(), component));
		}
	}

	private record FavoriteRecipeRowPanelSlot(FocusedRecipe recipe, ImmutableRect2i area) {
	}

	private record FavoriteRecipeElementPanelSlot(FavoriteRecipeElement<?> element, ImmutableRect2i area) {
	}

	private class FavoriteRecipeRowInputHandler implements IUserInputHandler {
		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			if (input.getKey().getType() != InputConstants.Type.MOUSE ||
				input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT ||
				InputModifiers.hasShift(input) ||
				InputModifiers.hasControl(input) ||
				InputModifiers.hasAlt(input)) {
				return Optional.empty();
			}
			Optional<FavoriteRecipeRowPanelSlot> slot = getFavoriteRecipeRowPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
			if (slot.isEmpty()) {
				return Optional.empty();
			}
			if (input.getInputType() == InputType.EXECUTE && favoritePanelState.toggleRecipeRowCollapsed(slot.get().recipe())) {
				playClickSound();
			}
			return Optional.of(this);
		}
	}

	private class RecipeCollapseInputHandler implements IUserInputHandler {
		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			if (input.getKey().getType() != InputConstants.Type.MOUSE ||
				input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT ||
				!InputModifiers.hasAlt(input) ||
				InputModifiers.hasShift(input)) {
				return Optional.empty();
			}
			Optional<BookmarkPanelLayout.PanelSlot<IBookmark>> target = getPanelSlots().stream()
				.filter(slot -> slot.area().contains(input.getMouseX(), input.getMouseY()))
				.filter(slot -> slot.recipeKey() instanceof ResourceLocation)
				.filter(slot -> bookmarkList.isGroupCraftingMode(slot.groupId()))
				.findFirst();
			if (target.isEmpty()) {
				return Optional.empty();
			}
			if (input.getInputType() == InputType.EXECUTE) {
				ResourceLocation recipeUid = (ResourceLocation) target.get().recipeKey();
				if (bookmarkList.toggleGroupCollapsedRecipeId(target.get().groupId(), recipeUid)) {
					playClickSound();
				}
			}
			return Optional.of(this);
		}
	}

	private class GroupInputHandler implements IUserInputHandler {
		private static final long SCROLL_STEP = 1;
		private static final long SCROLL_LARGE_STEP = 64;

		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			if (input.getKey().getType() != InputConstants.Type.MOUSE) {
				return Optional.empty();
			}

			int mouseButton = input.getKey().getValue();
			if (mouseButton != InputConstants.MOUSE_BUTTON_LEFT && mouseButton != InputConstants.MOUSE_BUTTON_RIGHT) {
				return Optional.empty();
			}

			BookmarkHotkeyMouseButton hotkeyMouseButton = mouseButton == InputConstants.MOUSE_BUTTON_LEFT ?
				BookmarkHotkeyMouseButton.LEFT :
				BookmarkHotkeyMouseButton.RIGHT;
			if (input.getInputType() == InputType.EXECUTE && groupPanelDrag != null) {
				boolean handled = groupPanelDrag.complete(input);
				groupPanelDrag = null;
				return handled ? Optional.of(this) : Optional.empty();
			}

			if (getDefaultGroupControlArea().contains(input.getMouseX(), input.getMouseY())) {
				BookmarkHotkeyContext context = createDefaultGroupControlHotkeyContext();
				Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveGroupMouseAction(
					context,
					hotkeyMouseButton,
					InputModifiers.hasShift(input),
					InputModifiers.hasAlt(input)
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

			Optional<GroupPanelSlot> slot = getGroupPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
			if (slot.isEmpty()) {
				return Optional.empty();
			}

			BookmarkHotkeyContext context = createGroupPanelHotkeyContext(slot.get());
			Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveGroupMouseAction(
				context,
				hotkeyMouseButton,
				InputModifiers.hasShift(input),
				InputModifiers.hasAlt(input)
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
				action.get() == BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING) {
				if (input.getInputType() == InputType.SIMULATE) {
					groupPanelDrag = new GroupPanelDrag(
						slot.get(),
						action.get() == BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING,
						action.get()
					);
					return Optional.of(this);
				}
				if (input.getInputType() == InputType.EXECUTE && applyGroupClickAction(slot.get().groupId(), action.get())) {
					playClickSound();
					return Optional.of(this);
				}
				return Optional.empty();
			}

			if (input.getInputType() == InputType.SIMULATE && shouldStartGroupPanelDrag(action.get())) {
				groupPanelDrag = new GroupPanelDrag(
					slot.get(),
					action.get() == BookmarkHotkeyAction.GROUP_EXCLUDE_DRAG,
					null
				);
				return Optional.of(this);
			}
			return Optional.empty();
		}

		@Override
		public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDelta) {
			Optional<GroupPanelSlot> groupSlot = getGroupPanelSlotUnderMouse(mouseX, mouseY);
			boolean defaultControl = getDefaultGroupControlArea().contains(mouseX, mouseY);
			if (!isMouseOver(mouseX, mouseY) && groupSlot.isEmpty() && !defaultControl) {
				return Optional.empty();
			}
			boolean controlDown = Screen.hasControlDown();
			boolean altDown = Screen.hasAltDown();
			boolean shiftDown = Screen.hasShiftDown();
			if (!controlDown && !shiftDown) {
				return Optional.empty();
			}

			if (groupSlot.isPresent() || defaultControl) {
				String groupId = groupSlot.map(GroupPanelSlot::groupId).orElse(BookmarkGroupManager.DEFAULT_GROUP_ID);
				BookmarkHotkeyContext context = groupSlot
					.map(BookmarkOverlay::createGroupPanelHotkeyContext)
					.orElseGet(BookmarkOverlay::createDefaultGroupControlHotkeyContext);
				Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, controlDown, altDown, shiftDown);
				if (action.filter(a -> a == BookmarkHotkeyAction.SHIFT_AMOUNT || a == BookmarkHotkeyAction.SHIFT_AMOUNT_STEP).isPresent()) {
					long step = getScrollStep(scrollDelta, action.get());
					if (bookmarkList.shiftGroupAmount(groupId, step)) {
						playClickSound();
						return Optional.of(this);
					}
				}
				return Optional.empty();
			}

			Optional<IBookmark> bookmark = getBookmarkUnderMouse(mouseX, mouseY);
			if (bookmark.isPresent()) {
				BookmarkHotkeyContext context = createBookmarkHotkeyContext(bookmark.get());
				Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, controlDown, altDown, shiftDown);
				if (action.filter(a -> a == BookmarkHotkeyAction.SHIFT_AMOUNT || a == BookmarkHotkeyAction.SHIFT_AMOUNT_STEP).isPresent()) {
					long step = getScrollStep(scrollDelta, action.get());
					if (bookmarkList.shiftBookmarkAmount(bookmark.get(), step)) {
						playClickSound();
						return Optional.of(this);
					}
				} else if (action.filter(a -> a == BookmarkHotkeyAction.CYCLE_PERMUTATION).isPresent()) {
					long step = getScrollStep(scrollDelta, action.get());
					if (bookmarkList.cycleBookmarkPermutation(bookmark.get(), step)) {
						playClickSound();
						return Optional.of(this);
					}
				}
			}
			return Optional.empty();
		}

		private static long getScrollStep(double scrollDelta, BookmarkHotkeyAction action) {
			long direction = scrollDelta > 0 ? 1 : -1;
			long step = action == BookmarkHotkeyAction.SHIFT_AMOUNT_STEP ? SCROLL_LARGE_STEP : SCROLL_STEP;
			return direction * step;
		}
	}

	private boolean applyGroupClickAction(String groupId, BookmarkHotkeyAction action) {
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

	private static BookmarkHotkeyContext createGroupPanelHotkeyContext(GroupPanelSlot slot) {
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
		BookmarkItemMetadata metadata = bookmarkList.getBookmarkMetadata(bookmark);
		BookmarkHotkeySubject subject = metadata.recipeUid() == null ? BookmarkHotkeySubject.ITEM_BOOKMARK : BookmarkHotkeySubject.RECIPE_BOOKMARK;
		return BookmarkHotkeyContext.builder(subject)
			.hasIngredient(true)
			.hasRecipe(metadata.recipeUid() != null)
			.isBookmarkSlot(true)
			.build();
	}

	private Optional<IBookmark> getBookmarkUnderMouse(double mouseX, double mouseY) {
		return this.contents.getSlots()
			.filter(slot -> slot.getArea().contains(mouseX, mouseY))
			.map(IngredientListSlot::getOptionalElement)
			.flatMap(Optional::stream)
			.map(IElement::getBookmark)
			.flatMap(Optional::stream)
			.findFirst();
	}

	public Optional<FocusedRecipeCandidate> getFocusedRecipeCandidateUnderMouse(double mouseX, double mouseY) {
		if (isFavoritePanelDisplayed()) {
			return getFavoriteRecipeUnderMouse(mouseX, mouseY)
				.map(recipe -> new FocusedRecipeCandidate(recipe, false));
		}
		return getBookmarkUnderMouse(mouseX, mouseY)
			.map(bookmarkList::getBookmarkMetadata)
			.flatMap(FocusedRecipeCandidate::fromBookmarkMetadata);
	}

	private Optional<FocusedRecipe> getFavoriteRecipeUnderMouse(double mouseX, double mouseY) {
		return this.favoriteContents.getSlots()
			.filter(slot -> slot.getArea().contains(mouseX, mouseY))
			.map(IngredientListSlot::getOptionalElement)
			.flatMap(Optional::stream)
			.filter(FavoriteRecipeElement.class::isInstance)
			.map(FavoriteRecipeElement.class::cast)
			.map(FavoriteRecipeElement::getFocusedRecipe)
			.findFirst();
	}

	private Optional<String> getGroupIdUnderMouse(double mouseX, double mouseY) {
		return this.contents.getSlots()
			.filter(slot -> slot.getArea().contains(mouseX, mouseY))
			.map(IngredientListSlot::getOptionalElement)
			.flatMap(Optional::stream)
			.map(IElement::getBookmark)
			.flatMap(Optional::stream)
			.map(bookmarkList::getBookmarkGroupId)
			.findFirst();
	}

	private Optional<String> getGroupIdUnderMouseGroupPanel(double mouseX, double mouseY) {
		return getGroupPanelSlotUnderMouse(mouseX, mouseY)
			.map(GroupPanelSlot::groupId);
	}

	public Optional<String> getPullGroupIdUnderMouse(double mouseX, double mouseY) {
		if (getDefaultGroupControlArea().contains(mouseX, mouseY)) {
			return Optional.of(BookmarkGroupManager.DEFAULT_GROUP_ID);
		}
		return getGroupIdUnderMouseGroupPanel(mouseX, mouseY)
			.or(() -> getGroupIdUnderMouse(mouseX, mouseY));
	}

	public Optional<String> getPatternEncodeGroupIdUnderMouse(double mouseX, double mouseY) {
		if (getDefaultGroupControlArea().contains(mouseX, mouseY)) {
			return Optional.of(BookmarkGroupManager.DEFAULT_GROUP_ID);
		}
		return getGroupIdUnderMouseGroupPanel(mouseX, mouseY);
	}

	public boolean removeGroupUnderMouseGroupPanel(UserInput input) {
		Optional<GroupPanelSlot> slot = getGroupPanelSlotUnderMouse(input.getMouseX(), input.getMouseY())
			.filter(groupPanelSlot -> !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupPanelSlot.groupId()));
		if (slot.isEmpty()) {
			return false;
		}
		Optional<BookmarkHotkeyAction> action = BookmarkHotkeyRouter.resolveBookmarkKeyAction(
			createGroupPanelHotkeyContext(slot.get()),
			InputModifiers.hasShift(input),
			InputModifiers.hasControl(input)
		);
		if (action.filter(a -> a == BookmarkHotkeyAction.GROUP_REMOVE).isEmpty()) {
			return false;
		}
		if (!input.isSimulate()) {
			if (bookmarkList.removeGroup(slot.get().groupId())) {
				playClickSound();
			}
		}
		return true;
	}

	private Optional<GroupPanelSlot> getGroupPanelSlotUnderMouse(double mouseX, double mouseY) {
		return BookmarkPanelLayout.findRowUnderMouse(getGroupPanelRowSlots(), mouseX, mouseY, GROUP_PANEL_WIDTH)
			.map(this::toGroupPanelSlot);
	}

	private Optional<FavoriteRecipeRowPanelSlot> getFavoriteRecipeRowPanelSlotUnderMouse(double mouseX, double mouseY) {
		return BookmarkPanelLayout.findRowUnderMouse(toFavoriteRecipeRowSlots(), mouseX, mouseY, GROUP_PANEL_WIDTH)
			.map(slot -> new FavoriteRecipeRowPanelSlot(slot.item(), slot.area()));
	}

	private Optional<FavoriteRecipeElementPanelSlot> getFavoriteRecipeElementSlotUnderMouse(double mouseX, double mouseY) {
		return this.favoriteContents.getSlots()
			.filter(slot -> slot.getArea().contains(mouseX, mouseY))
			.map(slot -> slot.getOptionalElement()
				.filter(FavoriteRecipeElement.class::isInstance)
				.map(element -> new FavoriteRecipeElementPanelSlot((FavoriteRecipeElement<?>) element, slot.getArea())))
			.flatMap(Optional::stream)
			.findFirst();
	}

	private List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> getFavoriteRecipeElementPanelSlots() {
		return this.favoriteContents.getSlots()
			.map(slot -> slot.getOptionalElement()
				.filter(FavoriteRecipeElement.class::isInstance)
				.map(element -> (FavoriteRecipeElement<?>) element)
				.map(element -> new BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>(
					element,
					element.getRecipeRowGroupId(),
					slot.getArea(),
					false
				)))
			.flatMap(Optional::stream)
			.toList();
	}

	private List<BookmarkPanelLayout.RowSlot<FocusedRecipe>> toFavoriteRecipeRowSlots() {
		if (favoritePanelState.displayMode() != FavoriteRecipePanelState.DisplayMode.RECIPE_ROWS) {
			return List.of();
		}
		List<BookmarkPanelLayout.PanelSlot<FocusedRecipe>> panelSlots = this.favoriteContents.getSlots()
			.map(slot -> slot.getOptionalElement()
				.filter(FavoriteRecipeElement.class::isInstance)
				.map(FavoriteRecipeElement.class::cast)
				.filter(FavoriteRecipeElement::isVisible)
				.map(element -> new BookmarkPanelLayout.PanelSlot<>(
					element.getFocusedRecipe(),
					element.getRecipeRowGroupId(),
					slot.getArea(),
					false
				)))
			.flatMap(Optional::stream)
			.toList();
		return BookmarkPanelLayout.createRowSlots(panelSlots);
	}

	private ImmutableRect2i getDefaultGroupControlArea() {
		ImmutableRect2i back = contents.getBackButtonArea();
		ImmutableRect2i next = contents.getNextPageButtonArea();
		if (back.isEmpty() || next.isEmpty()) {
			return ImmutableRect2i.EMPTY;
		}
		int x = back.getX() + back.getWidth();
		int width = next.getX() - x;
		if (width <= 0) {
			return ImmutableRect2i.EMPTY;
		}
		return new ImmutableRect2i(x, back.getY(), width, back.getHeight());
	}

	private Optional<GroupPanelSlot> getClosestGroupPanelSlotAtY(double mouseY) {
		return BookmarkPanelLayout.findClosestRowAtY(getGroupPanelRowSlots(), mouseY)
			.map(this::toGroupPanelSlot);
	}

	private static ImmutableRect2i getGroupPanelArea(ImmutableRect2i slotArea) {
		return BookmarkPanelLayout.getGroupPanelArea(slotArea, GROUP_PANEL_WIDTH);
	}

	private static List<BookmarkPanelLayout.RowSlot<IBookmark>> toRowSlots(List<GroupPanelSlot> slots) {
		return slots.stream()
			.map(BookmarkOverlay::toRowSlot)
			.toList();
	}

	private static BookmarkPanelLayout.RowSlot<IBookmark> toRowSlot(GroupPanelSlot slot) {
		return new BookmarkPanelLayout.RowSlot<>(slot.bookmark(), slot.groupId(), slot.area());
	}

	private GroupPanelSlot toGroupPanelSlot(BookmarkPanelLayout.RowSlot<IBookmark> slot) {
		return new GroupPanelSlot(slot.item(), slot.groupId(), slot.area());
	}

	public List<IBookmarkDragTarget> createBookmarkDragTargets() {
		updateScreenPropertiesIfDirty();
		List<DragTarget> slotTargets = this.contents.getSlots()
			.map(this::createDragTarget)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.toList();

		List<GroupPanelDragTarget> groupPanelTargets = this.contents.getSlots()
			.map(this::createGroupPanelDragTarget)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.toList();

		if (slotTargets.isEmpty()) {
			return List.of();
		}

		IBookmark firstBookmark = slotTargets.get(0).bookmark;
		IBookmark lastBookmark = slotTargets.get(slotTargets.size() - 1).bookmark;

		List<IBookmarkDragTarget> bookmarkDragTargets = new ArrayList<>(slotTargets);
		bookmarkDragTargets.addAll(groupPanelTargets);

		IPaged pageDelegate = this.contents.getPageDelegate();
		if (pageDelegate.getPageCount() > 1) {
			// if a bookmark is dropped on the next button, put it on the next page
			bookmarkDragTargets.add(new ActionDragTarget(
				this.contents.getNextPageButtonArea(),
				lastBookmark,
				bookmarkList.getBookmarkGroupId(lastBookmark),
				bookmarkList,
				1,
				pageDelegate::nextPage
			));

			// if a bookmark is dropped on the back button, put it on the previous page
			bookmarkDragTargets.add(new ActionDragTarget(
				this.contents.getBackButtonArea(),
				firstBookmark,
				bookmarkList.getBookmarkGroupId(firstBookmark),
				bookmarkList,
				-1,
				pageDelegate::previousPage
			));
		}

		// if a bookmark is dropped somewhere else in the contents area, put it at the end of the current page
		bookmarkDragTargets.add(new DragTarget(
			this.contents.getSlotBackgroundArea(),
			lastBookmark,
			bookmarkList.getBookmarkGroupId(lastBookmark),
			bookmarkList,
			0
		));

		return bookmarkDragTargets;
	}

	private Optional<DragTarget> createDragTarget(IngredientListSlot ingredientListSlot) {
		List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots = getPanelSlots();
		List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots = getGroupPanelRowSlots();
		return ingredientListSlot.getOptionalElement()
			.flatMap(IElement::getBookmark)
			.map(bookmark -> new DragTarget(
				ingredientListSlot.getArea(),
				bookmark,
				bookmarkList.getBookmarkGroupId(bookmark),
				bookmarkList,
				0,
				panelSlots,
				rowSlots,
				rowSlots.stream()
					.filter(rowSlot -> rowSlot.area().equals(ingredientListSlot.getArea()))
					.findFirst()
					.orElse(null)
			));
	}

	private Optional<GroupPanelDragTarget> createGroupPanelDragTarget(IngredientListSlot ingredientListSlot) {
		Optional<GroupPanelSlot> rowSlot = getGroupPanelSlots().stream()
			.filter(slot -> slot.area().equals(ingredientListSlot.getArea()))
			.findFirst();
		if (rowSlot.isEmpty()) {
			return Optional.empty();
		}
		return ingredientListSlot.getOptionalElement()
			.flatMap(IElement::getBookmark)
			.map(bookmark -> {
				ImmutableRect2i area = ingredientListSlot.getArea();
				ImmutableRect2i groupPanelArea = getGroupPanelArea(area);
				return new GroupPanelDragTarget(groupPanelArea, bookmark, rowSlot.get().groupId(), bookmarkList);
			});
	}

	private void updateSortDrag(int mouseX, int mouseY) {
		if (sortDragState != null) {
			sortDragState.update(bookmarkList, getPanelSlots(), getProjectedPanelSlots(), mouseX, mouseY, System.currentTimeMillis());
			if (sortDragState.isActive()) {
				contents.updateLayout(false);
				sortDragState.updateProjectedSlotOverlayAreas(getProjectedPanelSlots());
			}
		}
	}

	private void updateFavoriteSortDrag(int mouseX, int mouseY) {
		if (favoriteSortDragState != null) {
			boolean changed = favoriteSortDragState.update(
				favoriteRecipes,
				favoritePanelState,
				getFavoriteRecipeElementPanelSlots(),
				mouseX,
				mouseY,
				System.currentTimeMillis()
			);
			if (changed || favoriteSortDragState.isActive()) {
				favoriteContents.updateLayout(false);
				favoriteSortDragState.updateProjectedSlotOverlayAreas(getFavoriteRecipeElementPanelSlots());
			}
		}
	}

	private IDragHandler createSortDragHandler() {
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
				Optional<GroupPanelSlot> slot = getGroupPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
				if (slot.filter(groupSlot -> !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupSlot.groupId())).isPresent()) {
					sortDragState = BookmarkSortDragState.group(
						slot.get().groupId(),
						slot.get().area(),
						input.getMouseX(),
						input.getMouseY()
					);
					return Optional.of(this);
				}

				return getDraggableIngredientUnderMouse(input.getMouseX(), input.getMouseY())
					.findFirst()
					.flatMap(clicked -> clicked.getElement()
						.getBookmark()
						.map(bookmark -> {
							sortDragState = BookmarkSortDragState.item(
								bookmark,
								clicked.getArea(),
								input.getMouseX(),
								input.getMouseY(),
								System.currentTimeMillis(),
								GROUP_PANEL_DRAG_THRESHOLD_MS
							);
							return this;
						}));
			}

			@Override
			public boolean handleDragComplete(Screen screen, UserInput input) {
				if (sortDragState == null) {
					return false;
				}
				boolean handled = sortDragState.isActive();
				sortDragState.stop();
				sortDragState = null;
				contents.updateLayout(false);
				return handled;
			}

			@Override
			public void handleDragCanceled() {
				if (sortDragState != null) {
					sortDragState.stop();
					sortDragState = null;
					contents.updateLayout(false);
				}
			}
		};
	}

	private IDragHandler createFavoriteSortDragHandler() {
		return new IDragHandler() {
			@Override
			public Optional<IDragHandler> handleDragStart(Screen screen, UserInput input) {
				if (favoritePanelState.displayMode() != FavoriteRecipePanelState.DisplayMode.RECIPE_ROWS) {
					return Optional.empty();
				}
				if (input.getKey().getType() != InputConstants.Type.MOUSE) {
					return Optional.empty();
				}
				int mouseButton = input.getKey().getValue();
				if (mouseButton != InputConstants.MOUSE_BUTTON_LEFT || !InputModifiers.hasShift(input)) {
					return Optional.empty();
				}

				Optional<FavoriteRecipeRowPanelSlot> rowPanelSlot = getFavoriteRecipeRowPanelSlotUnderMouse(input.getMouseX(), input.getMouseY());
				if (rowPanelSlot.isPresent()) {
					favoriteSortDragState = FavoriteRecipeSortDragState.recipe(
						rowPanelSlot.get().recipe(),
						rowPanelSlot.get().area(),
						input.getMouseX(),
						input.getMouseY(),
						System.currentTimeMillis(),
						GROUP_PANEL_DRAG_THRESHOLD_MS
					);
					return Optional.of(this);
				}

				Optional<FavoriteRecipeElementPanelSlot> elementSlot = getFavoriteRecipeElementSlotUnderMouse(input.getMouseX(), input.getMouseY());
				if (elementSlot.isEmpty()) {
					return Optional.empty();
				}
				FavoriteRecipeElement<?> element = elementSlot.get().element();
				if (element.isFavoriteTarget()) {
					favoriteSortDragState = FavoriteRecipeSortDragState.recipe(
						element.getFocusedRecipe(),
						elementSlot.get().area(),
						input.getMouseX(),
						input.getMouseY(),
						System.currentTimeMillis(),
						GROUP_PANEL_DRAG_THRESHOLD_MS
					);
					return Optional.of(this);
				}
				return element.getRecipeInputKey()
					.map(inputKey -> {
						favoriteSortDragState = FavoriteRecipeSortDragState.input(
							element.getFocusedRecipe(),
							inputKey,
							elementSlot.get().area(),
							input.getMouseX(),
							input.getMouseY(),
							System.currentTimeMillis(),
							GROUP_PANEL_DRAG_THRESHOLD_MS
						);
						return this;
					});
			}

			@Override
			public boolean handleDragComplete(Screen screen, UserInput input) {
				if (favoriteSortDragState == null) {
					return false;
				}
				boolean handled = favoriteSortDragState.isActive();
				favoriteSortDragState.stop();
				favoriteSortDragState = null;
				favoritePanelState.clearSortDragHiddenElements();
				favoriteContents.updateLayout(false);
				return handled;
			}

			@Override
			public void handleDragCanceled() {
				if (favoriteSortDragState != null) {
					favoriteSortDragState.stop();
					favoriteSortDragState = null;
					favoritePanelState.clearSortDragHiddenElements();
					favoriteContents.updateLayout(false);
				}
			}
		};
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		if (isFavoritePanelDisplayed()) {
			return this.favoriteContents.isMouseOver(mouseX, mouseY);
		}
		return this.contents.isMouseOver(mouseX, mouseY);
	}

	public static class ActionDragTarget extends DragTarget {
		private final Runnable action;

		public ActionDragTarget(
			ImmutableRect2i area,
			IBookmark bookmark,
			String targetGroupId,
			BookmarkList bookmarkList,
			int offset,
			Runnable action
		) {
			super(area, bookmark, targetGroupId, bookmarkList, offset);
			this.action = action;
		}

		@Override
		public void accept(IBookmark bookmark) {
			super.accept(bookmark);
			action.run();
		}

		@Override
		public void accept(IBookmark bookmark, double mouseX, double mouseY) {
			super.accept(bookmark, mouseX, mouseY);
			action.run();
		}
	}

	public static class DragTarget implements IBookmarkDragTarget {
		private final ImmutableRect2i area;
		private final IBookmark bookmark;
		private final String targetGroupId;
		private final BookmarkList bookmarkList;
		private final int offset;
		private final List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots;
		private final List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots;
		private final @Nullable BookmarkPanelLayout.RowSlot<IBookmark> targetRow;

		public DragTarget(ImmutableRect2i area, IBookmark bookmark, String targetGroupId, BookmarkList bookmarkList, int offset) {
			this(area, bookmark, targetGroupId, bookmarkList, offset, List.of(), List.of(), null);
		}

		public DragTarget(
			ImmutableRect2i area,
			IBookmark bookmark,
			String targetGroupId,
			BookmarkList bookmarkList,
			int offset,
			List<? extends BookmarkPanelLayout.RowSlot<? extends IBookmark>> rowSlots,
			@Nullable BookmarkPanelLayout.RowSlot<? extends IBookmark> targetRow
		) {
			this(area, bookmark, targetGroupId, bookmarkList, offset, List.of(), copyRows(rowSlots), copyRow(targetRow));
		}

		public DragTarget(
			ImmutableRect2i area,
			IBookmark bookmark,
			String targetGroupId,
			BookmarkList bookmarkList,
			int offset,
			List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
			List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots,
			@Nullable BookmarkPanelLayout.RowSlot<IBookmark> targetRow
		) {
			this.area = area;
			this.bookmark = bookmark;
			this.targetGroupId = targetGroupId;
			this.bookmarkList = bookmarkList;
			this.offset = offset;
			this.panelSlots = List.copyOf(panelSlots);
			this.rowSlots = List.copyOf(rowSlots);
			this.targetRow = targetRow;
		}

		private static List<BookmarkPanelLayout.RowSlot<IBookmark>> copyRows(
			List<? extends BookmarkPanelLayout.RowSlot<? extends IBookmark>> rows
		) {
			return rows.stream()
				.map(DragTarget::copyRow)
				.toList();
		}

		private static @Nullable BookmarkPanelLayout.RowSlot<IBookmark> copyRow(
			@Nullable BookmarkPanelLayout.RowSlot<? extends IBookmark> row
		) {
			if (row == null) {
				return null;
			}
			return new BookmarkPanelLayout.RowSlot<>(row.item(), row.groupId(), row.area());
		}

		@Override
		public ImmutableRect2i getArea() {
			return area;
		}

		@Override
		public void accept(IBookmark bookmark) {
			accept(bookmark, area.getX() + area.getWidth() / 2.0, area.getY() + area.getHeight() / 2.0);
		}

		@Override
		public void accept(IBookmark bookmark, double mouseX, double mouseY) {
			BookmarkMoveSelection selection = BookmarkMoveSelection.create(bookmarkList, bookmark);
			Optional<BookmarkItemMovePlan> movePlan = createMovePlan(bookmark, selection, mouseX, mouseY);
			if (movePlan.isPresent()) {
				BookmarkItemMovePlan plan = movePlan.get();
				if (plan.rejected()) {
					return;
				}
				selection.moveToBookmark(bookmarkList, plan.targetBookmark(), plan.targetGroupId(), plan.offset());
				return;
			}
			String sourceGroupId = bookmarkList.getBookmarkGroupId(bookmark);
			if (!sourceGroupId.equals(targetGroupId)) {
				return;
			}
			selection.moveToBookmark(bookmarkList, this.bookmark, targetGroupId, offset);
		}

		@Override
		public Optional<BookmarkDragPreview> getPreview(IBookmark bookmark, double mouseX, double mouseY) {
			BookmarkMoveSelection selection = BookmarkMoveSelection.create(bookmarkList, bookmark);
			return createMovePlan(bookmark, selection, mouseX, mouseY)
				.map(plan -> new BookmarkDragPreview(getPreviewArea(bookmark, plan), plan.rejected()))
				.or(() -> Optional.of(new BookmarkDragPreview(area, isCrossGroupFallback(bookmark))));
		}

		private Optional<BookmarkItemMovePlan> createMovePlan(
			IBookmark bookmark,
			BookmarkMoveSelection selection,
			double mouseX,
			double mouseY
		) {
			if (targetRow == null || rowSlots.isEmpty()) {
				return Optional.empty();
			}
			String sourceGroupId = bookmarkList.getBookmarkGroupId(bookmark);
			if (!sourceGroupId.equals(targetRow.groupId()) && !selection.crossGroupMove()) {
				return Optional.empty();
			}
			if (sourceGroupId.equals(targetRow.groupId())) {
				return Optional.of(BookmarkItemMovePlan.createSameGroupByRow(
					panelSlots,
					rowSlots,
					bookmark,
					targetRow,
					bookmarkList.getBookmarks(),
					mouseY
				));
			}
			return Optional.of(BookmarkItemMovePlan.createCrossGroup(
				panelSlots,
				rowSlots,
				targetRow,
				mouseY,
				bookmarkList.getBookmarkGroups().stream()
					.collect(Collectors.toMap(BookmarkGroup::id, Function.identity())),
				selection.recipeIds(),
				getRecipeIdsByGroup(bookmarkList, selection.bookmarks())
			));
		}

		private ImmutableRect2i getPreviewArea(IBookmark bookmark, BookmarkItemMovePlan plan) {
			String sourceGroupId = bookmarkList.getBookmarkGroupId(bookmark);
			if (sourceGroupId.equals(plan.targetGroupId()) && targetRow != null) {
				ImmutableRect2i rowArea = rowSlots.stream()
					.filter(row -> row.item().equals(plan.targetBookmark()))
					.findFirst()
					.map(BookmarkPanelLayout.RowSlot::area)
					.orElse(targetRow.area());
				int y = plan.offset() > 0 ? rowArea.getY() + rowArea.getHeight() : rowArea.getY() - 2;
				return new ImmutableRect2i(rowArea.getX(), y, rowArea.getWidth(), 2);
			}
			return area;
		}

		private boolean isCrossGroupFallback(IBookmark bookmark) {
			String sourceGroupId = bookmarkList.getBookmarkGroupId(bookmark);
			return !sourceGroupId.equals(targetGroupId);
		}

		private static Map<String, Set<ResourceLocation>> getRecipeIdsByGroup(
			BookmarkList bookmarkList,
			List<IBookmark> ignoredBookmarks
		) {
			Set<IBookmark> ignored = new HashSet<>(ignoredBookmarks);
			Map<String, Set<ResourceLocation>> recipeIdsByGroup = new HashMap<>();
			for (IBookmark bookmark : bookmarkList.getBookmarks()) {
				if (ignored.contains(bookmark)) {
					continue;
				}
				BookmarkItemMetadata metadata = bookmarkList.getBookmarkMetadata(bookmark);
				ResourceLocation recipeUid = metadata.recipeUid();
				if (recipeUid != null && metadata.type().isRecipeAssociated()) {
					recipeIdsByGroup.computeIfAbsent(metadata.groupId(), groupId -> new HashSet<>())
						.add(recipeUid);
				}
			}
			return recipeIdsByGroup;
		}
	}

	public static class GroupPanelDragTarget implements IBookmarkDragTarget {
		private final ImmutableRect2i area;
		private final IBookmark targetBookmark;
		private final String targetGroupId;
		private final BookmarkList bookmarkList;

		public GroupPanelDragTarget(ImmutableRect2i area, IBookmark targetBookmark, String targetGroupId, BookmarkList bookmarkList) {
			this.area = area;
			this.targetBookmark = targetBookmark;
			this.targetGroupId = targetGroupId;
			this.bookmarkList = bookmarkList;
		}

		@Override
		public ImmutableRect2i getArea() {
			return area;
		}

		@Override
		public void accept(IBookmark bookmark) {
			// GTNH NEI edits bookmark groups through bracket drags, not by dropping bookmarks on the bracket strip.
		}

		@Override
		public Optional<BookmarkDragPreview> getPreview(IBookmark bookmark, double mouseX, double mouseY) {
			return Optional.of(new BookmarkDragPreview(area, true));
		}
	}

	private class GroupPanelDrag {
		private final GroupPanelSlot startSlot;
		private final boolean exclude;
		private final @Nullable BookmarkHotkeyAction clickFallbackAction;
		private final long startedAtMillis;
		private @Nullable GroupPanelSlot endSlot;

		public GroupPanelDrag(
			GroupPanelSlot startSlot,
			boolean exclude,
			@Nullable BookmarkHotkeyAction clickFallbackAction
		) {
			this.startSlot = startSlot;
			this.exclude = exclude;
			this.clickFallbackAction = clickFallbackAction;
			this.startedAtMillis = System.currentTimeMillis();
		}

		public List<GroupPanelSlot> getPreviewGroupPanelSlots(
			List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots,
			List<GroupPanelSlot> groupPanelSlots
		) {
			updateEndSlot(MouseUtil.getY());
			if (this.endSlot == null) {
				return groupPanelSlots;
			}

			List<BookmarkPanelLayout.RowSlot<IBookmark>> previewRows = BookmarkPanelLayout.createGroupingPreviewRows(
				panelSlots,
				toRowSlots(groupPanelSlots),
				toRowSlot(startSlot),
				toRowSlot(this.endSlot),
				exclude,
				GROUPING_PREVIEW_GROUP_ID
			);
			return previewRows.stream()
				.map(BookmarkOverlay.this::toGroupPanelSlot)
				.toList();
		}

		public boolean complete(UserInput input) {
			updateEndSlot(input.getMouseY());
			if (this.endSlot == null) {
				if (this.clickFallbackAction == null) {
					return false;
				}
				if (input.isSimulate()) {
					return true;
				}
				boolean changed = applyGroupClickAction(startSlot.groupId(), this.clickFallbackAction);
				if (changed) {
					playClickSound();
				}
				return changed;
			}
			BookmarkGroupingPlan plan = BookmarkGroupingPlan.create(
				getPanelSlots(),
				toRowSlot(startSlot),
				toRowSlot(this.endSlot),
				exclude
			);
			if (plan.bookmarks().isEmpty()) {
				return false;
			}
			boolean changed = input.isSimulate() || plan.apply(bookmarkList, "Group");
			if (changed && !input.isSimulate()) {
				playClickSound();
			}
			return changed;
		}

		private void updateEndSlot(double mouseY) {
			Optional<GroupPanelSlot> hoveredSlot = getClosestGroupPanelSlotAtY(mouseY);
			if (hoveredSlot.isEmpty()) {
				return;
			}
			GroupPanelSlot currentEndSlot = hoveredSlot.get();
			long elapsedMillis = System.currentTimeMillis() - startedAtMillis;
			if (BookmarkPanelLayout.shouldUpdateDragEnd(
				toRowSlot(startSlot),
				this.endSlot == null ? null : toRowSlot(this.endSlot),
				toRowSlot(currentEndSlot),
				elapsedMillis,
				GROUP_PANEL_DRAG_THRESHOLD_MS
			)) {
				this.endSlot = currentEndSlot;
			}
		}

	}
}
