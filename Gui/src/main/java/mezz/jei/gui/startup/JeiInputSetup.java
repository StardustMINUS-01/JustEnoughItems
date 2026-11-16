package mezz.jei.gui.startup;

import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.runtime.IBookmarkManager;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.ClientInputHandler;
import mezz.jei.gui.input.GuiContainerWrapper;
import mezz.jei.gui.input.GuiInputController;
import mezz.jei.gui.input.GuiInputScene;
import mezz.jei.gui.input.IngredientFocusSource;
import mezz.jei.gui.input.InputArea;
import mezz.jei.gui.input.InputCommands;
import mezz.jei.gui.input.handlers.BookmarkCommands;
import mezz.jei.gui.input.handlers.IngredientTagSelectionTooltip;
import mezz.jei.gui.input.handlers.IngredientShortcutInputHandler;
import mezz.jei.gui.input.handlers.ChatLinkInputHandler;
import mezz.jei.gui.input.handlers.BookmarkInputHandler;
import mezz.jei.gui.input.handlers.ChatCommands;
import mezz.jei.gui.input.handlers.IngredientCommands;
import mezz.jei.gui.overlay.IngredientListOverlay;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.util.CheatModeUtil;
import mezz.jei.gui.util.FocusUtil;

/**
 * Input areas and Minecraft event handling for JEI's screens.
 *
 * @param scene the input areas, ordered from front to back
 * @param handler sends Minecraft input events to JEI's input controller
 */
public record JeiInputSetup(GuiInputScene scene, ClientInputHandler handler) {
	public record ForkInputs(
		mezz.jei.api.recipe.IRecipeManager recipeManager,
		mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingRunner autoCraftingRunner,
		java.util.function.Function<mezz.jei.gui.input.FocusedRecipe, java.util.Optional<Integer>> favoriteTreeSaver,
		java.util.function.Function<mezz.jei.gui.bookmarks.BookmarkIngredientKey, java.util.Optional<mezz.jei.gui.input.FocusedRecipe>> favoriteRecipeLookup,
		java.util.function.Predicate<String> groupImporter
	) {}

