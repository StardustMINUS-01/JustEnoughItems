package mezz.jei.gui.startup;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingRunner;
import mezz.jei.gui.config.BookmarkConfigEntry;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.InteractiveTooltipController;
import mezz.jei.gui.input.handlers.BookmarkInputHandler;
import mezz.jei.gui.input.handlers.ChatLinkInputHandler;
import mezz.jei.gui.input.handlers.CheatInputHandler;
import mezz.jei.gui.input.handlers.IngredientShortcutInputHandler;
import mezz.jei.gui.input.handlers.IngredientTagSelectionTooltip;
import mezz.jei.gui.overlay.bookmarks.ScrollStep;
import mezz.jei.gui.chat.ChatRecipeTooltip;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
		IInternalKeyMappings keyMappings
	) {
		return create(ingredientListOverlay, bookmarkOverlay, recipesGui, bookmarkList, bookmarkManager, screenHelper,
			ingredientManager, focusFactory, focusUtil, clientConfig, toggleState, editModeConfig, serverConnection, keyMappings, null);
	}

	public record ForkInputs(
		IRecipeManager recipeManager,
		BookmarkAutoCraftingRunner autoCraftingRunner,
		Function<FocusedRecipe, Optional<Integer>> favoriteTreeSaver,
		Function<BookmarkIngredientKey, Optional<FocusedRecipe>> favoriteRecipeLookup,
		Codec<BookmarkConfigEntry> bookmarkEntryCodec,
		DynamicOps<JsonElement> bookmarkRegistryOps,
		ScrollStep scrollStep
	) {
	}

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
		@Nullable ForkInputs forkInputs
	) {
		var searchInputLayer = ingredientListOverlay.getSearchInputLayer();
		InputArea searchInputArea = searchInputLayer.createInputArea(keyMappings);
		InputArea recipeTooltipInputArea;
		if (forkInputs == null) {
			recipeTooltipInputArea = recipesGui.createTooltipInputArea();
		} else {
			var recipeForeground = recipesGui.getForegroundInputLayer();
			recipeTooltipInputArea = InputArea.builder("Recipe foreground", recipeForeground)
				.controls(recipeForeground).blockUnhandledMouseInput().build();
		}
		InputArea bookmarkTooltipInputArea = bookmarkOverlay.createTooltipInputArea();
		InputArea recipeInputArea = recipesGui.createInputArea(keyMappings);
		InputArea ingredientInputArea = ingredientListOverlay.createInputArea(keyMappings);
		InputArea bookmarkInputArea = bookmarkOverlay.createInputArea(keyMappings);
		GuiContainerWrapper containerWrapper = new GuiContainerWrapper(screenHelper);
		InputArea containerInputArea = containerWrapper.createInputArea(recipesGui, focusFactory);
		ChatCommands chatCommands = new ChatCommands(recipesGui, focusUtil, screenHelper, bookmarkManager, ingredientManager);
		InputArea chatInputArea = chatCommands.createInputArea(keyMappings);
		IngredientTagSelectionTooltip tagSelectionTooltip = new IngredientTagSelectionTooltip();
		List<InputArea> areas = new ArrayList<>();
		areas.add(InteractiveTooltipController.INSTANCE.createInputArea());
		areas.add(tagSelectionTooltip.createInputArea());
		if (forkInputs != null) {
			areas.add(recipeTooltipInputArea);
			var chapterLayer = bookmarkOverlay.getChapterInputLayer();
			areas.add(InputArea.builder("Bookmark chapters", chapterLayer).controls(chapterLayer).blockUnhandledMouseInput().build());
		}
		areas.add(recipesGui.createSearchInputArea(keyMappings));
		areas.add(searchInputArea);
		if (forkInputs == null) {
			areas.add(recipeTooltipInputArea);
		}
		areas.addAll(List.of(
			bookmarkTooltipInputArea,
			recipeInputArea,
			bookmarkOverlay.createQuantityInputArea(),
			ingredientInputArea,
			bookmarkInputArea,
			containerInputArea
		));
		if (forkInputs != null) {
			ChatLinkInputHandler chatLinks = new ChatLinkInputHandler(recipesGui, focusUtil, screenHelper, bookmarkList,
				forkInputs.bookmarkEntryCodec(), forkInputs.bookmarkRegistryOps());
			areas.add(2, chatLinks.createInputArea());
		}
		areas.add(chatInputArea);
		GuiInputScene inputScene = new GuiInputScene(ingredientListOverlay.getKeyboardFocus(), areas.toArray(InputArea[]::new));

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
		if (forkInputs == null) {
			ingredientCommands.registerInputCommands(sharedCommands);
			BookmarkCommands bookmarkCommands = new BookmarkCommands(focusSource, bookmarkList, bookmarkOverlay, inputScene, clientConfig, recipesGui);
			bookmarkCommands.registerInputCommands(sharedCommands, keyMappings);
		} else {
			CheatInputHandler cheatInputHandler = new CheatInputHandler(focusSource, clientConfig, ingredientManager, toggleState, serverConnection, forkInputs.scrollStep());
			BookmarkInputHandler bookmarkInputHandler = new BookmarkInputHandler(
				focusSource, bookmarkList, bookmarkOverlay, ingredientManager, serverConnection, forkInputs.autoCraftingRunner(),
				forkInputs.favoriteTreeSaver(), forkInputs.favoriteRecipeLookup(), inputScene, clientConfig, recipesGui,
				forkInputs.bookmarkEntryCodec(), forkInputs.bookmarkRegistryOps()
			);
			IngredientShortcutInputHandler ingredientShortcuts = new IngredientShortcutInputHandler(
				focusSource, recipesGui, focusUtil, clientConfig, ingredientManager, forkInputs.recipeManager(), focusFactory, tagSelectionTooltip
			);
			ChatRecipeTooltip.INSTANCE.setShortcuts(ingredientShortcuts);
			ingredientCommands.registerInputCommands(sharedCommands, ingredientShortcuts, bookmarkInputHandler, cheatInputHandler);
		}

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
		commands.add("Recipe API callbacks",
			input -> inputScene.isTarget(recipesGui, input),
			(screen, input) -> recipesGui.getRecipeLayouts().beginApiInput(screen, input, keyMappings));

		GuiInputController controller = new GuiInputController(inputScene, commands, searchInputLayer, keyMappings);
		ClientInputHandler handler = new ClientInputHandler(controller, keyMappings, screenHelper);
		return new JeiInputSetup(inputScene, handler);
	}
}
