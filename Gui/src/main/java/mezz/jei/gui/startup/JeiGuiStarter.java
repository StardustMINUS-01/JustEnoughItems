package mezz.jei.gui.startup;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.registration.IRuntimeRegistration;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.config.IJeiClientConfigs;
import mezz.jei.common.gui.JeiGuiColors;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.packets.PacketCraftingGridCraftAck;
import mezz.jei.common.platform.Services;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.common.util.LoggedTimer;
import mezz.jei.gui.bookmarks.BookmarkCodec;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingRunner;
import mezz.jei.gui.collapsible.CollapsibleManager;
import mezz.jei.gui.collapsible.CollapsibleState;
import mezz.jei.gui.config.CollapsibleConfig;
import mezz.jei.common.config.CollapsibleColorConfig;
import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleSettings;
import mezz.jei.gui.config.CollapsibleStateStore;
import mezz.jei.gui.config.BookmarkConfigEntry;
import mezz.jei.gui.config.BookmarkConfigEntryCodec;
import mezz.jei.gui.config.ConfigFileImporter;
import mezz.jei.gui.config.ConfigRulesReloadController;
import mezz.jei.gui.config.FavoriteRecipeConfig;
import mezz.jei.gui.config.IBookmarkConfig;
import mezz.jei.gui.config.ILookupHistoryConfig;
import mezz.jei.gui.config.IngredientTypeSortingConfig;
import mezz.jei.gui.config.ModNameSortingConfig;
import mezz.jei.gui.config.RecipePreferenceConfig;
import mezz.jei.gui.events.GuiEventHandler;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.favorites.FavoriteTreeBookmarkWriter;
import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.favorites.FavoriteTreeRecipeLayoutResolver;
import mezz.jei.gui.favorites.RecipePreferenceCandidateResolver;
import mezz.jei.gui.favorites.SlotPreferenceResolver;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.filter.FilterTextSource;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.ingredients.IngredientFilterApi;
import mezz.jei.gui.ingredients.IngredientListElementFactory;
import mezz.jei.gui.ingredients.IngredientSorter;
import mezz.jei.gui.input.ClientInputHandler;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.GuiContainerWrapper;
import mezz.jei.gui.input.ICharTypedHandler;
import mezz.jei.gui.input.handlers.BookmarkInputHandler;
import mezz.jei.gui.input.handlers.ChatLinkInputHandler;
import mezz.jei.gui.input.handlers.CheatInputHandler;
import mezz.jei.gui.input.handlers.DragRouter;
import mezz.jei.gui.input.handlers.EditInputHandler;
import mezz.jei.gui.input.handlers.IngredientShortcutInputHandler;
import mezz.jei.gui.input.handlers.GlobalInputHandler;
import mezz.jei.gui.input.handlers.GuiAreaInputHandler;
import mezz.jei.gui.input.handlers.UserInputRouter;
import mezz.jei.gui.input.handlers.WorldInputHandler;
import mezz.jei.gui.overlay.IngredientListOverlay;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.bookmarks.ScrollStep;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistory;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class JeiGuiStarter {
	private static final Logger LOGGER = LogManager.getLogger();

	public static JeiEventHandlers start(IRuntimeRegistration registration) {
		LOGGER.info("Starting JEI GUI");
		LoggedTimer timer = new LoggedTimer();

		IConnectionToServer serverConnection = Internal.getServerConnection();
		Textures textures = Internal.getTextures();
		IInternalKeyMappings keyMappings = Internal.getKeyMappings();

		IScreenHelper screenHelper = registration.getScreenHelper();
		IRecipeTransferManager recipeTransferManager = registration.getRecipeTransferManager();
		RecipeTransferService recipeTransferService = new RecipeTransferService(recipeTransferManager);
		IRecipeManager recipeManager = registration.getRecipeManager();
		IIngredientManager ingredientManager = registration.getIngredientManager();
		IEditModeConfig editModeConfig = registration.getEditModeConfig();
		ISearchStorageBuilderFactory searchStorageBuilderFactory = registration.getSearchStorageBuilderFactory();

		IJeiHelpers jeiHelpers = registration.getJeiHelpers();
		IIngredientVisibility ingredientVisibility = jeiHelpers.getIngredientVisibility();
		IColorHelper colorHelper = jeiHelpers.getColorHelper();
		IModIdHelper modIdHelper = jeiHelpers.getModIdHelper();
		IFocusFactory focusFactory = jeiHelpers.getFocusFactory();
		IGuiHelper guiHelper = jeiHelpers.getGuiHelper();
		ICodecHelper codecHelper = jeiHelpers.getCodecHelper();

		IFilterTextSource filterTextSource = new FilterTextSource();
		Minecraft minecraft = Minecraft.getInstance();
		JeiGuiColors.onResourceManagerReload(minecraft.getResourceManager());
		ClientLevel level = minecraft.level;
		ErrorUtil.checkNotNull(level, "minecraft.level");

		RegistryAccess registryAccess = level.registryAccess();

		timer.start("Building ingredient list");
		List<IListElementInfo<?>> ingredientList = IngredientListElementFactory.createBaseList(ingredientManager, modIdHelper);
		timer.stop();

		timer.start("Building ingredient filter");
		GuiConfigData configData = GuiConfigData.create();

		ModNameSortingConfig modNameSortingConfig = configData.modNameSortingConfig();
		IngredientTypeSortingConfig ingredientTypeSortingConfig = configData.ingredientTypeSortingConfig();
		IClientToggleState toggleState = Internal.getClientToggleState();
		IBookmarkConfig bookmarkConfig = configData.bookmarkConfig();
		FavoriteRecipeConfig favoriteRecipeConfig = new FavoriteRecipeConfig(
			configData.configDir(),
			codecHelper,
			ingredientManager,
			registryAccess
		);
		RecipePreferenceConfig recipePreferenceConfig = configData.recipePreferenceConfig();
		CollapsibleConfig collapsibleConfig = configData.collapsibleConfig();
		CollapsibleStateStore collapsibleStateStore = configData.collapsibleStateStore();
		ILookupHistoryConfig lookupHistoryConfig = configData.lookupHistoryConfig();

		boolean collapsibleGroupsInstalled = Services.PLATFORM.getModHelper().isModLoaded("collapsible_groups");
		ConfigFileImporter configFileImporter = new ConfigFileImporter(configData.configDir());
		recipePreferenceConfig.ensureDefaultFile();
		configFileImporter.importFiles("recipe-preferences-", recipePreferenceConfig.getPath());

		RecipePreferenceRules recipePreferenceRules = recipePreferenceConfig.loadRules();
		CollapsibleManager collapsibleManager = null;
		if (!collapsibleGroupsInstalled) {
			CollapsibleState collapsibleState = new CollapsibleState();
			collapsibleState.load(collapsibleStateStore.load());
			collapsibleManager = new CollapsibleManager(
				collapsibleConfig.load(),
				CollapsibleSettings.fromConfig(),
				collapsibleState
			);
			collapsibleState.addListener(() -> collapsibleStateStore.save(collapsibleState.toMap()));
		}

		IJeiClientConfigs jeiClientConfigs = Internal.getJeiClientConfigs();
		IClientConfig clientConfig = jeiClientConfigs.getClientConfig();
		ScrollStep scrollStep = new ScrollStep(() -> clientConfig.quantityFieldEnabled().getValue());
		IIngredientGridConfig ingredientListConfig = jeiClientConfigs.getIngredientListConfig();
		IIngredientGridConfig bookmarkListConfig = jeiClientConfigs.getBookmarkListConfig();
		IIngredientFilterConfig ingredientFilterConfig = jeiClientConfigs.getIngredientFilterConfig();

		Function<List<IListElementInfo<?>>, Comparator<IListElement<?>>> sortIndexUpdater = ingredients -> IngredientSorter.sortIngredients(
			clientConfig,
			modNameSortingConfig,
			ingredientTypeSortingConfig,
			ingredientManager,
			ingredients
		);

		IngredientFilter ingredientFilter = new IngredientFilter(
			filterTextSource,
			clientConfig,
			ingredientFilterConfig,
			ingredientManager,
			sortIndexUpdater,
			ingredientList,
			modIdHelper,
			ingredientVisibility,
			colorHelper,
			searchStorageBuilderFactory,
			toggleState
		);
		ingredientManager.registerIngredientListener(ingredientFilter);
		ingredientVisibility.registerListener(ingredientFilter);
		timer.stop();

		IIngredientFilter ingredientFilterApi = new IngredientFilterApi(ingredientFilter, filterTextSource);
		registration.setIngredientFilter(ingredientFilterApi);

		BookmarkFactory bookmarkFactory = new BookmarkFactory(codecHelper, registryAccess, ingredientManager);
		Codec<IBookmark> bookmarkCodec = BookmarkCodec.create(codecHelper, ingredientManager, recipeManager, recipeTransferService, bookmarkFactory).codec();
		Codec<BookmarkConfigEntry> bookmarkEntryCodec = BookmarkConfigEntryCodec.create(codecHelper, ingredientManager, bookmarkCodec);
		RegistryOps<JsonElement> bookmarkRegistryOps = registryAccess.createSerializationContext(JsonOps.INSTANCE);

		LookupHistory lookupHistory = new LookupHistory(
			recipeManager,
			ingredientManager,
			registryAccess,
			codecHelper,
			clientConfig.maxLookupHistoryIngredients(),
			lookupHistoryConfig,
			bookmarkCodec
		);

		IngredientListOverlay ingredientListOverlay = OverlayHelper.createIngredientListOverlay(
			ingredientManager,
			screenHelper,
			ingredientFilter,
			lookupHistory,
			filterTextSource,
			keyMappings,
			ingredientListConfig,
			clientConfig,
			toggleState,
			serverConnection,
			ingredientFilterConfig,
			textures,
			colorHelper,
			collapsibleManager
		);
		registration.setIngredientListOverlay(ingredientListOverlay);

		FavoriteRecipeStore favoriteRecipes = favoriteRecipeConfig.loadFavorites();
		favoriteRecipes.addSourceListChangedListener(() -> favoriteRecipeConfig.saveFavorites(favoriteRecipes));
		BookmarkList bookmarkList = new BookmarkList(
			recipeManager,
			focusFactory,
			ingredientManager,
			registryAccess,
			bookmarkConfig,
			clientConfig,
			guiHelper,
			favoriteRecipes::getFavorite,
			codecHelper,
			bookmarkCodec,
			bookmarkFactory
		);
		bookmarkConfig.loadBookmarks(
			recipeManager,
			focusFactory,
			guiHelper,
			ingredientManager,
			registryAccess,
			bookmarkList,
			codecHelper,
			bookmarkCodec,
			bookmarkFactory,
			recipeTransferService
		);
		registration.setBookmarkManager(bookmarkList);

		BookmarkOverlay bookmarkOverlay = OverlayHelper.createBookmarkOverlay(
			ingredientManager,
			recipeManager,
			focusFactory,
			screenHelper,
			bookmarkList,
			favoriteRecipes,
			recipeTransferService,
			lookupHistory,
			keyMappings,
			bookmarkListConfig,
			ingredientFilterConfig,
			clientConfig,
			toggleState,
			serverConnection,
			scrollStep,
			textures,
			colorHelper
		);
		registration.setBookmarkOverlay(bookmarkOverlay);
		bookmarkOverlay.setQuantityAreaSupplier(ingredientListOverlay::getQuantityArea);

		BookmarkAutoCraftingRunner bookmarkAutoCraftingRunner = new BookmarkAutoCraftingRunner();
		PacketCraftingGridCraftAck.setListener(ack -> bookmarkAutoCraftingRunner.handleAck(ack.taskId(), ack.requestId(), ack.craftedCount()));

		FavoriteTreeRecipeLayoutResolver favoriteTreeRecipeResolver = new FavoriteTreeRecipeLayoutResolver(
			recipeManager,
			focusFactory,
			ingredientManager
		);
		AtomicReference<RecipePreferenceRules> recipePreferenceRulesRef = new AtomicReference<>(recipePreferenceRules);
		RecipePreferenceCandidateResolver recipePreferenceCandidateResolver = RecipePreferenceCandidateResolver.create(
			recipeManager,
			focusFactory,
			ingredientManager,
			recipePreferenceRulesRef::get
		);
		// Recipe preference data used to require a delayed first full scan after the JEI runtime
		// was created (Internal.setRuntime happens after the registerRuntime callback returns);
		// it is now resolved on demand, so no scan is needed.
		favoriteRecipes.setGeneratedFavoriteResolver(
			(target, layoutCache) -> recipePreferenceCandidateResolver.resolveGeneratedFavorite(target, layoutCache)
		);
		ConfigRulesReloadController<RecipePreferenceRules> recipePreferenceRulesReloadController = new ConfigRulesReloadController<>(
			recipePreferenceConfig::loadRules,
			minecraft::execute,
			rules -> {
				recipePreferenceRulesRef.set(rules);
				favoriteRecipes.clearGeneratedFavorites();
				recipePreferenceCandidateResolver.invalidateGeneratedFavorites();
			}
		);
		Internal.getFileWatcher().addCallback(
			recipePreferenceConfig.getPath(),
			recipePreferenceRulesReloadController::onConfigFileChanged
		);
		if (collapsibleManager != null) {
			CollapsibleManager activeCollapsibleManager = collapsibleManager;
			CollapsibleColorConfig.getCollapsedColor().addListener(color -> activeCollapsibleManager.setSettings(new CollapsibleSettings(color, activeCollapsibleManager.settings().expandedColor())));
			CollapsibleColorConfig.getExpandedColor().addListener(color -> activeCollapsibleManager.setSettings(new CollapsibleSettings(activeCollapsibleManager.settings().collapsedColor(), color)));
			ConfigRulesReloadController<CollapsibleRules> collapsibleRulesReloadController = new ConfigRulesReloadController<>(
				collapsibleConfig::load,
				minecraft::execute,
				activeCollapsibleManager::reload
			);
			Internal.getFileWatcher().addDirectoryCallback(
				collapsibleConfig.getDirectory(),
				path -> path.getFileName().toString().endsWith(".txt"),
				collapsibleRulesReloadController::onConfigFileChanged
			);
		}
		Internal.getFileWatcher().addDirectoryCallback(
			configData.configDir(),
			path -> path.getFileName().toString().startsWith("recipe-preferences-"),
			() -> configFileImporter.importFiles("recipe-preferences-", recipePreferenceConfig.getPath())
		);
		FavoriteTreeBookmarkWriter favoriteTreeBookmarkWriter = new FavoriteTreeBookmarkWriter(
			new FavoriteTreeBuilder(
				favoriteRecipes,
				favoriteTreeRecipeResolver,
				new SlotPreferenceResolver(recipePreferenceCandidateResolver, recipePreferenceRulesRef::get)
			),
			favoriteTreeRecipeResolver::resolveLayout,
			bookmarkList::addRecipeLayoutProjectionBookmarkGroup
		);
		FocusUtil focusUtil = new FocusUtil(focusFactory, clientConfig, ingredientManager);

		RecipesGui recipesGui = new RecipesGui(
			recipeManager,
			ingredientManager,
			recipeTransferService,
			keyMappings,
			focusFactory,
			bookmarkList,
			lookupHistory,
			guiHelper,
			bookmarkFactory,
			favoriteRecipes,
			favoriteRecipeConfig,
			favoriteTreeBookmarkWriter,
			bookmarkOverlay::showBookmarkPanel,
			bookmarkOverlay::showFavoritePanel,
			recipePreferenceRulesRef::get,
			searchStorageBuilderFactory,
			focusUtil
		);
		registration.setRecipesGui(recipesGui);
		var recipesGuiForegroundInputLayer = recipesGui.getForegroundInputLayer();
		var bookmarkPreviewTooltipController = bookmarkOverlay.getPreviewTooltipController();

		CombinedRecipeFocusSource recipeFocusSource = new CombinedRecipeFocusSource(
			recipesGui.getCandidateFocusSource(),
			bookmarkPreviewTooltipController,
			recipesGui,
			ingredientListOverlay,
			bookmarkOverlay,
			new GuiContainerWrapper(screenHelper)
		);
		var focusInputHandler = new IngredientShortcutInputHandler(recipeFocusSource, recipesGui, focusUtil, clientConfig, ingredientManager, recipeManager, focusFactory);
		var tagSelectionTooltip = focusInputHandler.getTagSelectionTooltip();
		GuiEventHandler guiEventHandler = new GuiEventHandler(
			screenHelper,
			bookmarkOverlay,
			ingredientListOverlay,
			bookmarkAutoCraftingRunner,
			tagSelectionTooltip,
			recipesGuiForegroundInputLayer,
			bookmarkPreviewTooltipController
		);

		List<ICharTypedHandler> charTypedHandlers = List.of(
			tagSelectionTooltip,
			ingredientListOverlay,
			bookmarkOverlay
		);

		UserInputRouter userInputRouter = new UserInputRouter(
			"JEIGlobal",
			tagSelectionTooltip,
			recipesGuiForegroundInputLayer,
			bookmarkPreviewTooltipController,
			new EditInputHandler(recipeFocusSource, toggleState, editModeConfig),
			ingredientListOverlay.createDeleteItemInputHandler(),
			bookmarkOverlay.createDeleteItemInputHandler(),
			new CheatInputHandler(recipeFocusSource, clientConfig, ingredientManager, toggleState, serverConnection, scrollStep),
			ingredientListOverlay.createInputHandler(),
			bookmarkOverlay.createInputHandler(),
			new BookmarkInputHandler(
				recipeFocusSource,
				bookmarkList,
				bookmarkOverlay,
				ingredientManager,
				serverConnection,
				bookmarkAutoCraftingRunner,
				recipe -> favoriteTreeBookmarkWriter.save(recipe, clientConfig.favoriteTreeDepth().getValue()),
				favoriteRecipes::getFavorite,
				bookmarkPreviewTooltipController,
				clientConfig,
				recipesGui,
				bookmarkEntryCodec,
				bookmarkRegistryOps
			),
			focusInputHandler,
			new GlobalInputHandler(toggleState),
			new GuiAreaInputHandler(screenHelper, recipesGui, focusFactory)
		);

		DragRouter dragRouter = new DragRouter(
			tagSelectionTooltip,
			ingredientListOverlay.createDragHandler(),
			bookmarkOverlay.createDragHandler()
		);
		ClientInputHandler clientInputHandler = new ClientInputHandler(
			charTypedHandlers,
			new ChatLinkInputHandler(recipesGui, focusUtil, screenHelper, bookmarkList, bookmarkEntryCodec, bookmarkRegistryOps),
			userInputRouter,
			dragRouter,
			keyMappings,
			screenHelper
		);
		ResourceReloadHandler resourceReloadHandler = new ResourceReloadHandler(
			ingredientListOverlay,
			ingredientFilter,
			() -> {
				favoriteRecipes.clearGeneratedFavorites();
				recipePreferenceCandidateResolver.invalidateAll();
			}
		);

		return new JeiEventHandlers(
			guiEventHandler,
			clientInputHandler,
			new WorldInputHandler(bookmarkOverlay, recipesGui, focusUtil, ingredientManager),
			resourceReloadHandler
		);
	}
}
