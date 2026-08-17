package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.config.HistoryDisplaySide;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.config.file.IConfigListener;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutablePoint2i;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkMoveSelection;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IDragHandler;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.FocusedRecipeCandidate;
import mezz.jei.gui.input.ICharTypedHandler;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.MouseUtil;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedDragHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.NullDragHandler;
import mezz.jei.gui.input.handlers.ProxyDragHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import mezz.jei.gui.overlay.GuiPropertiesCache;
import mezz.jei.gui.overlay.IScreenPropertiesUpdater;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryButton;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay;
import mezz.jei.gui.overlay.bookmarks.BookmarkGroupDropBridge.GroupDropAvailabilityProvider;
import mezz.jei.gui.overlay.bookmarks.BookmarkGroupDropBridge.GroupDropHandler;
import mezz.jei.gui.overlay.bookmarks.BookmarkGroupDropBridge.GroupDropHighlightProvider;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlayLayout.GroupPanelSlot;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridLayout;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

public class BookmarkOverlay implements IRecipeFocusSource, IBookmarkOverlay, ICharTypedHandler {
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

	public static void setGroupDropHandler(@Nullable GroupDropHandler groupDropHandler) {
		BookmarkGroupDropBridge.setGroupDropHandler(groupDropHandler);
	}

	public static void setGroupDropAvailabilityProvider(@Nullable GroupDropAvailabilityProvider groupDropAvailabilityProvider) {
		BookmarkGroupDropBridge.setGroupDropAvailabilityProvider(groupDropAvailabilityProvider);
	}