	public static JeiInputSetup create(
		IngredientListOverlay ingredientListOverlay,
		BookmarkOverlay bookmarkOverlay,
		RecipesGui recipesGui,
		BookmarkList bookmarkList,
		IBookmarkManager bookmarkManager,
		IScreenHelper screenHelper,
		IIngredientManager ingredientManager,
		IFocusFactory focusFactory,
		FocusUtil focusUtil,
		IClientConfig clientConfig,
		IClientToggleState toggleState,
		IEditModeConfig editModeConfig,
		IConnectionToServer serverConnection,
		IInternalKeyMappings keyMappings,
		ForkInputs forkInputs
	) {
		var searchInputLayer = ingredientListOverlay.getSearchInputLayer();
		InputArea searchInputArea = searchInputLayer.createInputArea(keyMappings);
		InputArea recipeTooltipInputArea = recipesGui.createTooltipInputArea();
		InputArea bookmarkTooltipInputArea = bookmarkOverlay.createTooltipInputArea();
		InputArea recipeInputArea = recipesGui.createInputArea(keyMappings);
		InputArea ingredientInputArea = ingredientListOverlay.createInputArea(keyMappings);
		InputArea bookmarkInputArea = bookmarkOverlay.createInputArea(keyMappings);
		GuiContainerWrapper containerWrapper = new GuiContainerWrapper(screenHelper);
		InputArea containerInputArea = containerWrapper.createInputArea(recipesGui, focusFactory);
		ChatCommands chatCommands = new ChatCommands(recipesGui, focusUtil, screenHelper, bookmarkManager, ingredientManager);
		InputArea chatInputArea = chatCommands.createInputArea(keyMappings);
		IngredientTagSelectionTooltip tagSelector = new IngredientTagSelectionTooltip();
		ChatLinkInputHandler links = new ChatLinkInputHandler(recipesGui, focusUtil, screenHelper, bookmarkList, forkInputs.groupImporter());
		GuiInputScene inputScene = new GuiInputScene(
			ingredientListOverlay.getKeyboardFocus(),
			tagSelector.createInputArea(),
			bookmarkOverlay.createChapterInputArea(),
			bookmarkOverlay.createQuantityInputArea(),
			recipesGui.createSearchInputArea(keyMappings),
			searchInputArea,
			recipeTooltipInputArea,
			bookmarkTooltipInputArea,
			recipeInputArea,
			ingredientInputArea,
			bookmarkInputArea,
			containerInputArea,
			links.createInputArea(),
			chatInputArea
		);

		InputCommands sharedCommands = new InputCommands(screen -> screenHelper.getGuiProperties(screen).isPresent());
		IngredientFocusSource focusSource = new IngredientFocusSource(inputScene);
		IngredientCommands ingredientCommands = new IngredientCommands(
			focusSource,
			keyMappings,
			recipesGui,
			focusUtil,
			ingredientManager,
			toggleState,
			editModeConfig,
			clientConfig,
			serverConnection
		);
		ingredientCommands.setQuantitySupplier(bookmarkOverlay.getScrollStep()::getValue);
		IngredientShortcutInputHandler ingredientShortcuts = new IngredientShortcutInputHandler(
			focusSource, recipesGui, focusUtil, clientConfig, ingredientManager, forkInputs.recipeManager(), focusFactory, tagSelector);
		mezz.jei.gui.chat.ChatRecipeTooltip.INSTANCE.setShortcuts(ingredientShortcuts);
		BookmarkInputHandler forkBookmarks = new BookmarkInputHandler(focusSource, bookmarkList, bookmarkOverlay, ingredientManager,
			serverConnection, forkInputs.autoCraftingRunner(), forkInputs.favoriteTreeSaver(), forkInputs.favoriteRecipeLookup(), inputScene, clientConfig, recipesGui);
		sharedCommands.add("Bookmark shortcuts", input -> true, (screen, input) -> forkBookmarks.beginInput(screen, input, keyMappings));
		sharedCommands.add("Ingredient shortcuts", input -> true, (screen, input) -> ingredientShortcuts.beginInput(screen, input, keyMappings));
		ingredientCommands.registerInputCommands(sharedCommands);
		BookmarkCommands bookmarkCommands = new BookmarkCommands(
			focusSource,
			bookmarkList,
			bookmarkOverlay,
			inputScene,
			clientConfig,
			recipesGui
		);
		bookmarkCommands.registerInputCommands(sharedCommands, keyMappings);
		sharedCommands.add(
			"Toggle Overlay",
			keyMappings.getToggleOverlay(),
			() -> true,
			toggleState::toggleOverlayEnabled
		);
		sharedCommands.add(
			"Toggle Bookmark Overlay",
			keyMappings.getToggleBookmarkOverlay(),
			() -> true,
			toggleState::toggleBookmarkEnabled
		);
		sharedCommands.add(
			"Toggle Cheat Mode",
			keyMappings.getToggleCheatMode(),
			() -> true,
			() -> CheatModeUtil.toggleCheatMode(toggleState)
		);
		sharedCommands.add(
			"Toggle Edit Mode",
			keyMappings.getToggleEditMode(),
			() -> true,
			toggleState::toggleEditModeEnabled
		);

		InputCommands commands = new InputCommands();
		commands.add("Shared shortcuts", input -> true,
			(screen, input) -> sharedCommands.resolve(screen, input, false));
		for (InputArea inputArea : inputScene.getInputAreas()) {
			commands.add(inputArea.name() + " screen shortcuts",
				input -> inputScene.getTarget(input.getMouseX(), input.getMouseY()).orElse(null) != inputArea,
				(screen, input) -> inputArea.commands().resolve(screen, input, false));
		}
		commands.add("Legacy recipe category callback",
			input -> inputScene.isTarget(recipesGui, input),
			(screen, input) -> recipesGui.getRecipeLayouts().beginLegacyCategoryInput(input));

		GuiInputController controller = new GuiInputController(inputScene, commands, searchInputLayer, keyMappings);
		ClientInputHandler handler = new ClientInputHandler(controller, keyMappings);
		return new JeiInputSetup(inputScene, handler);
	}
}
