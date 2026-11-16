package mezz.jei.gui.startup;

import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.config.HistoryDisplaySide;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.collapsible.CollapsibleGridSource;
import mezz.jei.gui.collapsible.CollapsibleManager;
import mezz.jei.gui.collapsible.CollapsibleSlotVisualsProvider;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipeGridSource;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.overlay.IngredientListSlotContext;
import mezz.jei.gui.overlay.bookmarks.BookmarkChainSlotVisuals;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotDisplayMode;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisualContext;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.overlay.ingredients.IngredientGridBackgroundRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientGrid;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.IngredientListOverlay;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryGridConfig;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.bookmarks.FavoriteRecipeSlotVisuals;
import mezz.jei.gui.overlay.bookmarks.ScrollStep;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import mezz.jei.gui.search.ISearchCompletionProvider;

public final class OverlayHelper {
	private OverlayHelper() {}

	public static IngredientGridWithNavigation createIngredientGridWithNavigation(
		String debugName,
		IIngredientGridSource ingredientFilter,
		IIngredientManager ingredientManager,
		IIngredientGridConfig ingredientGridConfig,
		IInternalKeyMappings keyMappings,
		IIngredientFilterConfig ingredientFilterConfig,
		IClientConfig clientConfig,
		IClientToggleState toggleState,
		IConnectionToServer serverConnection,
		IColorHelper colorHelper,
		IScreenHelper screenHelper,
		boolean supportsEditMode
	) {
		IngredientGrid ingredientListGrid = new IngredientGrid(
			ingredientManager,
			ingredientFilterConfig,
			clientConfig,
			toggleState,
			serverConnection,
			keyMappings,
			colorHelper,
			supportsEditMode
		);

		return new IngredientGridWithNavigation(
			debugName,
			ingredientFilter,
			ingredientListGrid,
			toggleState,
			clientConfig,
			serverConnection,
			ingredientGridConfig,
			screenHelper,
			ingredientManager
		);
	}

	public static IngredientListOverlay createIngredientListOverlay(
		IIngredientManager ingredientManager,
		IScreenHelper screenHelper,
		IIngredientGridSource ingredientFilter,
		IIngredientGridSource historyList,
		IFilterTextSource filterTextSource,
		IInternalKeyMappings keyMappings,
		IIngredientGridConfig ingredientGridConfig,
		IClientConfig clientConfig,
		IClientToggleState toggleState,
		IConnectionToServer serverConnection,
		IIngredientFilterConfig ingredientFilterConfig,
		Textures textures,
		IColorHelper colorHelper,
		@Nullable CollapsibleManager collapsibleManager,
		ISearchCompletionProvider searchCompletionProvider
	) {
		IIngredientGridSource gridSource = ingredientFilter;
		CollapsibleGridSource collapsibleGridSource = null;
		if (collapsibleManager != null) {
			collapsibleGridSource = new CollapsibleGridSource(ingredientFilter, collapsibleManager);
			gridSource = collapsibleGridSource;
		}
		IngredientGridWithNavigation ingredientListGridNavigation = createIngredientGridWithNavigation(
			"IngredientListOverlay",
			gridSource,
			ingredientManager,
			ingredientGridConfig,
			keyMappings,
			ingredientFilterConfig,
			clientConfig,
			toggleState,
			serverConnection,
			colorHelper,
			screenHelper,
			true
		);
		if (collapsibleManager != null && collapsibleGridSource != null) {
			var groupButton = new mezz.jei.gui.elements.IconButton(new mezz.jei.gui.collapsible.CollapsibleGroupButtonController(collapsibleManager));
			groupButton.setRightClickEnabled(true);
			ingredientListGridNavigation.setNavigationButton(groupButton);
			CollapsibleGridSource activeCollapsibleGridSource = collapsibleGridSource;
			CollapsibleSlotVisualsProvider collapsibleSlotVisualsProvider = new CollapsibleSlotVisualsProvider(
				ingredientListGridNavigation::getAllSlots,
				collapsibleManager::settings
			);
			ingredientListGridNavigation.setSlotVisualsResolver(collapsibleSlotVisualsProvider::apply);
			activeCollapsibleGridSource.addSourceListChangedListener(collapsibleSlotVisualsProvider::invalidate);
			collapsibleManager.state().addListener(() -> {
				collapsibleSlotVisualsProvider.invalidate();
				String groupId = collapsibleManager.getLastToggledGroupId();
				if (groupId != null) {
					IElement<?> anchor = activeCollapsibleGridSource.getAnchorElementForGroup(groupId);
					ingredientListGridNavigation.updateLayoutKeepingPageAnchorVisible(anchor);
				} else {
					ingredientListGridNavigation.updateLayout(false);
				}
			});
		}

		IIngredientGridConfig lookupHistoryGridConfig = new LookupHistoryGridConfig(
			ingredientGridConfig,
			clientConfig.maxLookupHistoryRows(),
			clientConfig.maxLookupHistoryColumns()
		);
		IngredientGridWithNavigation lookupHistoryGridNavigation = createIngredientGridWithNavigation(
			"IngredientListLookupHistory",
			historyList,
			ingredientManager,
			lookupHistoryGridConfig,
			keyMappings,
			ingredientFilterConfig,
			clientConfig,
			toggleState,
			serverConnection,
			colorHelper,
			screenHelper,
			false
		);
		LookupHistoryOverlay lookupHistoryOverlay = new LookupHistoryOverlay(
			historyList,
			lookupHistoryGridNavigation,
			lookupHistoryGridConfig,
			clientConfig,
			HistoryDisplaySide.RIGHT
		);
		IngredientGridBackgroundRenderer backgroundRenderer = new IngredientGridBackgroundRenderer(
			ingredientGridConfig.backgroundStyle(),
			textures.getIngredientListBackground(),
			textures.getIngredientListSlotBackground(),
			textures.getExclusionAreaShadow()
		);

		return new IngredientListOverlay(
			gridSource,
			filterTextSource,
			screenHelper,
			ingredientListGridNavigation,
			lookupHistoryOverlay,
			backgroundRenderer,
			ingredientGridConfig,
			clientConfig,
			toggleState,
			keyMappings,
			searchCompletionProvider
		);
	}

