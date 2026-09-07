package mezz.jei.gui.startup;

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
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.packets.PacketCraftingGridCraftAck;
import mezz.jei.common.platform.Services;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.common.util.LoggedTimer;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingRunner;
import mezz.jei.gui.bookmarks.hotkeys.ClientCraftingGridClickRunner;
import mezz.jei.gui.collapsible.CollapsibleManager;
import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleSettings;
import mezz.jei.gui.collapsible.CollapsibleState;
import mezz.jei.gui.config.CollapsibleConfig;
import mezz.jei.gui.config.ConfigRulesReloadController;
import mezz.jei.gui.config.CollapsibleStateStore;
import mezz.jei.gui.config.ConfigFileImporter;
import mezz.jei.gui.config.FavoriteRecipeConfig;
import mezz.jei.gui.config.IBookmarkConfig;
import mezz.jei.gui.config.ILookupHistoryConfig;
import mezz.jei.gui.config.IngredientTypeSortingConfig;
import mezz.jei.gui.config.ModNameSortingConfig;
import mezz.jei.gui.config.RecipePreferenceConfig;

import mezz.jei.gui.events.GuiEventHandler;
import mezz.jei.gui.filter.FilterTextSource;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.overlay.bookmarks.ScrollStep;
import mezz.jei.gui.favorites.FavoriteTreeBookmarkWriter;
import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.favorites.FavoriteTreeRecipeLayoutResolver;
import mezz.jei.gui.favorites.RecipePreferenceCandidateResolver;
import mezz.jei.gui.favorites.SlotPreferenceResolver;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
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
import mezz.jei.gui.input.handlers.DragRouter;
import mezz.jei.gui.input.handlers.EditInputHandler;
import mezz.jei.gui.input.handlers.FocusInputHandler;
import mezz.jei.gui.input.handlers.GlobalInputHandler;
import mezz.jei.gui.input.handlers.GuiAreaInputHandler;
import mezz.jei.gui.input.handlers.UserInputRouter;
import mezz.jei.gui.overlay.IngredientListOverlay;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistory;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.nio.file.Path;

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

		IFilterTextSource filterTextSource = new FilterTextSource();
		Minecraft minecraft = Minecraft.getInstance();
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
		ScrollStep scrollStep = new ScrollStep();
		IBookmarkConfig bookmarkConfig = configData.bookmarkConfig();
		ILookupHistoryConfig lookupHistoryConfig = configData.lookupHistoryConfig();

		CollapsibleConfig collapsibleConfig = configData.collapsibleConfig();
		CollapsibleStateStore collapsibleStateStore = configData.collapsibleStateStore();

		RecipePreferenceConfig recipePreferenceConfig = new RecipePreferenceConfig(configData.configDir());
		recipePreferenceConfig.ensureDefaultFile();

		boolean collapsibleGroupsInstalled = Services.PLATFORM.getModHelper().isModLoaded("collapsible_groups");
		ConfigFileImporter configFileImporter = new ConfigFileImporter(configData.configDir());
		configFileImporter.importFiles("recipe-preferences-", recipePreferenceConfig.getPath());
		RecipePreferenceRules recipePreferenceRules = recipePreferenceConfig.loadRules();
		CollapsibleManager collapsibleManager = null;
		if (!collapsibleGroupsInstalled) {
			collapsibleConfig.ensureDefaultFile();
			configFileImporter.importFiles("collapsible-items-", collapsibleConfig.getPath());
			CollapsibleState collapsibleState = new CollapsibleState();
			collapsibleState.load(collapsibleStateStore.load());
			CollapsibleRules collapsibleRules = collapsibleConfig.loadRules();
			CollapsibleSettings collapsibleSettings = collapsibleConfig.loadSettings();
			collapsibleManager = new CollapsibleManager(
				collapsibleRules,
				collapsibleSettings,
				collapsibleState
			);
			collapsibleState.addListener(() -> collapsibleStateStore.save(collapsibleState.toMap()));
		}

		IJeiClientConfigs jeiClientConfigs = Internal.getJeiClientConfigs();
		IClientConfig clientConfig = jeiClientConfigs.getClientConfig();
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

		LookupHistory lookupHistory = new LookupHistory(
			recipeManager,
			ingredientManager,
			focusFactory,
			clientConfig,
			lookupHistoryConfig
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

		// 1.20.1 has no GuiConfigData.favoriteRecipeConfig(), so a FavoriteRecipeConfig is created here
		// from the same config directory that GuiConfigData uses (1.21.1 uses configData.favoriteRecipeConfig())
		Path configDir = Services.PLATFORM.getConfigHelper().createJeiConfigDir();
		FavoriteRecipeConfig favoriteRecipeConfig = new FavoriteRecipeConfig(configDir);
		FavoriteRecipeStore favoriteRecipes = favoriteRecipeConfig.loadFavorites();
		favoriteRecipes.addSourceListChangedListener(() -> favoriteRecipeConfig.saveFavorites(favoriteRecipes));
		BookmarkList bookmarkList = new BookmarkList(recipeManager, focusFactory, ingredientManager, registryAccess, bookmarkConfig, clientConfig, guiHelper);
		bookmarkConfig.loadBookmarks(recipeManager, focusFactory, guiHelper, ingredientManager, registryAccess, bookmarkList);

		BookmarkOverlay bookmarkOverlay = OverlayHelper.createBookmarkOverlay(
			ingredientManager,
			recipeManager,
			focusFactory,
			screenHelper,
			bookmarkList,
			favoriteRecipes,
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

		if (collapsibleManager != null) {
			CollapsibleManager activeCollapsibleManager = collapsibleManager;
			ConfigRulesReloadController<CollapsibleRules> collapsibleRulesReloadController = new ConfigRulesReloadController<>(
				collapsibleConfig::loadRules,
				minecraft::execute,
				rules -> activeCollapsibleManager.reload(rules, collapsibleConfig.loadSettings())
			);
			Internal.getFileWatcher().addCallback(
				collapsibleConfig.getPath(),
				collapsibleRulesReloadController::onConfigFileChanged
			);
			Internal.getFileWatcher().addDirectoryCallback(
				configData.configDir(),
				path -> path.getFileName().toString().startsWith("collapsible-items-"),
				() -> configFileImporter.importFiles("collapsible-items-", collapsibleConfig.getPath())
			);
		}

		ClientCraftingGridClickRunner clientCraftingGridClickRunner = new ClientCraftingGridClickRunner();
		BookmarkAutoCraftingRunner bookmarkAutoCraftingRunner = new BookmarkAutoCraftingRunner();
		PacketCraftingGridCraftAck.setListener(ack -> bookmarkAutoCraftingRunner.handleAck(ack.taskId(), ack.requestId(), ack.craftedCount()));

		GuiEventHandler guiEventHandler = new GuiEventHandler(
			screenHelper,
			bookmarkOverlay,
			ingredientListOverlay,
			bookmarkAutoCraftingRunner,
			clientCraftingGridClickRunner
		);

		RecipesGui recipesGui = new RecipesGui(
			recipeManager,
			recipeTransferManager,
			ingredientManager,
			keyMappings,
			focusFactory,
			bookmarkList,
			lookupHistory,
			guiHelper,
			favoriteRecipes,
			favoriteRecipeConfig,
			favoriteTreeBookmarkWriter,
			clientCraftingGridClickRunner,
			bookmarkOverlay::showBookmarkPanel,
			bookmarkOverlay::showFavoritePanel
		);
		registration.setRecipesGui(recipesGui);

		CombinedRecipeFocusSource recipeFocusSource = new CombinedRecipeFocusSource(
			recipesGui,
			ingredientListOverlay,
			bookmarkOverlay,
			new GuiContainerWrapper(screenHelper)
		);

		List<ICharTypedHandler> charTypedHandlers = List.of(
			ingredientListOverlay,
			bookmarkOverlay
		);

		FocusUtil focusUtil = new FocusUtil(focusFactory, clientConfig, ingredientManager);

		UserInputRouter userInputRouter = new UserInputRouter(
			"JEIGlobal",
			new EditInputHandler(recipeFocusSource, toggleState, editModeConfig),
			ingredientListOverlay.createInputHandler(),
			bookmarkOverlay.createInputHandler(),
			new BookmarkInputHandler(recipeFocusSource, bookmarkList, bookmarkOverlay, ingredientManager,
				serverConnection,
				bookmarkAutoCraftingRunner,
				clientCraftingGridClickRunner,
				recipe -> favoriteTreeBookmarkWriter.save(recipe, clientConfig.getFavoriteTreeDepth()),
				favoriteRecipes::getFavorite,
				clientConfig,
				recipesGui),
			new FocusInputHandler(recipeFocusSource, recipesGui, focusUtil, clientConfig, ingredientManager, recipeManager, focusFactory, toggleState, serverConnection, bookmarkOverlay.getScrollStep()),
			new GlobalInputHandler(toggleState),
			new GuiAreaInputHandler(screenHelper, recipesGui, focusFactory)
		);

		DragRouter dragRouter = new DragRouter(
			ingredientListOverlay.createDragHandler(),
			bookmarkOverlay.createDragHandler()
		);
		ClientInputHandler clientInputHandler = new ClientInputHandler(
			charTypedHandlers,
			new ChatLinkInputHandler(recipesGui, focusUtil, screenHelper, bookmarkList),
			userInputRouter,
			dragRouter,
			keyMappings,
			screenHelper
		);
		ResourceReloadHandler resourceReloadHandler = new ResourceReloadHandler(
			ingredientListOverlay,
			ingredientFilter
		);

		return new JeiEventHandlers(
			guiEventHandler,
			clientInputHandler,
			resourceReloadHandler
		);
	}
}
