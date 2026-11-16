package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.input.UserInput;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiGuiColors.GuiColor;
import mezz.jei.common.gui.JeiGuiColors;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.handlers.InputGroup.Child;
import mezz.jei.common.input.handlers.InputGroup;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.common.util.ImmutablePoint2i;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkMoveSelection;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
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
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.common.input.MouseUtil;
import mezz.jei.gui.overlay.GuiPropertiesCache;
import mezz.jei.gui.overlay.IScreenPropertiesUpdater;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlayLayout.GroupPanelSlot;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryButtonController;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.ProjectedBookmarkElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import mezz.jei.gui.input.InputArea;
import mezz.jei.gui.input.InputCommands;
import mezz.jei.gui.overlay.ingredients.IngredientGridBackgroundRenderer;
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
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public class BookmarkOverlay implements IRecipeFocusSource, IBookmarkOverlay {
	private static final Logger LOGGER = LogManager.getLogger();
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

	/**
	 * @deprecated use {@link BookmarkGroupDropBridge.GroupDropHandler}; kept for 1.20.1 API compatibility.
	 */
	@Deprecated
	@FunctionalInterface
	public interface GroupDropHandler {
		boolean dropGroup(List<ITypedIngredient<?>> ingredients, double mouseX, double mouseY);
	}

	/**
	 * @deprecated use {@link BookmarkGroupDropBridge.GroupDropHighlightProvider}; kept for 1.20.1 API compatibility.
	 */
	@Deprecated
	@FunctionalInterface
	public interface GroupDropHighlightProvider {
		List<Rect2i> getDropAreas(List<ITypedIngredient<?>> ingredients);
	}

	public static void setGroupDropHandler(@Nullable GroupDropHandler groupDropHandler) {
		BookmarkGroupDropBridge.setGroupDropHandler(groupDropHandler == null ? null : groupDropHandler::dropGroup);
	}

	public static void setGroupDropHighlightProvider(@Nullable GroupDropHighlightProvider groupDropHighlightProvider) {
		BookmarkGroupDropBridge.setGroupDropHighlightProvider(groupDropHighlightProvider == null ? null : groupDropHighlightProvider::getDropAreas);
	}

	public static void setGroupDropAvailabilityProvider(@Nullable BookmarkGroupDropBridge.GroupDropAvailabilityProvider groupDropAvailabilityProvider) {
		BookmarkGroupDropBridge.setGroupDropAvailabilityProvider(groupDropAvailabilityProvider);
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
	private final IngredientGridWithNavigation favoriteContents;
	private final LookupHistoryOverlay lookupHistoryOverlay;
	private final IngredientGridBackgroundRenderer backgroundRenderer;
	private final IconButton bookmarkButton;
	private final IconButton favoriteButton;
	private final IconButton historyButton;

	// data
	private final BookmarkList bookmarkList;
	private final FavoriteRecipeStore favoriteRecipes;
	private final FavoriteRecipePanelState favoritePanelState;
	private final IClientToggleState toggleState;
	private final IClientConfig clientConfig;
	private final IIngredientGridConfig bookmarkListConfig;
	private final BookmarkPreviewTooltipController previewTooltipController;
	private final IIngredientGridConfig gridConfig;
	private final IInternalKeyMappings keyBindings;
	private final ScrollStep scrollStep;
	private final ScrollStepTextField scrollStepField;
	private final ScrollStepFieldInputHandler scrollStepInput;
	private java.util.function.Supplier<mezz.jei.common.util.ImmutableRect2i> quantityArea = () -> mezz.jei.common.util.ImmutableRect2i.EMPTY;

	public void setQuantityAreaSupplier(java.util.function.Supplier<mezz.jei.common.util.ImmutableRect2i> quantityArea) {
		this.quantityArea = quantityArea;
	}
	private final BookmarkOverlayLayout layout;
	private final BookmarkOverlayRenderer renderer;
	private final BookmarkOverlayInputHandlers inputHandlers;
	private final BookmarkOverlayDragHandlers dragHandlers;
	private ImmutableRect2i scrollStepArea = ImmutableRect2i.EMPTY;
	private long defaultGroupBookmarksVersion = Long.MIN_VALUE;
	private boolean defaultGroupHasBookmarks;

	private boolean screenPropertiesDirty;

	public BookmarkOverlay(
		BookmarkList bookmarkList,
		RecipeTransferService recipeTransferService,
		IngredientGridWithNavigation contents,
		FavoriteRecipeStore favoriteRecipes,
		FavoriteRecipePanelState favoritePanelState,
		IngredientGridWithNavigation favoriteContents,
		LookupHistoryOverlay lookupHistoryOverlay,
		IngredientGridBackgroundRenderer backgroundRenderer,
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
		this.gridConfig = bookmarkListConfig;
		this.keyBindings = keyBindings;
		this.scrollStep = scrollStep;
		this.scrollStepField = new ScrollStepTextField(scrollStep);
		this.scrollStepInput = new ScrollStepFieldInputHandler(scrollStepField);
		this.bookmarkListConfig = bookmarkListConfig;
		this.contents = contents;
		this.favoriteContents = favoriteContents;
		this.lookupHistoryOverlay = lookupHistoryOverlay;
		this.backgroundRenderer = backgroundRenderer;
		this.guiPropertiesCache = new GuiPropertiesCache<>(
			screen -> screenHelper.getGuiProperties(screen)
				.orElse(null)
		);
		this.layout = new BookmarkOverlayLayout(bookmarkList, contents);
		this.bookmarkButton = new IconButton(new BookmarkPanelButtonController(this, keyBindings));
		this.favoriteButton = FavoriteRecipePanelButton.create(this, favoriteRecipes);
		this.historyButton = new IconButton(new LookupHistoryButtonController(clientConfig));
		this.bookmarkDragManager = new BookmarkDragManager(this);
		this.previewTooltipController = new BookmarkPreviewTooltipController(this, recipeTransferService);
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
		contents.setExtraHoveredIngredientSource(() -> getGroupDropHoverIngredient(MouseUtil.getX(), MouseUtil.getY()));
		bookmarkList.addSourceListChangedListener(() -> {
			layout.clearPanelSnapshot();
			toggleState.setBookmarkEnabled(!bookmarkList.isEmpty());
			markScreenPropertiesDirty();
		});
		lookupHistoryOverlay.getLookupHistory().addSourceListChangedListener(this::markScreenPropertiesDirty);

		favoriteRecipes.addSourceListChangedListener(() -> {
			favoritePanelState.updateFavoritePanelAvailability(!favoriteRecipes.isEmpty());
			markScreenPropertiesDirty();
		});

		Internal.registerRuntimeListenerRemoval(clientConfig.lookupHistoryEnabled().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(clientConfig.maxLookupHistoryRows().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(clientConfig.lookupHistoryDisplaySide().addListener(v -> markScreenPropertiesDirty()));
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
		boolean visible = favoritePanelState.isFavoritePanelVisible();
		boolean validScreen = guiPropertiesCache.hasValidScreen();
		boolean hasRoom = favoriteContents.hasRoom();
		boolean notEmpty = !favoriteContents.isEmpty();
		return visible && validScreen && hasRoom && notEmpty;
	}

	public boolean hasKeyboardFocus() {
		return scrollStepField.isFocused();
	}

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
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[FavTree] toggleBookmarkPanel simulate={} listSize={} hasRoom={} isFavSelected={} isListDisplayed={} bookmarkEnabled={}",
				simulate, bookmarkList.getBookmarks().size(), hasBookmarkPanelRoom(), isFavoritePanelSelected(), isListDisplayed(), toggleState.isBookmarkOverlayEnabled());
		}
		if (bookmarkList.isEmpty() || !hasBookmarkPanelRoom()) {
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[FavTree] toggleBookmarkPanel REJECT listEmpty={} noRoom={}",
					bookmarkList.isEmpty(), !hasBookmarkPanelRoom());
			}
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
		Set<ImmutableRect2i> guiExclusionAreas = this.guiPropertiesCache.getGuiExclusionAreas();
		ImmutableRect2i displayArea = BookmarkOverlayLayout.calculateDisplayArea(guiProperties, guiExclusionAreas);
		ImmutablePoint2i mouseExclusionArea = this.guiPropertiesCache.getMouseExclusionArea();

		int historyHeight = clientConfig.isLookupHistoryEnabled() && lookupHistoryOverlay.isDisplayedOnThisSide()
			? lookupHistoryOverlay.getDisplayHeight()
			: 0;
		LayoutAreas layoutAreas = calculateLayoutAreas(displayArea, historyHeight);
		// Ported from JEI 1.21.1 (3adfca8d2): shift the contents area below exclusions that
		// meaningfully cover its top-left slot cell so group borders never split a partial row.
		ImmutableRect2i contentsLayoutArea = avoidTopLeftExclusions(layoutAreas.contentsLayoutArea(), guiExclusionAreas);
		layoutAreas = new LayoutAreas(contentsLayoutArea, layoutAreas.historyArea(), layoutAreas.contentsBottomLimit());
		this.contents.updateBounds(contentsLayoutArea, layoutAreas.contentsBottomLimit(), guiExclusionAreas, mouseExclusionArea);
		this.contents.updateLayout(false);

		this.favoriteContents.updateBounds(contentsLayoutArea, layoutAreas.contentsBottomLimit(), guiExclusionAreas, mouseExclusionArea);
		this.favoriteContents.updateLayout(false);

		layoutAreas.historyArea().ifPresent(area -> {
			this.lookupHistoryOverlay.updateBounds(area, guiExclusionAreas, mouseExclusionArea);
			this.lookupHistoryOverlay.updateLayout();
			ImmutableRect2i resizeArea = displayArea.cropLeft(GROUP_PANEL_WIDTH)
				.cropBottom(BUTTON_SIZE + LOOKUP_HISTORY_BOTTOM_PADDING);
			this.lookupHistoryOverlay.setResizeBounds(resizeArea, VerticalAlignment.BOTTOM);
		});

		ImmutableRect2i insetDisplayArea = displayArea.insetBy(BORDER_MARGIN);
		if (insetDisplayArea.getWidth() < BUTTON_ROW_WIDTH || insetDisplayArea.getHeight() < BUTTON_SIZE) {
			this.bookmarkButton.updateBounds(ImmutableRect2i.EMPTY);
			this.historyButton.updateBounds(ImmutableRect2i.EMPTY);
		} else if (contents.hasRoom() && this.contents.getBackgroundArea().getWidth() >= BUTTON_ROW_WIDTH) {
			ImmutableRect2i contentsArea = this.contents.getBackgroundArea();
			ImmutableRect2i bookmarkButtonArea = insetDisplayArea
				.matchWidthAndX(contentsArea)
				.keepBottom(BUTTON_SIZE)
				.keepLeft(BUTTON_SIZE);
			this.bookmarkButton.updateBounds(bookmarkButtonArea);
			ImmutableRect2i favoriteButtonArea = calculateFavoritePanelButtonArea(bookmarkButtonArea);
			this.favoriteButton.updateBounds(favoriteButtonArea);
			ImmutableRect2i historyButtonArea = calculateHistoryButtonArea(favoriteButtonArea);
			this.historyButton.updateBounds(historyButtonArea);
		} else {
			ImmutableRect2i bookmarkButtonArea = insetDisplayArea
				.keepBottom(BUTTON_SIZE)
				.keepLeft(BUTTON_SIZE);
			this.bookmarkButton.updateBounds(bookmarkButtonArea);
			ImmutableRect2i favoriteButtonArea = calculateFavoritePanelButtonArea(bookmarkButtonArea);
			this.favoriteButton.updateBounds(favoriteButtonArea);
			ImmutableRect2i historyButtonArea = calculateHistoryButtonArea(favoriteButtonArea);
			this.historyButton.updateBounds(historyButtonArea);
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
		gridConfig.addLayoutListener(this::markScreenPropertiesDirty);
	}

	private void updateScreenPropertiesIfDirty() {
		if (Minecraft.getInstance().screen instanceof mezz.jei.gui.config.screen.JeiConfigScreen) {
			return;
		}
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

	public ScrollStep getScrollStep() {
		return scrollStep;
	}

	/**
	 * Shifts the bookmark contents area below exclusion areas that meaningfully cover its
	 * top-left slot cell. Such areas (e.g. an FTB sidebar icon) would otherwise create a
	 * partial first row that breaks group borders; shifting the whole area keeps every row
	 * full and lets the page buttons and scrollbar follow automatically. The shift is
	 * aligned to whole grid rows, and tiny slivers keep blocking only the slot underneath.
	 * Ported from JEI 1.21.1 (3adfca8d2).
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

	private static LayoutAreas calculateLayoutAreas(ImmutableRect2i displayArea, int historyHeight) {
		ImmutableRect2i gridArea = displayArea.cropLeft(GROUP_PANEL_WIDTH);
		ImmutableRect2i contentsLayoutArea = gridArea.cropBottom(BUTTON_SIZE + INNER_PADDING);
		if (historyHeight <= 0) {
			return new LayoutAreas(contentsLayoutArea, Optional.empty(), OptionalInt.empty());
		}
		ImmutableRect2i historyArea = gridArea.cropBottom(BUTTON_SIZE + LOOKUP_HISTORY_BOTTOM_PADDING)
			.keepBottom(historyHeight);
		return new LayoutAreas(contentsLayoutArea, Optional.of(historyArea),
			OptionalInt.of(historyArea.y() - LOOKUP_HISTORY_PADDING_EXTRA));
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
		updateScreenPropertiesIfDirty();
		boolean contentsDisplayed = isListDisplayed();
		List<IngredientGridBackgroundRenderer.Panel> backgroundPanels = new ArrayList<>(2);
		if (contentsDisplayed && this.contents.isBackgroundEnabled()) {
			backgroundPanels.add(new IngredientGridBackgroundRenderer.Panel(
				this.contents.getBackgroundArea(),
				this.contents.getSlotBackgroundArea()
			));
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
		if (isFavoritePanelDisplayed() && favoriteContents.isBackgroundEnabled()) {
			backgroundPanels.add(new IngredientGridBackgroundRenderer.Panel(favoriteContents.getBackgroundArea(), favoriteContents.getSlotBackgroundArea()));
		}
		this.backgroundRenderer.draw(
			guiGraphics,
			backgroundPanels,
			this.guiPropertiesCache.getGuiExclusionAreas()
		);
	}

	public void drawForeground(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		if (isListDisplayed()) {
			dragHandlers.updateSortDrag(mouseX, mouseY);
			this.bookmarkDragManager.updateDrag(mouseX, mouseY);
			this.contents.drawForeground(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
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
			drawBookmarkGroupPanels(guiGraphics, mouseX, mouseY);
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
			renderer.drawFavoriteRecipeRowPanels(guiGraphics, mouseX, mouseY);
		}
		if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.draw(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
		}
		if (this.guiPropertiesCache.hasValidScreen()) {
			this.bookmarkButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			this.favoriteButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			this.historyButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			getScrollStepArea();
			this.scrollStepField.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
		}
	}

	private void drawBookmarkGroupPanels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (isSmoothScrolling()) {
			ImmutableRect2i ingredientGridArea = this.contents.getIngredientGridArea();
			ImmutableRect2i clipArea = calculateGroupPanelClipArea(ingredientGridArea);
			guiGraphics.enableScissor(clipArea.getX(), clipArea.getY(), clipArea.getX() + clipArea.getWidth(), clipArea.getY() + clipArea.getHeight());
		}
		renderer.drawBookmarkGroupPanels(guiGraphics, mouseX, mouseY, groupPanelDrag, sortDragState);
		if (isSmoothScrolling()) {
			guiGraphics.disableScissor();
		}
	}

	private void drawPageFlipEdgeHighlights(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!this.bookmarkDragManager.isDragging() || !canFlipPage()) {
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

	Stream<PreviewSource> getPreviewSourcesUnderMouse(double mouseX, double mouseY) {
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
		IElement<?> element() {
			return ProjectedBookmarkElement.getSourceElement(ingredient.getElement());
		}

		boolean isPresentAndVisible() {
			IElement<?> element = element();
			return ownerDisplayed.getAsBoolean() &&
				element.isVisible() &&
				owner.containsElement(element);
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
		this.bookmarkButton.tick();
		this.historyButton.tick();
		if (isListDisplayed()) {
			this.contents.tick();
		}
		if (isFavoritePanelDisplayed()) {
			this.favoriteContents.tick();
		}
		if (guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.tick();
		}
		this.bookmarkButton.tick();
		this.historyButton.tick();
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
	public boolean containsMouse(double mouseX, double mouseY) {
		updateScreenPropertiesIfDirty();
		if (!this.guiPropertiesCache.hasValidScreen()) {
			return false;
		}
		if (this.bookmarkButton.isMouseOver(mouseX, mouseY) || this.historyButton.isMouseOver(mouseX, mouseY) || this.favoriteButton.isMouseOver(mouseX, mouseY)) {
			return true;
		}
		if (isListDisplayed() && this.contents.containsMouse(mouseX, mouseY)) {
			return true;
		}
		return (isFavoritePanelDisplayed() && this.favoriteContents.containsMouse(mouseX, mouseY)) ||
			(toggleState.isOverlayEnabled() && this.lookupHistoryOverlay.containsMouse(mouseX, mouseY));
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

	public InputArea createQuantityInputArea() {
		return InputArea.builder("Bookmark quantity", scrollStepInput)
			.controls(new InputGroup("Bookmark quantity", inputHandlers.new GroupInputHandler(), scrollStepInput))
			.blockUnhandledMouseInput()
			.build();
	}

	private void registerInputCommands(InputCommands commands, IInternalKeyMappings keys) {
		favoriteContents.registerInputCommands(commands, keys, this::isFavoritePanelDisplayed);
		contents.registerInputCommands(commands, keys, this::isListDisplayed);
		lookupHistoryOverlay.registerInputCommands(commands, keys, this::isHistoryDisplayed);
	}

	private boolean isHistoryDisplayed() {
		updateScreenPropertiesIfDirty();
		return guiPropertiesCache.hasValidScreen() && toggleState.isOverlayEnabled() && lookupHistoryOverlay.isListDisplayed();
	}

	private IInputTarget createInputHandler() {
		return new InputGroup(
			"BookmarkOverlay",
			new Child(this::isListDisplayed, inputHandlers.new RecipeCollapseInputHandler()),
			new Child(this::isListDisplayed, inputHandlers.new GroupInputHandler()),
			new Child(this::isFavoritePanelDisplayed, inputHandlers.new FavoriteRecipeRowInputHandler()),
			new Child(() -> !getScrollStepArea().isEmpty(), scrollStepInput),
			new Child(this::isFavoritePanelDisplayed, favoriteContents.getResizeInputHandler()),
			new Child(this::isFavoritePanelDisplayed, favoriteContents.createInputHandler()),
			new Child(guiPropertiesCache::hasValidScreen, favoriteButton.createInputHandler()),
			new Child(this::isHistoryDisplayed, lookupHistoryOverlay.getResizeInputHandler()),
			new Child(this::isListDisplayed, contents.getResizeInputHandler()),
			new Child(this::isListDisplayed, contents.createDeleteItemInputHandler()),
			new Child(this::isListDisplayed, contents.createInputHandler()),
			new Child(() -> true, bookmarkButton.createInputHandler()),
			new Child(() -> true, historyButton.createInputHandler()),
			new Child(this::isHistoryDisplayed, lookupHistoryOverlay.createInputHandler())
		);
	}

	/** Returns whether a bookmark drag is pending or active. */
	public boolean hasBookmarkDrag() {
		return bookmarkDragManager.hasDrag() || sortDragState != null || favoriteSortDragState != null || groupPanelDrag != null;
	}

	private List<DragSource> createDragSources() {
		return List.of(
			new DragSource(this::isListDisplayed, dragHandlers::beginSortDrag),
			new DragSource(this::isFavoritePanelDisplayed, dragHandlers::beginFavoriteSortDrag),
			new DragSource(this::isFavoritePanelDisplayed, favoriteContents::beginDrag),
			new DragSource(this::isListDisplayed, contents::beginDrag),
			new DragSource(this::isHistoryDisplayed, lookupHistoryOverlay::beginDrag),
			new DragSource(this::isListDisplayed, bookmarkDragManager::beginDrag)
		);
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

	BookmarkOverlayLayout.PanelSnapshot getGroupPanelSnapshotForRendering() {
		return layout.getPanelSnapshotForRendering();
	}

	boolean canStartGroupDrop(int groupId) {
		return BookmarkGroupDropBridge.canStartGroupDrop(bookmarkList, groupId);
	}

	private Optional<ITypedIngredient<?>> getGroupDropHoverIngredient(double mouseX, double mouseY) {
		return BookmarkGroupDropBridge.getGroupDropHoverIngredient(bookmarkList, getGroupPanelSlotUnderMouse(mouseX, mouseY));
	}

	public BookmarkList getBookmarkList() {
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

	List<BookmarkPanelLayout.RowSlot<IBookmark>> getGroupPanelRowSlots() {
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
			.filter(groupPanelSlot -> BookmarkGroupManager.DEFAULT_GROUP_ID != groupPanelSlot.groupId());
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
		// 1.21.1 parity: panels scrolled outside the clip must not receive input.
		if (!isMouseOverVisibleGroupPanelArea(mouseX, mouseY)) {
			return Optional.empty();
		}
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

	boolean hasDefaultGroupBookmarks() {
		long bookmarkVersion = bookmarkList.getChangeVersion();
		if (defaultGroupBookmarksVersion != bookmarkVersion) {
			defaultGroupBookmarksVersion = bookmarkVersion;
			defaultGroupHasBookmarks = bookmarkList.getBookmarks().stream()
				.anyMatch(bookmark -> BookmarkGroupManager.DEFAULT_GROUP_ID == bookmarkList.getBookmarkGroupId(bookmark));
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
		return this.gridConfig.navigationMode().get().usesScrollbar() && clientConfig.smoothScrollingEnabled().get();
	}

	boolean isMouseOverVisibleGroupPanelArea(double mouseX, double mouseY) {
		if (!isSmoothScrolling()) {
			return true;
		}
		ImmutableRect2i ingredientGridArea = this.contents.getIngredientGridArea();
		ImmutableRect2i clipArea = calculateGroupPanelClipArea(ingredientGridArea);
		return clipArea.contains(mouseX, mouseY);
	}

	GroupPanelSlot toGroupPanelSlot(BookmarkPanelLayout.RowSlot<IBookmark> slot) {
		return new GroupPanelSlot(slot.item(), slot.groupId(), slot.area());
	}

	public List<BookmarkGroupDropTarget> createBookmarkDragTargets() {
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

		List<BookmarkGroupDropTarget> bookmarkDragTargets = new ArrayList<>(slotTargets);
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

	public List<BookmarkDragTarget> createBookmarkDragTargets(IBookmark draggedBookmark) {
		updateScreenPropertiesIfDirty();
		List<IElement<?>> elements = this.bookmarkList.getBookmarks().stream().map(IBookmark::getElement).toList();
		List<BookmarkDragTarget> targets = BookmarkDragTarget.createSlotTargets(this.contents.getAllSlots(), elements, draggedBookmark);
		List<IElement<?>> pageElements = this.contents.getPageElements();
		// Ignore the layout's placeholders when looking for the ends of the page.
		IElement<?> first = null;
		IElement<?> last = null;
		for (IElement<?> pageElement : pageElements) {
			if (pageElement.getBookmark().isPresent()) {
				last = ProjectedBookmarkElement.getSourceElement(pageElement);
				if (first == null) {
					first = last;
				}
			}
		}
		int firstIndex = elements.indexOf(first);
		int lastIndex = elements.indexOf(last);
		if (firstIndex < 0 || lastIndex < 0) {
			return targets;
		}
		if (canFlipPage()) {
			targets.add(new BookmarkDragTarget(this.contents.getNextPageButtonArea(), (lastIndex + 1) % elements.size()));
			targets.add(new BookmarkDragTarget(this.contents.getBackButtonArea(), Math.floorMod(firstIndex - 1, elements.size())));
		}

		// Trailing empty slots and background padding place the bookmark at the end of this page.
		targets.add(new BookmarkDragTarget(this.contents.getSlotBackgroundArea(), lastIndex));
		return targets;
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

	public static class ActionDragTarget extends DragTarget {
		private final Runnable action;

		public ActionDragTarget(
			ImmutableRect2i area,
			IBookmark bookmark,
			int targetGroupId,
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

	public static class DragTarget implements BookmarkGroupDropTarget {
		private final ImmutableRect2i area;
		private final IBookmark bookmark;
		private final int targetGroupId;
		private final BookmarkList bookmarkList;
		private final int offset;
		private final List<BookmarkPanelLayout.PanelSlot<IBookmark>> panelSlots;
		private final List<BookmarkPanelLayout.RowSlot<IBookmark>> rowSlots;
		private final @Nullable BookmarkPanelLayout.RowSlot<IBookmark> targetRow;

		public DragTarget(ImmutableRect2i area, IBookmark bookmark, int targetGroupId, BookmarkList bookmarkList, int offset) {
			this(area, bookmark, targetGroupId, bookmarkList, offset, List.of(), List.of(), null);
		}

		public DragTarget(
			ImmutableRect2i area,
			IBookmark bookmark,
			int targetGroupId,
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
			int targetGroupId,
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
			int sourceGroupId = bookmarkList.getBookmarkGroupId(bookmark);
			// 1.21.1 parity: this target accepts items that already live in the group (reordering)
			// and refuses cross-group drops, which the drag selection handles separately.
			if (sourceGroupId != targetGroupId) {
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
			int sourceGroupId = bookmarkList.getBookmarkGroupId(bookmark);
			if (sourceGroupId != targetRow.groupId() && !selection.crossGroupMove()) {
				return Optional.empty();
			}
			if (sourceGroupId == targetRow.groupId()) {
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
			int sourceGroupId = bookmarkList.getBookmarkGroupId(bookmark);
			if (sourceGroupId == plan.targetGroupId() && targetRow != null) {
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
			int sourceGroupId = bookmarkList.getBookmarkGroupId(bookmark);
			return sourceGroupId != targetGroupId;
		}

		private static Map<Integer, Set<ResourceLocation>> getRecipeIdsByGroup(
			BookmarkList bookmarkList,
			List<IBookmark> ignoredBookmarks
		) {
			Set<IBookmark> ignored = new HashSet<>(ignoredBookmarks);
			Map<Integer, Set<ResourceLocation>> recipeIdsByGroup = new HashMap<>();
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