	public static void setGroupDropHighlightProvider(@Nullable GroupDropHighlightProvider groupDropHighlightProvider) {
		BookmarkGroupDropBridge.setGroupDropHighlightProvider(groupDropHighlightProvider);
	}

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
	private final ScrollStep scrollStep;
	private final ScrollStepTextField scrollStepField;
	private ImmutableRect2i scrollStepArea = ImmutableRect2i.EMPTY;
	private final BookmarkOverlayLayout layout;
	private final BookmarkOverlayRenderer renderer;
	private final BookmarkOverlayInputHandlers inputHandlers;
	private final BookmarkOverlayDragHandlers dragHandlers;

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
		IInternalKeyMappings keyBindings,
		ScrollStep scrollStep
	) {
		this.bookmarkList = bookmarkList;
		this.favoriteRecipes = favoriteRecipes;
		this.favoritePanelState = favoritePanelState;
		this.toggleState = toggleState;
		this.clientConfig = clientConfig;
		this.keyBindings = keyBindings;
		this.scrollStep = scrollStep;
		this.scrollStepField = new ScrollStepTextField(scrollStep);
		this.bookmarkButton = BookmarkButton.create(this, keyBindings);
		this.favoriteButton = FavoriteRecipePanelButton.create(this, favoriteRecipes);
		this.historyButton = LookupHistoryButton.create(clientConfig);
		this.contents = contents;
		this.layout = new BookmarkOverlayLayout(bookmarkList, contents);
		this.renderer = new BookmarkOverlayRenderer(
			this,
			bookmarkList,
			GROUP_PANEL_WIDTH,
			GROUP_PANEL_HOVER_COLOR,
			GROUP_NONE_COLOR,
			GROUP_CHAIN_COLOR,
			FAVORITE_RECIPE_ROW_COLOR,
			GROUP_PLACEHOLDER_COLOR
		);
		this.inputHandlers = new BookmarkOverlayInputHandlers(this);
		this.dragHandlers = new BookmarkOverlayDragHandlers(this);
		this.favoriteContents = favoriteContents;
		this.lookupHistoryOverlay = lookupHistoryOverlay;
		this.guiPropertiesCache = new GuiPropertiesCache<>(
			screen -> screenHelper.getGuiProperties(screen)
				.orElse(null)
		);
		this.bookmarkDragManager = new BookmarkDragManager(this);
		contents.setExtraHoveredIngredientSource(() -> getGroupDropHoverIngredient(MouseUtil.getX(), MouseUtil.getY()));
		bookmarkList.addSourceListChangedListener(() -> {
			layout.clearPanelSnapshot();
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

	@Override
	public boolean hasKeyboardFocus() {
		return scrollStepField.isFocused();
	}

	@Override
	public boolean onCharTyped(char codePoint, int modifiers) {
		return scrollStepField.charTyped(codePoint, modifiers);
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
		layout.clearPanelSnapshot();
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
		// TEMP DISABLED (upstream merge trial): shift bookmark contents below top-left exclusion areas.
		// ImmutableRect2i contentsLayoutArea = avoidTopLeftExclusions(layoutAreas.contentsLayoutArea(), guiExclusionAreas);
		// layoutAreas = new LayoutAreas(contentsLayoutArea, layoutAreas.historyArea(), layoutAreas.contentsBottomLimit());
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
			ImmutableRect2i favoriteButtonArea = calculateFavoritePanelButtonArea(historyButtonArea);
			this.favoriteButton.updateBounds(favoriteButtonArea);
			ImmutableRect2i gridArea = this.contents.getIngredientGridArea();
			this.scrollStepArea = calculateScrollStepArea(favoriteButtonArea, gridArea.getX() + gridArea.getWidth());
			this.scrollStepField.updateBounds(scrollStepArea);
		} else {
			ImmutableRect2i bookmarkButtonArea = displayArea
				.insetBy(BORDER_MARGIN)
				.keepBottom(BUTTON_SIZE)
				.keepLeft(BUTTON_SIZE);
			this.bookmarkButton.updateBounds(bookmarkButtonArea);
			ImmutableRect2i historyButtonArea  = bookmarkButtonArea.moveRight(2 + BUTTON_SIZE);
			this.historyButton.updateBounds(historyButtonArea);
			ImmutableRect2i favoriteButtonArea = calculateFavoritePanelButtonArea(historyButtonArea);
			this.favoriteButton.updateBounds(favoriteButtonArea);
			this.scrollStepArea = calculateScrollStepArea(favoriteButtonArea, displayArea.getWidth() - BORDER_MARGIN);
			this.scrollStepField.updateBounds(scrollStepArea);
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

	public static ImmutableRect2i calculateScrollStepArea(ImmutableRect2i favoriteButtonArea, int rightBoundary) {
		int x = favoriteButtonArea.getX() + favoriteButtonArea.getWidth() + INNER_PADDING;
		int width = rightBoundary - x + 1;
		return new ImmutableRect2i(x, favoriteButtonArea.getY(), Math.max(0, width), favoriteButtonArea.getHeight());
	}

	/**
	 * Shifts the bookmark contents area below exclusion areas that meaningfully cover its
	 * top-left slot cell. Such areas (e.g. an FTB sidebar icon) would otherwise create a
	 * partial first row that breaks group borders; shifting the whole area keeps every row
	 * full and lets the page buttons and scrollbar follow automatically. The shift is
	 * aligned to whole grid rows, and tiny slivers keep blocking only the slot underneath.
	 */
	static ImmutableRect2i avoidTopLeftExclusions(
		ImmutableRect2i area,
		Set<ImmutableRect2i> guiExclusionAreas
	) {
		int areaBottom = area.getY() + area.getHeight();
		int candidateY = area.getY();
		while (candidateY < areaBottom) {
			ImmutableRect2i firstCellArea = new ImmutableRect2i(
				area.getX(),
				candidateY,
				IngredientGridLayout.INGREDIENT_WIDTH,
				IngredientGridLayout.INGREDIENT_HEIGHT
			);
			int nextY = candidateY;
			for (ImmutableRect2i exclusion : guiExclusionAreas) {
				if (!exclusion.intersects(firstCellArea)) {
					continue;
				}
				int overlapWidth = Math.min(exclusion.getX() + exclusion.getWidth(), firstCellArea.getX() + firstCellArea.getWidth())
					- Math.max(exclusion.getX(), firstCellArea.getX());
				int overlapHeight = Math.min(exclusion.getY() + exclusion.getHeight(), firstCellArea.getY() + firstCellArea.getHeight())
					- Math.max(exclusion.getY(), firstCellArea.getY());
				if (overlapWidth < IngredientGridLayout.INGREDIENT_WIDTH / 2 || overlapHeight < IngredientGridLayout.INGREDIENT_HEIGHT / 2) {
					continue;
				}
				int exclusionBottom = exclusion.getY() + exclusion.getHeight();
				nextY = Math.max(nextY, alignUp(exclusionBottom - area.getY(), IngredientGridLayout.INGREDIENT_HEIGHT) + area.getY());
				break;
			}
			if (nextY == candidateY) {
				if (candidateY == area.getY()) {
					return area;
				}
				return new ImmutableRect2i(area.getX(), candidateY, area.getWidth(), areaBottom - candidateY);
			}
			candidateY = nextY;
		}
		return area;
	}

	private static int alignUp(int value, int alignment) {
		return (value + alignment - 1) / alignment * alignment;
	}

	public static LayoutAreas calculateLayoutAreas(ImmutableRect2i displayArea, boolean lookupHistoryOnSide, int historyRows) {
		ImmutableRect2i contentsLayoutArea = displayArea
			.cropBottom(BUTTON_SIZE + INNER_PADDING)
			.cropLeft(GROUP_PANEL_WIDTH);
		Optional<ImmutableRect2i> historyArea = Optional.empty();
		OptionalInt contentsBottomLimit = OptionalInt.empty();
		if (lookupHistoryOnSide) {
			ImmutableRect2i calculatedHistoryArea = displayArea
				.cropLeft(GROUP_PANEL_WIDTH)
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
			dragHandlers.updateSortDrag(mouseX, mouseY);
			this.bookmarkDragManager.updateDrag(mouseX, mouseY);
			this.contents.drawForeground(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
			if (sortDragState != null) {
				sortDragState.drawTargetSlotOverlays(guiGraphics);
				sortDragState.drawSourceSlotOverlays(guiGraphics, getPanelSlots());
			}
			renderer.drawBookmarkGroupPanels(guiGraphics, mouseX, mouseY, groupPanelDrag, sortDragState);
			if (groupPanelDrag != null) {
				groupPanelDrag.drawPreview(guiGraphics, mouseX, mouseY);
			}
		}
		if (isFavoritePanelDisplayed()) {
			dragHandlers.updateFavoriteSortDrag(mouseX, mouseY);
			this.favoriteContents.drawForeground(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
			if (favoriteSortDragState != null) {
				favoriteSortDragState.drawTargetSlotOverlays(guiGraphics);
				favoriteSortDragState.drawSourceSlotOverlays(guiGraphics);
			}
			renderer.drawFavoriteRecipeRowPanels(guiGraphics, mouseX, mouseY, favoritePanelState);
		}
		if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.draw(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
		}
		if (this.guiPropertiesCache.hasValidScreen()) {
			this.bookmarkButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			this.favoriteButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			this.historyButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			this.scrollStepField.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
		}
	}

	public void drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (sortDragState != null) {
			boolean drewSortDrag = sortDragState.drawDraggedItems(guiGraphics, mouseX, mouseY);
			drewSortDrag = renderer.drawFloatingGroupPanel(guiGraphics, sortDragState, mouseX, mouseY) || drewSortDrag;
			if (drewSortDrag || sortDragState.isActive()) {
				return;
			}
		}
		if (favoriteSortDragState != null) {
			boolean drewSortDrag = favoriteSortDragState.drawDraggedItems(guiGraphics, mouseX, mouseY);
			drewSortDrag = renderer.drawFloatingFavoriteRecipeRowPanel(guiGraphics, favoriteSortDragState, mouseX, mouseY) || drewSortDrag;
			if (drewSortDrag || favoriteSortDragState.isActive()) {
				return;
			}
		}
		updateScreenPropertiesIfDirty();
		if (!this.bookmarkDragManager.drawDraggedItem(guiGraphics, mouseX, mouseY)) {
			if (isListDisplayed()) {
				if (renderer.drawGroupHotkeyTooltip(guiGraphics, mouseX, mouseY)) {
					return;
				}
				this.contents.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
			}
			if (isFavoritePanelDisplayed()) {
				if (renderer.drawFavoriteRecipeRowHotkeyTooltip(guiGraphics, mouseX, mouseY)) {
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
		final IUserInputHandler recipeCollapseInputHandler = inputHandlers.new RecipeCollapseInputHandler();
		final IUserInputHandler favoriteRecipeRowInputHandler = inputHandlers.new FavoriteRecipeRowInputHandler();
		final IUserInputHandler groupInputHandler = inputHandlers.new GroupInputHandler();

		final IUserInputHandler buttonInputHandler = new CombinedInputHandler(
			"BookmarkOverlayButton",
			bookmarkButtonInputHandler,
			favoriteButtonInputHandler,
			historyButtonInputHandler
		);
		final IUserInputHandler fallbackInputHandler = new CombinedInputHandler(
			"BookmarkOverlayFallback",
			groupInputHandler,
			this.scrollStepField.createInputHandler(),
			buttonInputHandler
		);

		final IUserInputHandler displayedInputHandler = new CombinedInputHandler(
			"BookmarkOverlay",
			recipeCollapseInputHandler,
			groupInputHandler,
			this.scrollStepField.createInputHandler(),
			this.contents.createInputHandler(),
			buttonInputHandler
		);
		final IUserInputHandler favoriteDisplayedInputHandler = new CombinedInputHandler(
			"FavoriteRecipeOverlay",
			favoriteRecipeRowInputHandler,
			this.scrollStepField.createInputHandler(),
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
			return fallbackInputHandler;
		});
	}

	public IDragHandler createDragHandler() {
		final IDragHandler historyDragHandler = this.lookupHistoryOverlay.createDragHandler();
		final IDragHandler favoriteDragHandler = new CombinedDragHandler(
			dragHandlers.createFavoriteSortDragHandler(),
			this.favoriteContents.createDragHandler()
		);
		final IDragHandler sortDragHandler = dragHandlers.createSortDragHandler();
		final IDragHandler contentsDragHandler = new ProxyDragHandler(() ->
			groupPanelDrag != null && groupPanelDrag.isDropMode()
				? NullDragHandler.INSTANCE
				: this.contents.createDragHandler()
		);
		final IDragHandler combinedDragHandlers = new CombinedDragHandler(
			sortDragHandler,
			contentsDragHandler,
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
			this.bookmarkList.getDisplaySlots(
				this.contents.getUsableColumnCount(),
				this.contents.getUsableColumnsPerRow()
			);
			this.contents.drawOnForeground(guiGraphics, mouseX, mouseY);
		}
		if (isFavoritePanelDisplayed()) {
			this.favoriteContents.drawOnForeground(guiGraphics, mouseX, mouseY);
		}
		this.lookupHistoryOverlay.drawOnForeground(guiGraphics, mouseX, mouseY);
	}

	List<GroupPanelSlot> getGroupPanelSlots() {
		return layout.getGroupPanelSlots();
	}

	boolean canStartGroupDrop(String groupId) {
		return BookmarkGroupDropBridge.canStartGroupDrop(bookmarkList, groupId);
	}

	private Optional<ITypedIngredient<?>> getGroupDropHoverIngredient(double mouseX, double mouseY) {
		return BookmarkGroupDropBridge.getGroupDropHoverIngredient(bookmarkList, getGroupPanelSlotUnderMouse(mouseX, mouseY));
	}

	BookmarkList getBookmarkList() {
		return bookmarkList;
	}

	IInternalKeyMappings getKeyBindings() {
		return keyBindings;
	}

	FavoriteRecipeStore getFavoriteRecipes() {
		return favoriteRecipes;
	}

	FavoriteRecipePanelState getFavoritePanelState() {
		return favoritePanelState;
	}

	ScrollStep getScrollStep() {
		return scrollStep;
	}

	ScrollStepTextField getScrollStepField() {
		return scrollStepField;
	}

	ImmutableRect2i getScrollStepArea() {
		return scrollStepArea;
	}

	IClientToggleState getToggleState() {
		return toggleState;
	}

	@Nullable GroupPanelDrag getGroupPanelDrag() {
		return groupPanelDrag;
	}

	void setGroupPanelDrag(@Nullable GroupPanelDrag groupPanelDrag) {
		this.groupPanelDrag = groupPanelDrag;
	}

	@Nullable BookmarkSortDragState getSortDragState() {
		return sortDragState;
	}

	void setSortDragState(@Nullable BookmarkSortDragState sortDragState) {
		this.sortDragState = sortDragState;
	}

	@Nullable FavoriteRecipeSortDragState getFavoriteSortDragState() {
		return favoriteSortDragState;
	}

	void setFavoriteSortDragState(@Nullable FavoriteRecipeSortDragState favoriteSortDragState) {
		this.favoriteSortDragState = favoriteSortDragState;
	}

	IngredientGridWithNavigation getContents() {
		return contents;
	}

	IngredientGridWithNavigation getFavoriteContents() {
		return favoriteContents;
	}

	BookmarkOverlayInputHandlers getInputHandlers() {
		return inputHandlers;
	}

	BookmarkOverlayRenderer getRenderer() {
		return renderer;
	}

	List<BookmarkPanelLayout.PanelSlot<IBookmark>> getPanelSlots() {
		return layout.getPanelSlots();
	}

	List<BookmarkPanelLayout.PanelSlot<IBookmark>> getProjectedPanelSlots() {
		return layout.getProjectedPanelSlots();
	}

	private List<BookmarkPanelLayout.RowSlot<IBookmark>> getGroupPanelRowSlots() {
		return layout.getGroupPanelRowSlots();
	}

	record FavoriteRecipeRowPanelSlot(FocusedRecipe recipe, ImmutableRect2i area) {
	}

	record FavoriteRecipeElementPanelSlot(FavoriteRecipeElement<?> element, ImmutableRect2i area) {
	}

	public static boolean shouldStartGroupPanelDrag(BookmarkHotkeyAction action) {
		return BookmarkOverlayInputHandlers.shouldStartGroupPanelDrag(action);
	}

	static void playClickSound() {
		JeiClientSoundUtil.playClickSound();
	}

	Optional<IBookmark> getBookmarkUnderMouse(double mouseX, double mouseY) {
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
			BookmarkOverlayInputHandlers.createGroupPanelHotkeyContext(slot.get()),
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

	Optional<GroupPanelSlot> getGroupPanelSlotUnderMouse(double mouseX, double mouseY) {
		return BookmarkPanelLayout.findRowUnderMouse(getGroupPanelRowSlots(), mouseX, mouseY, GROUP_PANEL_WIDTH)
			.map(this::toGroupPanelSlot);
	}

	Optional<FavoriteRecipeRowPanelSlot> getFavoriteRecipeRowPanelSlotUnderMouse(double mouseX, double mouseY) {
		return BookmarkPanelLayout.findRowUnderMouse(toFavoriteRecipeRowSlots(), mouseX, mouseY, GROUP_PANEL_WIDTH)
			.map(slot -> new FavoriteRecipeRowPanelSlot(slot.item(), slot.area()));
	}

	Optional<FavoriteRecipeElementPanelSlot> getFavoriteRecipeElementSlotUnderMouse(double mouseX, double mouseY) {
		return this.favoriteContents.getSlots()
			.filter(slot -> slot.getArea().contains(mouseX, mouseY))
			.map(slot -> slot.getOptionalElement()
				.filter(FavoriteRecipeElement.class::isInstance)
				.map(element -> new FavoriteRecipeElementPanelSlot((FavoriteRecipeElement<?>) element, slot.getArea())))
			.flatMap(Optional::stream)
			.findFirst();
	}

	List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> getFavoriteRecipeElementPanelSlots() {
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

	List<BookmarkPanelLayout.RowSlot<FocusedRecipe>> toFavoriteRecipeRowSlots() {
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

	ImmutableRect2i getDefaultGroupControlArea() {
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

	Optional<GroupPanelSlot> getClosestGroupPanelSlotAtY(double mouseY) {
		return BookmarkPanelLayout.findClosestRowAtY(getGroupPanelRowSlots(), mouseY)
			.map(this::toGroupPanelSlot);
	}

	static ImmutableRect2i getGroupPanelArea(ImmutableRect2i slotArea) {
		return BookmarkPanelLayout.getGroupPanelArea(slotArea, GROUP_PANEL_WIDTH);
	}

	GroupPanelSlot toGroupPanelSlot(BookmarkPanelLayout.RowSlot<IBookmark> slot) {
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

}
