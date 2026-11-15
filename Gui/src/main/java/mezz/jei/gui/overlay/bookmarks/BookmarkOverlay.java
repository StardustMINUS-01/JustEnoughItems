package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.gui.JeiGuiColors.GuiColor;
import mezz.jei.common.gui.JeiGuiColors;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.config.IngredientGridNavigationMode;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.MouseUtil;
import mezz.jei.common.input.handlers.InputGroup.Child;
import mezz.jei.common.input.handlers.InputGroup;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.common.util.ImmutablePoint2i;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.common.config.NavigationVisibility;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.DragSource;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.FocusedRecipeCandidate;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.input.InputArea;
import mezz.jei.gui.input.InputCommands;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.common.input.UserInput;
import mezz.jei.gui.overlay.GuiPropertiesCache;
import mezz.jei.gui.overlay.IScreenPropertiesUpdater;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryButtonController;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay;
import mezz.jei.gui.overlay.bookmarks.BookmarkGroupDropBridge.GroupDropAvailabilityProvider;
import mezz.jei.gui.overlay.bookmarks.BookmarkGroupDropBridge.GroupDropHandler;
import mezz.jei.gui.overlay.bookmarks.BookmarkGroupDropBridge.GroupDropHighlightProvider;
import mezz.jei.gui.overlay.bookmarks.BookmarkPanelProjection.GroupPanelSlot;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.overlay.ingredients.IngredientGridBackgroundRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientGridLayout;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public class BookmarkOverlay implements IRecipeFocusSource, IBookmarkOverlay {
	private static final int BORDER_MARGIN = 6;
	private static final int INNER_PADDING = 2;
	private static final int BUTTON_SIZE = 20;
	private static final int GROUP_PANEL_WIDTH = 7;
	private static final int GROUP_PANEL_HOVER_COLOR = 0x66555555;
	private static final int GROUP_NONE_COLOR = 0xFF666666;
	private static final int GROUP_CHAIN_COLOR = 0xFF4FA3FF;
	private static final int FAVORITE_RECIPE_ROW_COLOR = 0x6645DA75;
	private static final int GROUP_PLACEHOLDER_COLOR = 0x66222222;

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
	private static final int LOOKUP_HISTORY_BOTTOM_PADDING = BORDER_MARGIN;
	private static final int LOOKUP_HISTORY_PADDING_EXTRA = LOOKUP_HISTORY_BOTTOM_PADDING - INNER_PADDING;
	private static final int BUTTON_GAP = 2;
	private static final int BUTTON_ROW_WIDTH = BUTTON_SIZE * 2 + BUTTON_GAP;

	// input
	private final BookmarkDragManager bookmarkDragManager;
	private @Nullable GroupPanelDrag groupPanelDrag;
	private @Nullable BookmarkSortDragState sortDragState;
	private @Nullable FavoriteRecipeSortDragState favoriteSortDragState;

	// areas
	private final GuiPropertiesCache<Screen> guiPropertiesCache;

	// display elements
	private final IngredientGridWithNavigation contents;
	private final Optional<FavoriteRecipeOverlay> favorites;
	private final LookupHistoryOverlay lookupHistoryOverlay;
	private final IngredientGridBackgroundRenderer backgroundRenderer;
	private final IconButton bookmarkButton;
	private final Optional<IconButton> favoriteButton;
	private final IconButton historyButton;

	// data
	private final BookmarkList bookmarkList;
	private final IClientToggleState toggleState;
	private final IClientConfig clientConfig;
	private final IInternalKeyMappings keyBindings;
	private final ScrollStep scrollStep;
	private final ScrollStepTextField scrollStepField;
	private final ScrollStepFieldInputHandler scrollStepInput;
	private ImmutableRect2i scrollStepArea = ImmutableRect2i.EMPTY;
	private java.util.function.Supplier<ImmutableRect2i> quantityArea = () -> ImmutableRect2i.EMPTY;

	public void setQuantityAreaSupplier(java.util.function.Supplier<ImmutableRect2i> quantityArea) {
		this.quantityArea = quantityArea;
	}
	private final BookmarkPanelProjection layout;
	private final BookmarkOverlayRenderer renderer;
	private final BookmarkOverlayInputHandlers inputHandlers;
	private final BookmarkOverlayDragHandlers dragHandlers;
	private final BookmarkChapterNavigation chapterNavigation;
	private long defaultGroupBookmarksVersion = Long.MIN_VALUE;
	private boolean defaultGroupHasBookmarks;

	private final IIngredientGridConfig bookmarkListConfig;
	private final BookmarkPreviewTooltipController previewTooltipController;
	private boolean screenPropertiesDirty;

	// Keep the constructor used by OverlayHelper first for mixins that select <init> by name.
	public BookmarkOverlay(
		BookmarkList bookmarkList,
		RecipeTransferService recipeTransferService,
		IngredientGridWithNavigation contents,
		LookupHistoryOverlay lookupHistoryOverlay,
		IngredientGridBackgroundRenderer backgroundRenderer,
		IClientToggleState toggleState,
		IClientConfig clientConfig,
		IIngredientGridConfig bookmarkListConfig,
		IScreenHelper screenHelper,
		IInternalKeyMappings keyBindings,
		@Nullable Optional<FavoriteRecipeOverlay> favorites,
		@Nullable ScrollStep scrollStep
	) {
		this.bookmarkList = bookmarkList;
		this.favorites = favorites == null ? Optional.empty() : favorites;
		this.toggleState = toggleState;
		this.clientConfig = clientConfig;
		this.keyBindings = keyBindings;
		this.scrollStep = scrollStep == null ? new ScrollStep() : scrollStep;
		this.scrollStepField = new ScrollStepTextField(this.scrollStep);
		this.scrollStepInput = new ScrollStepFieldInputHandler(scrollStepField);
		this.bookmarkListConfig = bookmarkListConfig;
		this.contents = contents;
		this.layout = new BookmarkPanelProjection(bookmarkList, contents);
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
		this.chapterNavigation = new BookmarkChapterNavigation(this);
		contents.enableLeadingNavigationContent();
		this.lookupHistoryOverlay = lookupHistoryOverlay;
		this.backgroundRenderer = backgroundRenderer;
		this.guiPropertiesCache = new GuiPropertiesCache<>(
			screen -> screenHelper.getGuiProperties(screen)
				.orElse(null)
		);
		this.bookmarkButton = new IconButton(new BookmarkPanelButtonController(this, keyBindings));
		this.favoriteButton = this.favorites.map(favorite -> FavoriteRecipePanelButton.create(this, favorite.recipes()));
		this.historyButton = new IconButton(new LookupHistoryButtonController(clientConfig));
		this.bookmarkDragManager = new BookmarkDragManager(this);
		contents.setExtraHoveredIngredientSource(() -> getGroupDropHoverIngredient(MouseUtil.getX(), MouseUtil.getY()));
		this.previewTooltipController = new BookmarkPreviewTooltipController(this, recipeTransferService);
		bookmarkList.addSourceListChangedListener(() -> {
			layout.clearPanelSnapshot();
			toggleState.setBookmarkEnabled(!bookmarkList.isEmpty() || bookmarkList.getChapters().size() > 1);
			markScreenPropertiesDirty();
		});

		this.favorites.ifPresent(favorite -> favorite.recipes().addSourceListChangedListener(() -> {
			favorite.panelState().updateFavoritePanelAvailability(!favorite.recipes().isEmpty());
			markScreenPropertiesDirty();
		}));

		lookupHistoryOverlay.getLookupHistory().addSourceListChangedListener(this::markScreenPropertiesDirty);

		Internal.registerRuntimeListenerRemoval(clientConfig.lookupHistoryEnabled().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(clientConfig.maxLookupHistoryRows().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(clientConfig.maxLookupHistoryColumns().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(clientConfig.lookupHistoryDisplaySide().addListener(v -> markScreenPropertiesDirty()));
		addGridConfigListeners(bookmarkListConfig);
	}

	public BookmarkOverlay(
		BookmarkList bookmarkList,
		RecipeTransferService recipeTransferService,
		IngredientGridWithNavigation contents,
		LookupHistoryOverlay lookupHistoryOverlay,
		IngredientGridBackgroundRenderer backgroundRenderer,
		IClientToggleState toggleState,
		IClientConfig clientConfig,
		IIngredientGridConfig bookmarkListConfig,
		IScreenHelper screenHelper,
		IInternalKeyMappings keyBindings
	) {
		// Build the defaults in the target constructor so callbacks can still read the receiver here.
		this(bookmarkList, recipeTransferService, contents, lookupHistoryOverlay, backgroundRenderer,
			toggleState, clientConfig, bookmarkListConfig, screenHelper, keyBindings, null, null);
	}

	public BookmarkList getBookmarkList() {
		return bookmarkList;
	}

	public boolean isListDisplayed() {
		if (favorites.isPresent() && !favorites.get().panelState().isBookmarkPanelVisible()) {
			return false;
		}
		updateScreenPropertiesIfDirty();
		return toggleState.isBookmarkOverlayEnabled() &&
			guiPropertiesCache.hasValidScreen() &&
			contents.hasRoom() &&
			(!bookmarkList.isEmpty() || bookmarkList.getChapters().size() > 1);
	}

	public boolean isFavoritePanelDisplayed() {
		if (favorites.isEmpty()) {
			return false;
		}
		FavoriteRecipeOverlay favorite = favorites.get();
		return favorite.panelState().isFavoritePanelVisible() &&
			guiPropertiesCache.hasValidScreen() &&
			favorite.contents().hasRoom() &&
			!favorite.contents().isEmpty();
	}

	public boolean isFavoritePanelSelected() {
		return favorites.isPresent() && favorites.get().panelState().isFavoritePanelVisible();
	}

	public boolean hasBookmarkPanelRoom() {
		return contents.hasRoom();
	}

	public boolean hasFavoritePanelRoom() {
		return favorites.isPresent() && favorites.get().contents().hasRoom();
	}

	public boolean toggleBookmarkPanel(boolean simulate) {
		if (bookmarkList.isEmpty() && bookmarkList.getChapters().size() <= 1 || !hasBookmarkPanelRoom()) {
			return false;
		}
		if (!simulate) {
			if (isFavoritePanelSelected()) {
				getFavoritePanelState().showBookmarkPanel();
				toggleState.setBookmarkEnabled(true);
			} else if (isListDisplayed()) {
				toggleState.setBookmarkEnabled(false);
			} else {
				favorites.ifPresent(favorite -> favorite.panelState().showBookmarkPanel());
				toggleState.setBookmarkEnabled(true);
			}
		}
		return true;
	}

	public boolean toggleFavoritePanel(boolean simulate) {
		if (!hasFavoritePanelRoom() || getFavoriteContents().isEmpty()) {
			return false;
		}
		if (!simulate) {
			boolean wasFavoritePanelDisplayed = isFavoritePanelDisplayed();
			getFavoritePanelState().handleFavoritePanelButtonClick(false);
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
			return getFavoritePanelState().handleFavoritePanelButtonClick(true);
		}
		return true;
	}

	public void showFavoritePanel() {
		if (hasFavoritePanelRoom()) {
			getFavoritePanelState().showFavoritePanel();
		}
	}

	public void showBookmarkPanel() {
		if (hasBookmarkPanelRoom()) {
			favorites.ifPresent(favorite -> favorite.panelState().showBookmarkPanel());
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
			chapterNavigation.resetInput();
			dragHandlers.stopSortDrag();
			dragHandlers.stopFavoriteSortDrag();
			groupPanelDrag = null;
			this.contents.close();
			if (favorites.isPresent()) {
				favorites.get().contents().close();
			}
			this.lookupHistoryOverlay.close();
			return;
		}
		updateBounds(guiProperties, this.guiPropertiesCache.getGuiExclusionAreas());
	}

	private void updateBounds(IGuiProperties guiProperties, Set<ImmutableRect2i> guiExclusionAreas) {
		ImmutableRect2i displayArea = BookmarkOverlayLayout.calculateDisplayArea(guiProperties, guiExclusionAreas);
		ImmutablePoint2i mouseExclusionArea = this.guiPropertiesCache.getMouseExclusionArea();
		Set<ImmutableRect2i> contentGuiExclusionAreas = guiExclusionAreas;
		ImmutablePoint2i contentMouseExclusionArea = mouseExclusionArea;

		LayoutAreas layoutAreas = calculateLayoutAreas(
			displayArea,
			clientConfig.lookupHistoryEnabled().get() && lookupHistoryOverlay.isDisplayedOnThisSide(),
			lookupHistoryOverlay.getDisplayHeight()
		);
		IngredientGridNavigationMode navigationMode = bookmarkListConfig.navigationMode().get();
		NavigationVisibility navigationVisibility = bookmarkListConfig.navigationVisibility().get();
		// ENABLED page navigation already moves the whole grid below exclusions that block its
		// button strip. Scrollbar navigation has no equivalent strip, while AUTO_HIDE and DISABLED
		// page navigation may not show one, so use bookmark whole-row avoidance for those modes.
		if (shouldAvoidTopLeftExclusions(navigationMode, navigationVisibility)) {
			ImmutableRect2i contentsLayoutArea = avoidTopLeftExclusions(layoutAreas.contentsLayoutArea(), guiExclusionAreas);
			layoutAreas = new LayoutAreas(contentsLayoutArea, layoutAreas.historyArea(), layoutAreas.contentsBottomLimit());
			if (navigationMode.usesScrollbar() && clientConfig.smoothScrollingEnabled().get()) {
				contentGuiExclusionAreas = filterContentExclusionAreas(contentsLayoutArea, guiExclusionAreas);
				contentMouseExclusionArea = filterContentMouseExclusionPoint(contentsLayoutArea, mouseExclusionArea);
			}
		}
		this.contents.updateBounds(layoutAreas.contentsLayoutArea(), layoutAreas.contentsBottomLimit(), contentGuiExclusionAreas, contentMouseExclusionArea);
		this.contents.updateLayout(false);
		layoutAreas.historyArea().ifPresent(historyArea -> {
			this.lookupHistoryOverlay.updateBounds(historyArea, guiExclusionAreas, mouseExclusionArea);
			this.lookupHistoryOverlay.updateLayout();
			this.lookupHistoryOverlay.setResizeBounds(displayArea.cropBottom(BUTTON_SIZE + LOOKUP_HISTORY_BOTTOM_PADDING), VerticalAlignment.BOTTOM);
		});

		if (favorites.isPresent()) {
			IngredientGridWithNavigation favoriteContents = getFavoriteContents();
			favoriteContents.updateBounds(layoutAreas.contentsLayoutArea(), layoutAreas.contentsBottomLimit(), contentGuiExclusionAreas, contentMouseExclusionArea);
			favoriteContents.updateLayout(false);
		}

		ImmutableRect2i insetDisplayArea = displayArea.insetBy(BORDER_MARGIN);
		int buttonRowWidth = BUTTON_ROW_WIDTH + (favorites.isPresent() ? BUTTON_SIZE + BUTTON_GAP : 0);
		if (insetDisplayArea.width() < buttonRowWidth || insetDisplayArea.height() < BUTTON_SIZE) {
			bookmarkButton.updateBounds(ImmutableRect2i.EMPTY);
			if (favoriteButton.isPresent()) {
				favoriteButton.get().updateBounds(ImmutableRect2i.EMPTY);
			}
			historyButton.updateBounds(ImmutableRect2i.EMPTY);
		} else if (contents.hasRoom() && contents.getBackgroundArea().width() >= buttonRowWidth) {
			ImmutableRect2i contentsArea = this.contents.getBackgroundArea();
			ImmutableRect2i bookmarkButtonArea = displayArea
				.insetBy(BORDER_MARGIN)
				.matchWidthAndX(contentsArea)
				.keepBottom(BUTTON_SIZE)
				.keepLeft(BUTTON_SIZE);
			this.bookmarkButton.updateBounds(bookmarkButtonArea);
			updatePanelButtonBounds(bookmarkButtonArea);
		} else {
			ImmutableRect2i bookmarkButtonArea = displayArea
				.insetBy(BORDER_MARGIN)
				.keepBottom(BUTTON_SIZE)
				.keepLeft(BUTTON_SIZE);
			this.bookmarkButton.updateBounds(bookmarkButtonArea);
			updatePanelButtonBounds(bookmarkButtonArea);
		}
	}

	private void updatePanelButtonBounds(ImmutableRect2i bookmarkButtonArea) {
		ImmutableRect2i nextButtonArea = calculateFavoritePanelButtonArea(bookmarkButtonArea);
		if (favoriteButton.isPresent()) {
			favoriteButton.get().updateBounds(nextButtonArea);
		}
		historyButton.updateBounds(favorites.isPresent() ? calculateHistoryButtonArea(nextButtonArea) : nextButtonArea);
	}

	public boolean hasRoom() {
		updateScreenPropertiesIfDirty();
		return contents.hasRoom();
	}

	private void markScreenPropertiesDirty() {
		this.screenPropertiesDirty = true;
	}

	private void addGridConfigListeners(IIngredientGridConfig gridConfig) {
		Internal.registerRuntimeListenerRemoval(gridConfig.maxColumns().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.maxRows().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.backgroundStyle().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.layoutMode().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.navigationMode().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.horizontalAlignment().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.verticalAlignment().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.navigationVisibility().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.shrinkToFit().addListener(v -> markScreenPropertiesDirty()));
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

	public static ImmutableRect2i calculateFavoritePanelButtonArea(ImmutableRect2i bookmarkButtonArea) {
		return bookmarkButtonArea.moveRight(BUTTON_SIZE + INNER_PADDING);
	}

	public static ImmutableRect2i calculateHistoryButtonArea(ImmutableRect2i favoriteButtonArea) {
		return favoriteButtonArea.moveRight(BUTTON_SIZE + INNER_PADDING);
	}

	static boolean shouldAvoidTopLeftExclusions(
		IngredientGridNavigationMode navigationMode,
		NavigationVisibility navigationVisibility
	) {
		return navigationMode.usesScrollbar() || navigationVisibility != NavigationVisibility.ENABLED;
	}

	static Set<ImmutableRect2i> filterContentExclusionAreas(
		ImmutableRect2i area,
		Set<ImmutableRect2i> guiExclusionAreas
	) {
		Set<ImmutableRect2i> filteredExclusionAreas = null;
		for (ImmutableRect2i exclusion : guiExclusionAreas) {
			if (exclusion.getY() + exclusion.getHeight() <= area.getY()) {
				if (filteredExclusionAreas == null) {
					filteredExclusionAreas = new HashSet<>(guiExclusionAreas);
				}
				filteredExclusionAreas.remove(exclusion);
			}
		}
		return filteredExclusionAreas == null ? guiExclusionAreas : filteredExclusionAreas;
	}

	static @Nullable ImmutablePoint2i filterContentMouseExclusionPoint(
		ImmutableRect2i area,
		@Nullable ImmutablePoint2i mouseExclusionPoint
	) {
		return mouseExclusionPoint == null || mouseExclusionPoint.y() < area.y() ? null : mouseExclusionPoint;
	}

	/**
	 * Shifts the bookmark contents area below exclusion areas that meaningfully cover its
	 * top-left slot cell. Such areas (e.g. an FTB sidebar icon) would otherwise create a
	 * partial first row that breaks group borders; shifting the whole area keeps every row
	 * full and lets the page buttons and scrollbar follow automatically. The shift is
	 * aligned to whole grid rows, and tiny slivers keep blocking only the slot underneath.
	 *
	 * Used for scrollbar navigation and button navigation with AUTO_HIDE or DISABLED page
	 * buttons. With ENABLED page navigation, upstream handles the shift itself.
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

	public static LayoutAreas calculateLayoutAreas(ImmutableRect2i displayArea, boolean lookupHistoryOnSide, int historyDisplayHeight) {
		ImmutableRect2i contentsLayoutArea = displayArea
			.cropBottom(BUTTON_SIZE + INNER_PADDING)
			.cropLeft(GROUP_PANEL_WIDTH);
		Optional<ImmutableRect2i> historyArea = Optional.empty();
		OptionalInt contentsBottomLimit = OptionalInt.empty();
		if (lookupHistoryOnSide && historyDisplayHeight > 0) {
			ImmutableRect2i calculatedHistoryArea = displayArea
				.cropLeft(GROUP_PANEL_WIDTH)
				.cropBottom(BUTTON_SIZE + LOOKUP_HISTORY_BOTTOM_PADDING)
				.keepBottom(historyDisplayHeight);
			historyArea = Optional.of(calculatedHistoryArea);
			contentsBottomLimit = OptionalInt.of(calculatedHistoryArea.getY() - LOOKUP_HISTORY_PADDING_EXTRA);
			contentsLayoutArea = cropBottomTo(contentsLayoutArea, contentsBottomLimit.getAsInt());
		}
		return new LayoutAreas(contentsLayoutArea, historyArea, contentsBottomLimit);
	}

	private static ImmutableRect2i cropBottomTo(ImmutableRect2i area, int bottomY) {
		int cropAmount = getBottom(area) - bottomY;
		if (cropAmount <= 0) {
			return area;
		}
		return area.cropBottom(cropAmount);
	}

	private static int getBottom(ImmutableRect2i area) {
		return area.y() + area.height();
	}

	public void drawScreen(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		drawBackground(guiGraphics);
		drawForeground(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
	}

	public void drawBackground(GuiGraphics guiGraphics) {
		updateScreenPropertiesIfDirty();
		boolean contentsDisplayed = isListDisplayed();
		List<IngredientGridBackgroundRenderer.Panel> backgroundPanels = new ArrayList<>(2);
		if (contentsDisplayed && this.contents.isBackgroundEnabled()) {
			backgroundPanels.add(new IngredientGridBackgroundRenderer.Panel(
				this.contents.getBackgroundArea(),
				this.contents.getSlotBackgroundArea()
			));
		}
		if (isFavoritePanelDisplayed()) {
			IngredientGridWithNavigation favoriteContents = getFavoriteContents();
			if (favoriteContents.isBackgroundEnabled()) {
				backgroundPanels.add(new IngredientGridBackgroundRenderer.Panel(
					favoriteContents.getBackgroundArea(), favoriteContents.getSlotBackgroundArea()));
			}
		}
		boolean lookupHistoryDisplayed = guiPropertiesCache.hasValidScreen() &&
			toggleState.isOverlayEnabled() &&
			this.lookupHistoryOverlay.isListDisplayed();
		if (lookupHistoryDisplayed && this.lookupHistoryOverlay.isBackgroundEnabled()) {
			backgroundPanels.add(new IngredientGridBackgroundRenderer.Panel(
				this.lookupHistoryOverlay.getBackgroundArea(),
				this.lookupHistoryOverlay.getSlotBackgroundArea()
			));
		}
		this.backgroundRenderer.draw(
			guiGraphics,
			backgroundPanels,
			this.guiPropertiesCache.getGuiExclusionAreas()
		);
	}

	public void drawForeground(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		var mouse = mezz.jei.gui.input.GuiHoverUtil.backgroundMouse(chapterNavigation.isMenuHovered(mouseX, mouseY), mouseX, mouseY);
		// Keep the render arguments in their original local slots for overlay callbacks.
		int backgroundMouseX = mouse.x();
		int backgroundMouseY = mouse.y();
		if (isListDisplayed()) {
			dragHandlers.updateSortDrag(mouseX, mouseY);
			this.bookmarkDragManager.updateDrag(mouseX, mouseY);
			drawPageFlipEdgeHighlights(guiGraphics, mouseX, mouseY);
			this.contents.drawForeground(minecraft, guiGraphics, backgroundMouseX, backgroundMouseY, partialTicks);
			renderer.drawDefaultGroupControlIndicator(
				minecraft,
				guiGraphics,
				getDefaultGroupControlArea(),
				contents.getPageDelegate()
			);
			if (sortDragState != null) {
				sortDragState.drawTargetSlotOverlays(guiGraphics);
				sortDragState.drawSourceSlotOverlays(guiGraphics, getPanelSlots());
			}
			drawBookmarkGroupPanels(guiGraphics, backgroundMouseX, backgroundMouseY);
			if (groupPanelDrag != null) {
				groupPanelDrag.drawPreview(guiGraphics, mouseX, mouseY);
			}
		}
		if (isFavoritePanelDisplayed()) {
			dragHandlers.updateFavoriteSortDrag(mouseX, mouseY);
			getFavoriteContents().drawForeground(minecraft, guiGraphics, backgroundMouseX, backgroundMouseY, partialTicks);
			if (favoriteSortDragState != null) {
				favoriteSortDragState.drawTargetSlotOverlays(guiGraphics);
				favoriteSortDragState.drawSourceSlotOverlays(guiGraphics);
			}
			renderer.drawFavoriteRecipeRowPanels(guiGraphics, mouseX, mouseY, getFavoritePanelState());
		}
		if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.draw(minecraft, guiGraphics, backgroundMouseX, backgroundMouseY, partialTicks);
		}
		if (this.guiPropertiesCache.hasValidScreen()) {
			this.bookmarkButton.draw(guiGraphics, backgroundMouseX, backgroundMouseY, partialTicks);
			drawFavoriteButton(guiGraphics, backgroundMouseX, backgroundMouseY, partialTicks);
			this.historyButton.draw(guiGraphics, backgroundMouseX, backgroundMouseY, partialTicks);
			getScrollStepArea();
			this.scrollStepField.renderWidget(guiGraphics, backgroundMouseX, backgroundMouseY, partialTicks);
		}
		drawDragPreviews(guiGraphics, mouseX, mouseY);
	}

	// Keep the bookmark and history button calls directly in the foreground and tooltip methods.
	private void drawFavoriteButton(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		if (favoriteButton.isPresent()) {
			favoriteButton.get().draw(guiGraphics, mouseX, mouseY, partialTicks);
		}
	}

	private void drawBookmarkGroupPanels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!isSmoothScrolling()) {
			renderer.drawBookmarkGroupPanels(guiGraphics, mouseX, mouseY, groupPanelDrag, sortDragState);
			return;
		}
		ImmutableRect2i clipArea = calculateGroupPanelClipArea(this.contents.getIngredientGridArea());
		guiGraphics.enableScissor(
			clipArea.getX(),
			clipArea.getY(),
			clipArea.getX() + clipArea.getWidth(),
			clipArea.getY() + clipArea.getHeight()
		);
		try {
			renderer.drawBookmarkGroupPanels(guiGraphics, mouseX, mouseY, groupPanelDrag, sortDragState);
		} finally {
			guiGraphics.disableScissor();
		}
	}

	private void drawPageFlipEdgeHighlights(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!(this.bookmarkDragManager.isDragging() || sortDragState != null && sortDragState.isActive()) || !canFlipPage()) {
			return;
		}
		PageFlipHover.Direction hoveredDirection = getHoveredPageEdge(mouseX, mouseY);
		drawPageFlipEdgeHighlight(guiGraphics, getNextPageEdgeArea(), hoveredDirection == PageFlipHover.Direction.NEXT);
		drawPageFlipEdgeHighlight(guiGraphics, getBackPageEdgeArea(), hoveredDirection == PageFlipHover.Direction.PREVIOUS);
	}

	private static void drawPageFlipEdgeHighlight(GuiGraphics guiGraphics, ImmutableRect2i area, boolean hovered) {
		GuiColor color;
		if (hovered) {
			color = GuiColor.BOOKMARK_DRAG_PAGE_FLIP_HIGHLIGHT;
		} else {
			color = GuiColor.BOOKMARK_DRAG_PAGE_FLIP_HINT;
		}
		guiGraphics.fill(
			area.getX(),
			area.getY(),
			area.getX() + area.getWidth(),
			area.getY() + area.getHeight(),
			JeiGuiColors.getColor(color)
		);
	}

	/** Creates input handling for the bookmark preview. */
	public InputArea createTooltipInputArea() {
		return previewTooltipController.createInputArea();
	}

	/** Registered alongside the chapter layer by JeiInputSetup so text input owns its focus. */
	public InputArea createQuantityInputArea() {
		return InputArea.builder("Bookmark quantity", scrollStepInput)
			.controls(new InputGroup("Bookmark quantity", inputHandlers.new GroupInputHandler(), scrollStepInput))
			.blockUnhandledMouseInput().build();
	}

	public mezz.jei.common.input.IGuiInputLayer getChapterInputLayer() { return chapterNavigation; }

	boolean blocksChapterBackgroundHover(double x, double y) { return chapterNavigation.isMenuHovered(x, y); }

	Stream<PreviewSource> getPreviewSourcesUnderMouse(double mouseX, double mouseY) {
		if (chapterNavigation.isMenuHovered(mouseX, mouseY))
			return Stream.empty();
		Stream<PreviewSource> bookmarkSources = contents.getIngredientUnderMouse(mouseX, mouseY)
			.map(ingredient -> new PreviewSource(ingredient, bookmarkList, this::isListDisplayed));
		IIngredientGridSource lookupHistory = lookupHistoryOverlay.getLookupHistory();
		Stream<PreviewSource> lookupHistorySources = lookupHistoryOverlay.getIngredientUnderMouse(mouseX, mouseY)
			.map(ingredient -> new PreviewSource(ingredient, lookupHistory, lookupHistoryOverlay::isListDisplayed));
		return Stream.concat(bookmarkSources, lookupHistorySources);
	}

	record PreviewSource(
		IClickableIngredientInternal<?> ingredient,
		IIngredientGridSource owner,
		BooleanSupplier ownerDisplayed
	) {
		boolean isPresentAndVisible() {
			IElement<?> element = ingredient.getElement();
			return ownerDisplayed.getAsBoolean() &&
				element.isVisible() &&
				owner.containsElement(element);
		}
	}

	public void drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (chapterNavigation.isMenuHovered(mouseX, mouseY))
			return;
		if (sortDragState != null && sortDragState.isActive() || favoriteSortDragState != null && favoriteSortDragState.isActive())
			return;
		updateScreenPropertiesIfDirty();
		if (!this.bookmarkDragManager.drawDraggedItem(guiGraphics, mouseX, mouseY)) {
			if (isListDisplayed() && !previewTooltipController.isVisible()) {
				if (renderer.drawGroupHotkeyTooltip(guiGraphics, mouseX, mouseY)) {
					return;
				}
				this.contents.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
			}
			if (isFavoritePanelDisplayed()) {
				if (renderer.drawFavoriteRecipeRowHotkeyTooltip(guiGraphics, mouseX, mouseY)) {
					return;
				}
				getFavoriteContents().drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
			}
			if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
				this.lookupHistoryOverlay.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
			}
		}
		if (this.guiPropertiesCache.hasValidScreen()) {
			bookmarkButton.drawTooltips(guiGraphics, mouseX, mouseY);
			drawFavoriteButtonTooltips(guiGraphics, mouseX, mouseY);
			historyButton.drawTooltips(guiGraphics, mouseX, mouseY);
		}
	}

	private void drawFavoriteButtonTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (favoriteButton.isPresent()) {
			favoriteButton.get().drawTooltips(guiGraphics, mouseX, mouseY);
		}
	}

	public void tick() {
		this.bookmarkButton.tick();
		this.historyButton.tick();
		if (favoriteButton.isPresent()) {
			favoriteButton.get().tick();
		}
		if (isListDisplayed()) {
			this.contents.tick();
		}
		if (isFavoritePanelDisplayed()) {
			getFavoriteContents().tick();
		}
		if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.tick();
		}
	}

	public void drawDragPreviews(GuiGraphics graphics, int mouseX, int mouseY) {
		boolean bookmarks = sortDragState != null && sortDragState.isActive() && isListDisplayed();
		boolean favorites = favoriteSortDragState != null && favoriteSortDragState.isActive() && isFavoritePanelDisplayed();
		if (!bookmarks && !favorites)
			return;
		// Drag previews are foreground content, not tooltips suppressed by menu hit testing.
		graphics.pose().pushPose();
		try {
			graphics.pose().translate(0, 0, 400);
			if (bookmarks) {
				sortDragState.drawDraggedItems(graphics, mouseX, mouseY);
				renderer.drawFloatingGroupPanel(graphics, sortDragState, mouseX, mouseY);
			}
			if (favorites) {
				favoriteSortDragState.drawDraggedItems(graphics, mouseX, mouseY);
				renderer.drawFloatingFavoriteRecipeRowPanel(graphics, favoriteSortDragState, mouseX, mouseY);
			}
		} finally {
			graphics.pose().popPose();
		}
	}

	@Override
	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		if (chapterNavigation.isMenuHovered(mouseX, mouseY))
			return Stream.empty();
		updateScreenPropertiesIfDirty();
		if (isListDisplayed()) {
			return Stream.concat(this.contents.getIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getIngredientUnderMouse(mouseX, mouseY));
		}
		if (isFavoritePanelDisplayed()) {
			return Stream.concat(getFavoriteContents().getIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getIngredientUnderMouse(mouseX, mouseY));
		}
		if (this.lookupHistoryOverlay.isListDisplayed()) {
			return this.lookupHistoryOverlay.getIngredientUnderMouse(mouseX, mouseY);
		}
		return Stream.empty();
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		updateScreenPropertiesIfDirty();
		if (!this.guiPropertiesCache.hasValidScreen()) {
			return false;
		}
		if (this.bookmarkButton.isMouseOver(mouseX, mouseY) || this.historyButton.isMouseOver(mouseX, mouseY)) {
			return true;
		}
		if (isListDisplayed() && this.contents.isMouseOver(mouseX, mouseY)) {
			return true;
		}
		if (favoriteButton.isPresent() && favoriteButton.get().isMouseOver(mouseX, mouseY) || getScrollStepArea().contains(mouseX, mouseY)) {
			return true;
		}
		if (isFavoritePanelDisplayed() && (getFavoriteContents().isMouseOver(mouseX, mouseY) ||
			getFavoriteRecipeRowPanelSlotUnderMouse(mouseX, mouseY).isPresent())
		) {
			return true;
		}
		if (isListDisplayed() && getGroupPanelSlotUnderMouse(mouseX, mouseY).isPresent()) {
			return true;
		}
		return toggleState.isOverlayEnabled() && this.lookupHistoryOverlay.isMouseOver(mouseX, mouseY);
	}

	@Override
	public Stream<IDraggableIngredientInternal<?>> getDraggableIngredientUnderMouse(double mouseX, double mouseY) {
		if (chapterNavigation.isMenuHovered(mouseX, mouseY))
			return Stream.empty();
		updateScreenPropertiesIfDirty();
		if (isListDisplayed()) {
			return Stream.concat(this.contents.getDraggableIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getDraggableIngredientUnderMouse(mouseX, mouseY));
		}
		if (isFavoritePanelDisplayed()) {
			return Stream.concat(getFavoriteContents().getDraggableIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getDraggableIngredientUnderMouse(mouseX, mouseY));
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

	/** Creates this overlay's controls, shortcuts, and ingredient drag sources. */
	public InputArea createInputArea(IInternalKeyMappings keys) {
		InputCommands commands = new InputCommands();
		registerInputCommands(commands, keys);
		return InputArea.builder("Bookmarks", this)
			.blockUnhandledMouseInput()
			.controls(createInputHandler())
			.commands(commands)
			.dragSources(createDragSources())
			.build();
	}

	private void registerInputCommands(InputCommands commands, IInternalKeyMappings keys) {
		contents.registerInputCommands(commands, keys, this::isListDisplayed);
		favorites.ifPresent(favorite -> favorite.contents().registerInputCommands(commands, keys, this::isFavoritePanelDisplayed));
		lookupHistoryOverlay.registerInputCommands(commands, keys, this::isHistoryDisplayed);
	}

	private boolean isHistoryDisplayed() {
		updateScreenPropertiesIfDirty();
		return guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled() && lookupHistoryOverlay.isListDisplayed();
	}

	private IInputTarget createInputHandler() {
		List<Child> children = new ArrayList<>();
		children.add(new Child(this::isHistoryDisplayed, lookupHistoryOverlay.getResizeInputHandler()));
		children.add(new Child(this::isListDisplayed, contents.getResizeInputHandler()));
		favorites.ifPresent(favorite -> children.add(new Child(this::isFavoritePanelDisplayed, favorite.contents().getResizeInputHandler())));
		children.add(new Child(this::isListDisplayed, inputHandlers.new RecipeCollapseInputHandler()));
		favorites.ifPresent(favorite -> children.add(new Child(this::isFavoritePanelDisplayed, inputHandlers.new FavoriteRecipeRowInputHandler())));
		children.add(new Child(guiPropertiesCache::hasValidScreen, inputHandlers.new GroupInputHandler()));
		children.add(new Child(guiPropertiesCache::hasValidScreen, scrollStepInput));
		children.add(new Child(this::isListDisplayed, contents.createDeleteItemInputHandler()));
		favorites.ifPresent(favorite -> children.add(new Child(this::isFavoritePanelDisplayed, favorite.contents().createDeleteItemInputHandler())));
		children.add(new Child(this::isListDisplayed, contents.createInputHandler()));
		favorites.ifPresent(favorite -> children.add(new Child(this::isFavoritePanelDisplayed, favorite.contents().createInputHandler())));
		children.add(new Child(guiPropertiesCache::hasValidScreen, bookmarkButton.createInputHandler()));
		favoriteButton.ifPresent(button -> children.add(new Child(guiPropertiesCache::hasValidScreen, button.createInputHandler())));
		children.add(new Child(guiPropertiesCache::hasValidScreen, historyButton.createInputHandler()));
		children.add(new Child(this::isHistoryDisplayed, lookupHistoryOverlay.createInputHandler()));
		return new InputGroup("BookmarkOverlay", children.toArray(Child[]::new));
	}

	/** Returns whether a bookmark drag is pending or active. */
	public boolean hasBookmarkDrag() {
		return bookmarkDragManager.hasDrag() || sortDragState != null || favoriteSortDragState != null || groupPanelDrag != null;
	}

	private List<DragSource> createDragSources() {
		List<DragSource> sources = new ArrayList<>();
		favorites.ifPresent(favorite -> {
			sources.add(new DragSource(this::isFavoritePanelDisplayed, dragHandlers::beginFavoriteSortDrag));
			sources.add(new DragSource(this::isFavoritePanelDisplayed, favorite.contents()::beginDrag));
		});
		sources.add(new DragSource(this::isListDisplayed, dragHandlers::beginSortDrag));
		sources.add(new DragSource(() -> isListDisplayed() && groupPanelDrag == null, contents::beginDrag));
		sources.add(new DragSource(this::isHistoryDisplayed, lookupHistoryOverlay::beginDrag));
		sources.add(new DragSource(this::isListDisplayed, dragHandlers::beginPlainSortDrag));
		return List.copyOf(sources);
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
			getFavoriteContents().drawOnForeground(guiGraphics, mouseX, mouseY);
		}
		this.lookupHistoryOverlay.drawOnForeground(guiGraphics, mouseX, mouseY);
	}

	List<GroupPanelSlot> getGroupPanelSlots() {
		return layout.getGroupPanelSlots();
	}

	BookmarkPanelProjection.PanelSnapshot getGroupPanelSnapshotForRendering() {
		return layout.getPanelSnapshotForRendering();
	}

	boolean canStartGroupDrop(int groupId) {
		return BookmarkGroupDropBridge.canStartGroupDrop(bookmarkList, groupId);
	}

	private Optional<ITypedIngredient<?>> getGroupDropHoverIngredient(double mouseX, double mouseY) {
		return BookmarkGroupDropBridge.getGroupDropHoverIngredient(bookmarkList, getGroupPanelSlotUnderMouse(mouseX, mouseY));
	}

	IInternalKeyMappings getKeyBindings() {
		return keyBindings;
	}

	FavoriteRecipeStore getFavoriteRecipes() {
		return favorites.orElseThrow().recipes();
	}

	FavoriteRecipePanelState getFavoritePanelState() {
		return favorites.orElseThrow().panelState();
	}

	public ScrollStep getScrollStep() {
		return scrollStep;
	}

	ScrollStepTextField getScrollStepField() {
		return scrollStepField;
	}

	ImmutableRect2i getScrollStepArea() {
		ImmutableRect2i area = quantityArea.get();
		if (!area.equals(scrollStepArea)) {
			scrollStepArea = area;
			scrollStepField.updateBounds(area);
		}
		return scrollStepArea;
	}

	IClientToggleState getToggleState() {
		return toggleState;
	}

	@Nullable
	GroupPanelDrag getGroupPanelDrag() {
		return groupPanelDrag;
	}

	void setGroupPanelDrag(@Nullable GroupPanelDrag groupPanelDrag) {
		this.groupPanelDrag = groupPanelDrag;
	}

	@Nullable
	BookmarkSortDragState getSortDragState() {
		return sortDragState;
	}

	void setSortDragState(@Nullable BookmarkSortDragState sortDragState) {
		this.sortDragState = sortDragState;
	}

	@Nullable
	FavoriteRecipeSortDragState getFavoriteSortDragState() {
		return favoriteSortDragState;
	}

	void setFavoriteSortDragState(@Nullable FavoriteRecipeSortDragState favoriteSortDragState) {
		this.favoriteSortDragState = favoriteSortDragState;
	}

	IngredientGridWithNavigation getContents() {
		return contents;
	}

	IngredientGridWithNavigation getFavoriteContents() {
		return favorites.orElseThrow().contents();
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
		if (favorites.isEmpty()) {
			return Optional.empty();
		}
		return getFavoriteContents().getSlots()
			.filter(slot -> slot.getArea().contains(mouseX, mouseY))
			.map(IngredientListSlot::getOptionalElement)
			.flatMap(Optional::stream)
			.filter(FavoriteRecipeElement.class::isInstance)
			.map(FavoriteRecipeElement.class::cast)
			.map(FavoriteRecipeElement::getFocusedRecipe)
			.findFirst();
	}

	private Optional<Integer> getGroupIdUnderMouse(double mouseX, double mouseY) {
		return this.contents.getSlots()
			.filter(slot -> slot.getArea().contains(mouseX, mouseY))
			.map(IngredientListSlot::getOptionalElement)
			.flatMap(Optional::stream)
			.map(IElement::getBookmark)
			.flatMap(Optional::stream)
			.map(bookmarkList::getBookmarkGroupId)
			.findFirst();
	}

	private Optional<Integer> getGroupIdUnderMouseGroupPanel(double mouseX, double mouseY) {
		return getGroupPanelSlotUnderMouse(mouseX, mouseY)
			.map(GroupPanelSlot::groupId);
	}

	public Optional<Integer> getPullGroupIdUnderMouse(double mouseX, double mouseY) {
		if (hasDefaultGroupBookmarks() && getDefaultGroupControlArea().contains(mouseX, mouseY)) {
			return Optional.of(BookmarkGroupManager.DEFAULT_GROUP_ID);
		}
		return getGroupIdUnderMouseGroupPanel(mouseX, mouseY)
			.or(() -> getGroupIdUnderMouse(mouseX, mouseY));
	}

	public Optional<Integer> getPatternEncodeGroupIdUnderMouse(double mouseX, double mouseY) {
		if (hasDefaultGroupBookmarks() && getDefaultGroupControlArea().contains(mouseX, mouseY)) {
			return Optional.of(BookmarkGroupManager.DEFAULT_GROUP_ID);
		}
		return getGroupIdUnderMouseGroupPanel(mouseX, mouseY);
	}

	public boolean removeGroupUnderMouseGroupPanel(UserInput input) {
		Optional<GroupPanelSlot> slot = getGroupPanelSlotUnderMouse(input.getMouseX(), input.getMouseY())
			.filter(groupPanelSlot -> !(groupPanelSlot.groupId() == BookmarkGroupManager.DEFAULT_GROUP_ID));
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
		if (chapterNavigation.isMenuHovered(mouseX, mouseY))
			return Optional.empty();
		if (!isMouseOverVisibleGroupPanelArea(mouseX, mouseY)) {
			return Optional.empty();
		}
		return BookmarkPanelLayout.findRowUnderMouse(getGroupPanelRowSlots(), mouseX, mouseY, GROUP_PANEL_WIDTH)
			.map(this::toGroupPanelSlot);
	}

	boolean isMouseOverVisibleGroupPanelArea(double mouseX, double mouseY) {
		return !isSmoothScrolling() || calculateGroupPanelClipArea(this.contents.getIngredientGridArea()).contains(mouseX, mouseY);
	}

	Optional<FavoriteRecipeRowPanelSlot> getFavoriteRecipeRowPanelSlotUnderMouse(double mouseX, double mouseY) {
		return BookmarkPanelLayout.findRowUnderMouse(toFavoriteRecipeRowSlots(), mouseX, mouseY, GROUP_PANEL_WIDTH)
			.map(slot -> new FavoriteRecipeRowPanelSlot(slot.item(), slot.area()));
	}

	Optional<FavoriteRecipeElementPanelSlot> getFavoriteRecipeElementSlotUnderMouse(double mouseX, double mouseY) {
		if (favorites.isEmpty()) {
			return Optional.empty();
		}
		return getFavoriteContents().getSlots()
			.filter(slot -> slot.getArea().contains(mouseX, mouseY))
			.map(slot -> slot.getOptionalElement()
				.filter(FavoriteRecipeElement.class::isInstance)
				.map(element -> new FavoriteRecipeElementPanelSlot((FavoriteRecipeElement<?>) element, slot.getArea())))
			.flatMap(Optional::stream)
			.findFirst();
	}

	List<BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>> getFavoriteRecipeElementPanelSlots() {
		if (favorites.isEmpty()) {
			return List.of();
		}
		List<FavoriteRecipeElementPanelSlot> elementSlots = getFavoriteContents().getSlots()
			.map(slot -> slot.getOptionalElement()
				.filter(FavoriteRecipeElement.class::isInstance)
				.map(element -> (FavoriteRecipeElement<?>) element)
				.map(element -> new FavoriteRecipeElementPanelSlot(element, slot.getArea())))
			.flatMap(Optional::stream)
			.toList();
		Map<FocusedRecipe, Integer> rowIds = new HashMap<>();
		return elementSlots.stream()
			.map(slot -> new BookmarkPanelLayout.PanelSlot<FavoriteRecipeElement<?>>(
				slot.element(),
				rowIds.computeIfAbsent(slot.element().getFocusedRecipe(), recipe -> rowIds.size() + 1),
				slot.area(),
				false
			))
			.toList();
	}

	List<BookmarkPanelLayout.RowSlot<FocusedRecipe>> toFavoriteRecipeRowSlots() {
		if (favorites.isEmpty() || getFavoritePanelState().displayMode() != FavoriteRecipePanelState.DisplayMode.RECIPE_ROWS) {
			return List.of();
		}
		List<BookmarkPanelLayout.PanelSlot<FocusedRecipe>> panelSlots = getFavoriteRecipeElementPanelSlots().stream()
			.filter(slot -> slot.item().isVisible())
			.map(slot -> new BookmarkPanelLayout.PanelSlot<>(slot.item().getFocusedRecipe(), slot.groupId(), slot.area(), false))
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
		ImmutableRect2i chapterArea = contents.getLeadingNavigationArea();
		if (!chapterArea.isEmpty())
			x = chapterArea.x() + chapterArea.width() + 2;
		int width = next.getX() - x;
		if (width <= 0) {
			return ImmutableRect2i.EMPTY;
		}
		return new ImmutableRect2i(x, back.getY(), width, back.getHeight());
	}

	boolean hasDefaultGroupBookmarks() {
		long bookmarkVersion = bookmarkList.getChangeVersion();
		if (defaultGroupBookmarksVersion != bookmarkVersion) {
			defaultGroupBookmarksVersion = bookmarkVersion;
			defaultGroupHasBookmarks = bookmarkList.getBookmarks().stream()
				.anyMatch(bookmark -> (bookmarkList.getBookmarkGroupId(bookmark) == BookmarkGroupManager.DEFAULT_GROUP_ID));
		}
		return defaultGroupHasBookmarks;
	}

	Optional<GroupPanelSlot> getClosestGroupPanelSlotAtY(double mouseY) {
		return BookmarkPanelLayout.findClosestRowAtY(getGroupPanelRowSlots(), mouseY)
			.map(this::toGroupPanelSlot);
	}

	static ImmutableRect2i getGroupPanelArea(ImmutableRect2i slotArea) {
		return BookmarkPanelLayout.getGroupPanelArea(slotArea, GROUP_PANEL_WIDTH);
	}

	static ImmutableRect2i calculateGroupPanelClipArea(ImmutableRect2i ingredientGridArea) {
		return new ImmutableRect2i(
			ingredientGridArea.getX() - GROUP_PANEL_WIDTH,
			ingredientGridArea.getY(),
			GROUP_PANEL_WIDTH,
			ingredientGridArea.getHeight()
		);
	}

	boolean isSmoothScrolling() {
		return this.bookmarkListConfig.navigationMode().get().usesScrollbar() && this.clientConfig.smoothScrollingEnabled().get();
	}

	GroupPanelSlot toGroupPanelSlot(BookmarkPanelLayout.RowSlot<IBookmark> slot) {
		return new GroupPanelSlot(slot.item(), slot.groupId(), slot.area());
	}

	public List<BookmarkDragTarget> createBookmarkDragTargets(IBookmark draggedBookmark) {
		updateScreenPropertiesIfDirty();
		List<IElement<?>> elements = this.bookmarkList.getElements();
		List<BookmarkDragTarget> targets = BookmarkDragTarget.createSlotTargets(this.contents.getAllSlots(), elements, draggedBookmark);
		List<IElement<?>> pageElements = this.contents.getPageElements();
		if (pageElements.isEmpty()) {
			return targets;
		}

		// Use the current page's range, including hidden bookmarks, even after navigating during a drag.
		int firstIndex = elements.indexOf(pageElements.getFirst());
		int lastIndex = elements.indexOf(pageElements.getLast());
		if (canFlipPage()) {
			targets.add(new BookmarkDragTarget(this.contents.getNextPageButtonArea(), (lastIndex + 1) % elements.size()));
			targets.add(new BookmarkDragTarget(this.contents.getBackButtonArea(), Math.floorMod(firstIndex - 1, elements.size())));
		}

		// Trailing empty slots and background padding place the bookmark at the end of this page.
		targets.add(new BookmarkDragTarget(this.contents.getSlotBackgroundArea(), lastIndex));
		return targets;
	}

	public void moveBookmark(IBookmark bookmark, int index) {
		this.bookmarkList.moveBookmark(bookmark, index);
		// Keep the dropped bookmark visible when its visibility and the slot under the mouse pointer are restored.
		this.contents.setPageAnchorElement(bookmark.getElement());
	}

	public IPaged getPageDelegate() {
		return this.contents.getPageDelegate();
	}

	public void setPageButtonsForcePressed(boolean nextButton, boolean backButton) {
		this.contents.setPageButtonsForcePressed(nextButton, backButton);
	}

	private ImmutableRect2i getNextPageEdgeArea() {
		return this.contents.getSlotBackgroundArea()
			.keepRight(IngredientGridLayout.INGREDIENT_WIDTH / 2);
	}

	private ImmutableRect2i getBackPageEdgeArea() {
		return this.contents.getSlotBackgroundArea()
			.keepLeft(IngredientGridLayout.INGREDIENT_WIDTH / 2);
	}

	PageFlipHover.@Nullable Direction getHoveredPageEdge(double mouseX, double mouseY) {
		if (!canFlipPage()) {
			return null;
		}
		if (MathUtil.contains(getNextPageEdgeArea(), mouseX, mouseY)) {
			return PageFlipHover.Direction.NEXT;
		}
		if (MathUtil.contains(getBackPageEdgeArea(), mouseX, mouseY)) {
			return PageFlipHover.Direction.PREVIOUS;
		}
		return null;
	}

	private boolean canFlipPage() {
		return !this.bookmarkListConfig.navigationMode().get().usesScrollbar() &&
			getPageDelegate().getPageCount() > 1;
	}

	void scrollDuringDrag(BookmarkDragScroll dragScroll, double mouseX, double mouseY) {
		double pixels = dragScroll.update(
			this.contents.getSlotBackgroundArea(),
			this.bookmarkListConfig.navigationMode().get().usesScrollbar(),
			this.clientConfig.smoothScrollingEnabled().get(),
			mouseX,
			mouseY
		);
		if (pixels != 0) {
			this.contents.scrollByPixels(pixels);
		}
	}

	public boolean isBookmarkElementUnderMouse(IElement<?> element, double mouseX, double mouseY) {
		return isListDisplayed() &&
			element.isVisible() &&
			bookmarkList.containsElement(element) &&
			contents.getIngredientUnderMouse(mouseX, mouseY)
				.anyMatch(ingredient -> ingredient.getElement() == element);
	}
}