	public static BookmarkOverlay createBookmarkOverlay(
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IScreenHelper screenHelper,
		BookmarkList bookmarkList,
		FavoriteRecipeStore favoriteRecipes,
		RecipeTransferService recipeTransferService,
		IIngredientGridSource lookupHistory,
		IInternalKeyMappings keyMappings,
		IIngredientGridConfig bookmarkListConfig,
		IIngredientFilterConfig ingredientFilterConfig,
		IClientConfig clientConfig,
		IClientToggleState toggleState,
		IConnectionToServer serverConnection,
		ScrollStep scrollStep,
		Textures textures,
		IColorHelper colorHelper
	) {
		IngredientGridWithNavigation bookmarkListGridNavigation = createIngredientGridWithNavigation(
			"BookmarkOverlay",
			bookmarkList,
			ingredientManager,
			bookmarkListConfig,
			keyMappings,
			ingredientFilterConfig,
			clientConfig,
			toggleState,
			serverConnection,
			colorHelper,
			screenHelper,
			false
		);
		bookmarkListGridNavigation.setSlotVisualsResolver(element -> element.element()
			.getBookmark()
			.flatMap(bookmarkList::getDisplayEntry)
			.flatMap(entry -> BookmarkChainSlotVisuals.create(entry, new BookmarkSlotVisualContext(
				getBookmarkSlotDisplayMode(),
				getHoveredBookmarkDisplayEntry(bookmarkList, element),
				element.rowIndex(),
				element.hoveredRowIndex(),
				clientConfig.getBookmarkRecipeMarkerMode()
			)))
		);

		FavoriteRecipePanelState favoritePanelState = new FavoriteRecipePanelState();
		FavoriteRecipeGridSource favoriteRecipeGridSource = new FavoriteRecipeGridSource(
			favoriteRecipes,
			favoritePanelState,
			ingredientManager,
			recipeManager,
			focusFactory
		);
		IngredientGridWithNavigation favoriteRecipeGridNavigation = createIngredientGridWithNavigation(
			"FavoriteRecipeOverlay",
			favoriteRecipeGridSource,
			ingredientManager,
			bookmarkListConfig,
			keyMappings,
			ingredientFilterConfig,
			clientConfig,
			toggleState,
			serverConnection,
			colorHelper,
			screenHelper,
			false
		);
		favoriteRecipeGridNavigation.setSlotVisualsResolver(context -> Optional.of(context.element())
			.filter(FavoriteRecipeElement.class::isInstance)
			.map(FavoriteRecipeElement.class::cast)
			.flatMap(FavoriteRecipeSlotVisuals::create)
		);

		IIngredientGridConfig lookupHistoryGridConfig = new LookupHistoryGridConfig(
			bookmarkListConfig,
			clientConfig.maxLookupHistoryRows(),
			clientConfig.maxLookupHistoryColumns()
		);
		IngredientGridWithNavigation lookupHistoryGridNavigation = createIngredientGridWithNavigation(
			"BookmarkLookupHistory",
			lookupHistory,
			ingredientManager,
			lookupHistoryGridConfig,
			keyMappings,
			ingredientFilterConfig,
			clientConfig,
			toggleState,
			serverConnection,
			colorHelper,
			screenHelper,
			false
		);
		LookupHistoryOverlay lookupHistoryOverlay = new LookupHistoryOverlay(
			lookupHistory,
			lookupHistoryGridNavigation,
			lookupHistoryGridConfig,
			clientConfig,
			HistoryDisplaySide.LEFT
		);
		IngredientGridBackgroundRenderer backgroundRenderer = new IngredientGridBackgroundRenderer(
			bookmarkListConfig.backgroundStyle(),
			textures.getBookmarkListBackground(),
			textures.getBookmarkListSlotBackground(),
			textures.getExclusionAreaShadow()
		);

		return new BookmarkOverlay(
			bookmarkList,
			recipeTransferService,
			bookmarkListGridNavigation,
			favoriteRecipes,
			favoritePanelState,
			favoriteRecipeGridNavigation,
			lookupHistoryOverlay,
			backgroundRenderer,
			toggleState,
			clientConfig,
			bookmarkListConfig,
			screenHelper,
			keyMappings,
			scrollStep
		);
	}

	private static BookmarkSlotDisplayMode getBookmarkSlotDisplayMode() {
		if (Screen.hasShiftDown()) {
			return BookmarkSlotDisplayMode.SHIFT;
		}
		if (Screen.hasControlDown()) {
			return BookmarkSlotDisplayMode.REAL;
		}
		return BookmarkSlotDisplayMode.DEFAULT;
	}

	private static Optional<BookmarkDisplayEntry<?>> getHoveredBookmarkDisplayEntry(BookmarkList bookmarkList, IngredientListSlotContext context) {
		return context.hoveredElement()
			.flatMap(hoveredElement -> hoveredElement.getBookmark()
				.flatMap(bookmarkList::getDisplayEntry)
				.map(entry -> (BookmarkDisplayEntry<?>) entry));
	}
}
