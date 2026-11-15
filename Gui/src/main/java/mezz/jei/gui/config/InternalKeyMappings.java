package mezz.jei.gui.config;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.keys.IJeiKeyMappingCategoryBuilder;
import mezz.jei.common.input.keys.IJeiKeyMappingInternal;
import mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers;
import mezz.jei.common.input.keys.JeiKeyConflictContext;
import mezz.jei.common.input.keys.JeiKeyModifier;
import mezz.jei.common.input.keys.JeiMultiKeyMapping;
import mezz.jei.common.platform.IPlatformInputHelper;
import mezz.jei.common.platform.Services;
import mezz.jei.common.util.Translator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class InternalKeyMappings implements IInternalKeyMappings {
	private final IJeiKeyMappingInternal toggleOverlay;
	private final IJeiKeyMappingInternal focusSearch;
	private final IJeiKeyMappingInternal toggleCheatMode;
	private final IJeiKeyMappingInternal toggleEditMode;

	private final IJeiKeyMappingInternal toggleCheatModeConfigButton;
	private final IJeiKeyMappingInternal toggleHideIngredientsWhenNotSearching;

	private final IJeiKeyMappingInternal recipeBack;
	private final IJeiKeyMappingInternal recipeForward;
	private final IJeiKeyMappingInternal previousCategory;
	private final IJeiKeyMappingInternal nextCategory;
	private final IJeiKeyMappingInternal previousRecipePage;
	private final IJeiKeyMappingInternal nextRecipePage;
	private final IJeiKeyMappingInternal pauseRecipeCycling;

	private final IJeiKeyMappingInternal previousPage;
	private final IJeiKeyMappingInternal nextPage;

	private final IJeiKeyMappingInternal bookmark;
	private final IJeiKeyMappingInternal selectRecipeInput;
	private final IJeiKeyMappingInternal favoriteRecipe;
	private final IJeiKeyMappingInternal bookmarkPullItems;
	private final IJeiKeyMappingInternal overlayRecipe;
	private final IJeiKeyMappingInternal craftItems;
	private final IJeiKeyMappingInternal encodeRecipeChainPatterns;
	private final IJeiKeyMappingInternal showRecipeTree;
	private final IJeiKeyMappingInternal toggleBookmarkOverlay;
	private final IJeiKeyMappingInternal transferRecipeBookmark;
	private final IJeiKeyMappingInternal maxTransferRecipeBookmark;
	private final IJeiKeyMappingInternal showBookmarkTooltipFeatures;
	private final IJeiKeyMappingInternal quickMove;
	private final IJeiKeyMappingInternal searchIngredientInTerminal;
	private final IJeiKeyMappingInternal shareToChat;
	private final IJeiKeyMappingInternal bookmarkWorldTarget;
	private final IJeiKeyMappingInternal showWorldTargetRecipe;
	private final IJeiKeyMappingInternal showWorldTargetUses;
	private final IJeiKeyMappingInternal transferRecipeGui;
	private final IJeiKeyMappingInternal maxTransferRecipeGui;

	private final IJeiKeyMappingWithExtraModifiers showRecipe;
	private final IJeiKeyMappingWithExtraModifiers showUses;
	private final IJeiKeyMappingInternal copyIngredientName;
	private final IJeiKeyMappingInternal copyIngredientTags;
	private final IJeiKeyMappingInternal copyIngredientComponents;
	private final IJeiKeyMappingInternal copyIngredientId;

	private final IJeiKeyMapping cheatOneItem;
	private final IJeiKeyMapping cheatItemStack;
	private final IJeiKeyMappingInternal cheatOneItemToInventory;
	private final IJeiKeyMappingInternal cheatItemStackToInventory;

	private final IJeiKeyMappingInternal toggleHideIngredient;
	private final IJeiKeyMappingInternal toggleWildcardHideIngredient;

	private final IJeiKeyMappingInternal hoveredClearSearchBar;
	private final IJeiKeyMappingInternal previousSearch;
	private final IJeiKeyMappingInternal nextSearch;

	private final IJeiKeyMappingInternal copyRecipeId;

	private final IJeiKeyMappingInternal closeRecipeGui;

	// internal only, unregistered and can't be changed because they match vanilla Minecraft hard-coded keys:
	private final IJeiKeyMapping escapeKey;
	private final IJeiKeyMapping leftClick;
	private final IJeiKeyMapping rightClick;
	private final IJeiKeyMapping enterKey;
	private final IJeiKeyMapping tabKey;

	private final List<KeyMapping> configKeyMappings;

	private static int getDefaultBookmarkTooltipFeaturesKey() {
		if (Minecraft.ON_OSX) {
			return GLFW.GLFW_KEY_LEFT_SUPER;
		}
		return GLFW.GLFW_KEY_LEFT_SHIFT;
	}

	public InternalKeyMappings(Consumer<KeyMapping> registerMethod) {
		IPlatformInputHelper inputHelper = Services.PLATFORM.getInputHelper();

		IJeiKeyMappingInternal showRecipe1;
		IJeiKeyMappingInternal showRecipe2;
		IJeiKeyMappingInternal showUses1;
		IJeiKeyMappingInternal showUses2;
		IJeiKeyMappingInternal cheatOneItem1;
		IJeiKeyMappingInternal cheatOneItem2;
		IJeiKeyMappingInternal cheatItemStack1;
		IJeiKeyMappingInternal cheatItemStack2;

		String overlaysCategoryName = Translator.translateToLocal("jei.key.category.overlays");
		IJeiKeyMappingCategoryBuilder overlay = inputHelper.createKeyMappingCategoryBuilder(overlaysCategoryName);

		String mouseHoverCategoryName = Translator.translateToLocal("jei.key.category.mouse.hover");
		IJeiKeyMappingCategoryBuilder mouseHover = inputHelper.createKeyMappingCategoryBuilder(mouseHoverCategoryName);
		String worldTargetCategoryName = Translator.translateToLocal("jei.key.category.world.target");
		IJeiKeyMappingCategoryBuilder worldTarget = inputHelper.createKeyMappingCategoryBuilder(worldTargetCategoryName);

		String searchCategoryName = Translator.translateToLocal("jei.key.category.search");
		IJeiKeyMappingCategoryBuilder search = inputHelper.createKeyMappingCategoryBuilder(searchCategoryName);

		String cheatModeCategoryName = Translator.translateToLocal("jei.key.category.cheat.mode");
		IJeiKeyMappingCategoryBuilder cheat = inputHelper.createKeyMappingCategoryBuilder(cheatModeCategoryName);

		String hoverConfigButtonCategoryName = Translator.translateToLocal("jei.key.category.hover.config.button");
		IJeiKeyMappingCategoryBuilder hoverConfig = inputHelper.createKeyMappingCategoryBuilder(hoverConfigButtonCategoryName);

		String editModeCategoryName = Translator.translateToLocal("jei.key.category.edit.mode");
		IJeiKeyMappingCategoryBuilder editMode = inputHelper.createKeyMappingCategoryBuilder(editModeCategoryName);

		String recipeCategoryName = Translator.translateToLocal("jei.key.category.recipe.gui");
		IJeiKeyMappingCategoryBuilder recipeCategory = inputHelper.createKeyMappingCategoryBuilder(recipeCategoryName);

		String devToolsCategoryName = Translator.translateToLocal("jei.key.category.dev.tools");
		IJeiKeyMappingCategoryBuilder devTools = inputHelper.createKeyMappingCategoryBuilder(devToolsCategoryName);

		// Overlay
		toggleOverlay = overlay.createMapping("key.jei.toggleOverlay")
			.setContext(JeiKeyConflictContext.GUI)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_O)
			.register(registerMethod);

		focusSearch = overlay.createMapping("key.jei.focusSearch")
			.setContext(JeiKeyConflictContext.GUI)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_F)
			.register(registerMethod);

		previousPage = overlay.createMapping("key.jei.previousPage")
			.setContext(JeiKeyConflictContext.GUI)
			.buildUnbound()
			.register(registerMethod);

		nextPage = overlay.createMapping("key.jei.nextPage")
			.setContext(JeiKeyConflictContext.GUI)
			.buildUnbound()
			.register(registerMethod);

		toggleBookmarkOverlay = overlay.createMapping("key.jei.toggleBookmarkOverlay")
			.setContext(JeiKeyConflictContext.GUI)
			.buildUnbound()
			.register(registerMethod);

		// Mouse Hover
		bookmark = mouseHover.createMapping("key.jei.bookmark")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.buildKeyboardKey(GLFW.GLFW_KEY_A)
			.register(registerMethod);

		favoriteRecipe = mouseHover.createMapping("key.jei.favoriteRecipe")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.buildKeyboardKey(GLFW.GLFW_KEY_F)
			.register(registerMethod);

		bookmarkPullItems = mouseHover.createMapping("key.jei.bookmarkPullItems")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.buildKeyboardKey(GLFW.GLFW_KEY_V)
			.register(registerMethod);

		overlayRecipe = mouseHover.createMapping("key.jei.overlayRecipe")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.buildKeyboardKey(GLFW.GLFW_KEY_S)
			.register(registerMethod);

		craftItems = mouseHover.createMapping("key.jei.craftItems")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.buildKeyboardKey(GLFW.GLFW_KEY_C)
			.register(registerMethod);

		encodeRecipeChainPatterns = mouseHover.createMapping("key.jei.encodeRecipeChainPatterns")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_Q)
			.register(registerMethod);

		showRecipeTree = mouseHover.createMapping("key.jei.showRecipeTree")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.setModifier(JeiKeyModifier.SHIFT)
			.buildKeyboardKey(GLFW.GLFW_KEY_B)
			.register(registerMethod);

		showRecipe1 = mouseHover.createMapping("key.jei.showRecipe")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.buildKeyboardKey(GLFW.GLFW_KEY_R)
			.register(registerMethod);

		showRecipe2 = mouseHover.createMapping("key.jei.showRecipe2")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.buildMouseLeft()
			.register(registerMethod);

		showUses1 = mouseHover.createMapping("key.jei.showUses")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.buildKeyboardKey(GLFW.GLFW_KEY_U)
			.register(registerMethod);

		showUses2 = mouseHover.createMapping("key.jei.showUses2")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.buildMouseRight()
			.register(registerMethod);

		copyIngredientName = mouseHover.createMapping("key.jei.copyIngredientName")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_C)
			.register(registerMethod);

		copyIngredientTags = mouseHover.createMapping("key.jei.copyIngredientTags")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_D)
			.register(registerMethod);

		copyIngredientComponents = mouseHover.createMapping("key.jei.copyIngredientComponents")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_S)
			.register(registerMethod);

		copyIngredientId = mouseHover.createMapping("key.jei.copyIngredientId")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_X)
			.register(registerMethod);

		transferRecipeBookmark = mouseHover.createMapping("key.jei.transferRecipeBookmark")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_BOOKMARK)
			.setModifier(JeiKeyModifier.SHIFT)
			.buildMouseLeft()
			.register(registerMethod);

		maxTransferRecipeBookmark = mouseHover.createMapping("key.jei.maxTransferRecipeBookmark")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_BOOKMARK)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildMouseLeft()
			.register(registerMethod);

		showBookmarkTooltipFeatures = mouseHover.createMapping("key.jei.showBookmarkTooltipFeatures")
			.setContext(JeiKeyConflictContext.GUI)
			.buildKeyboardKey(getDefaultBookmarkTooltipFeaturesKey())
			.register(registerMethod);

		transferRecipeGui = recipeCategory.createMapping("key.jei.transferRecipeGui")
			.setContext(JeiKeyConflictContext.GUI)
			.buildUnbound()
			.register(registerMethod);

		maxTransferRecipeGui = recipeCategory.createMapping("key.jei.maxTransferRecipeGui")
			.setContext(JeiKeyConflictContext.GUI)
			.buildUnbound()
			.register(registerMethod);

		quickMove = mouseHover.createMapping("key.jei.quickMove")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.setModifier(JeiKeyModifier.SHIFT)
			.buildMouseLeft()
			.register(registerMethod);

		searchIngredientInTerminal = mouseHover.createMapping("key.jei.searchIngredientInTerminal")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_G)
			.register(registerMethod);

		shareToChat = mouseHover.createMapping("key.jei.shareToChat")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_L)
			.register(registerMethod);

		bookmarkWorldTarget = worldTarget.createMapping("key.jei.bookmarkWorldTarget")
			.setContext(JeiKeyConflictContext.IN_GAME)
			.buildUnbound()
			.register(registerMethod);

		showWorldTargetRecipe = worldTarget.createMapping("key.jei.showWorldTargetRecipe")
			.setContext(JeiKeyConflictContext.IN_GAME)
			.buildUnbound()
			.register(registerMethod);

		showWorldTargetUses = worldTarget.createMapping("key.jei.showWorldTargetUses")
			.setContext(JeiKeyConflictContext.IN_GAME)
			.buildUnbound()
			.register(registerMethod);

		// Search Bar
		hoveredClearSearchBar = search.createMapping("key.jei.clearSearchBar")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_SEARCH)
			.buildMouseRight()
			.register(registerMethod);

		previousSearch = search.createMapping("key.jei.previousSearch")
			.setContext(JeiKeyConflictContext.JEI_GUI_FOCUSED_SEARCH)
			.setModifier(JeiKeyModifier.ALT)
			.buildKeyboardKey(GLFW.GLFW_KEY_UP)
			.register(registerMethod);

		nextSearch = search.createMapping("key.jei.nextSearch")
			.setContext(JeiKeyConflictContext.JEI_GUI_FOCUSED_SEARCH)
			.setModifier(JeiKeyModifier.ALT)
			.buildKeyboardKey(GLFW.GLFW_KEY_DOWN)
			.register(registerMethod);

		// Cheat Mode
		toggleCheatMode = cheat.createMapping("key.jei.toggleCheatMode")
			.setContext(JeiKeyConflictContext.GUI)
			.buildUnbound()
			.register(registerMethod);

		cheatOneItem1 = cheat.createMapping("key.jei.cheatOneItem")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_CHEAT_MODE)
			.buildMouseLeft()
			.register(registerMethod);

		cheatOneItem2 = cheat.createMapping("key.jei.cheatOneItem2")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_CHEAT_MODE)
			.buildMouseRight()
			.register(registerMethod);

		cheatItemStack1 = cheat.createMapping("key.jei.cheatItemStack")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_CHEAT_MODE)
			.setModifier(JeiKeyModifier.SHIFT)
			.buildMouseLeft()
			.register(registerMethod);

		cheatItemStack2 = cheat.createMapping("key.jei.cheatItemStack2")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_CHEAT_MODE)
			.buildMouseMiddle()
			.register(registerMethod);

		cheatOneItemToInventory = cheat.createMapping("key.jei.cheatOneItemToInventory")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_CHEAT_MODE)
			.setModifier(JeiKeyModifier.ALT)
			.buildMouseRight()
			.register(registerMethod);

		cheatItemStackToInventory = cheat.createMapping("key.jei.cheatItemStackToInventory")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_CHEAT_MODE)
			.setModifier(JeiKeyModifier.ALT)
			.buildMouseLeft()
			.register(registerMethod);

		// Hovering over config button
		toggleCheatModeConfigButton = hoverConfig.createMapping("key.jei.toggleCheatModeConfigButton")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_CONFIG_BUTTON)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildMouseLeft()
			.register(registerMethod);
		toggleHideIngredientsWhenNotSearching = hoverConfig.createMapping("key.jei.toggleHideIngredientsWhenNotSearching")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_CONFIG_BUTTON)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildMouseRight()
			.register(registerMethod);

		// Edit Mode
		toggleEditMode = editMode.createMapping("key.jei.toggleEditMode")
			.setContext(JeiKeyConflictContext.GUI)
			.buildUnbound()
			.register(registerMethod);

		toggleHideIngredient = editMode.createMapping("key.jei.toggleHideIngredient")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_INGREDIENT)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildMouseLeft()
			.register(registerMethod);

		toggleWildcardHideIngredient = editMode.createMapping("key.jei.toggleWildcardHideIngredient")
			.setContext(JeiKeyConflictContext.JEI_GUI_HOVER_INGREDIENT)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildMouseRight()
			.register(registerMethod);

		// Recipes
		recipeBack = recipeCategory.createMapping("key.jei.recipeBack")
			.setContext(JeiKeyConflictContext.GUI)
			.buildKeyboardKey(GLFW.GLFW_KEY_BACKSPACE)
			.register(registerMethod);

		recipeForward = recipeCategory.createMapping("key.jei.recipeForward")
			.setContext(JeiKeyConflictContext.GUI)
			.buildUnbound()
			.register(registerMethod);

		previousRecipePage = recipeCategory.createMapping("key.jei.previousRecipePage")
			.setContext(JeiKeyConflictContext.GUI)
			.buildKeyboardKey(GLFW.GLFW_KEY_PAGE_UP)
			.register(registerMethod);

		nextRecipePage = recipeCategory.createMapping("key.jei.nextRecipePage")
			.setContext(JeiKeyConflictContext.GUI)
			.buildKeyboardKey(GLFW.GLFW_KEY_PAGE_DOWN)
			.register(registerMethod);

		selectRecipeInput = recipeCategory.createMapping("key.jei.selectRecipeInput")
			.setContext(JeiKeyConflictContext.GUI)
			.buildMouseMiddle()
			.register(registerMethod);

		pauseRecipeCycling = recipeCategory.createMapping("key.jei.pauseRecipeCycling")
			.setContext(JeiKeyConflictContext.GUI)
			.buildKeyboardKey(GLFW.GLFW_KEY_LEFT_SHIFT)
			.register(registerMethod);

		previousCategory = recipeCategory.createMapping("key.jei.previousCategory")
			.setContext(JeiKeyConflictContext.GUI)
			.setModifier(JeiKeyModifier.SHIFT)
			.buildKeyboardKey(GLFW.GLFW_KEY_PAGE_UP)
			.register(registerMethod);

		nextCategory = recipeCategory.createMapping("key.jei.nextCategory")
			.setContext(JeiKeyConflictContext.GUI)
			.setModifier(JeiKeyModifier.SHIFT)
			.buildKeyboardKey(GLFW.GLFW_KEY_PAGE_DOWN)
			.register(registerMethod);

		closeRecipeGui = recipeCategory.createMapping("key.jei.closeRecipeGui")
			.setContext(JeiKeyConflictContext.GUI)
			.buildKeyboardKey(GLFW.GLFW_KEY_ESCAPE)
			.register(registerMethod);

		// Dev Tools
		copyRecipeId = devTools.createMapping("key.jei.copy.recipe.id")
			.setContext(JeiKeyConflictContext.GUI)
			.setModifier(JeiKeyModifier.CONTROL_OR_COMMAND)
			.buildKeyboardKey(GLFW.GLFW_KEY_T)
			.register(registerMethod);

		configKeyMappings = Stream.of(
				focusSearch,
				hoveredClearSearchBar,
				previousSearch,
				nextSearch,
				showRecipe1,
				showRecipe2,
				showUses1,
				showUses2,
				bookmark,
				selectRecipeInput,
				favoriteRecipe,
				bookmarkPullItems,
				overlayRecipe,
				craftItems,
				encodeRecipeChainPatterns,
				showRecipeTree,
				showBookmarkTooltipFeatures,
				searchIngredientInTerminal,
				bookmarkWorldTarget,
				showWorldTargetRecipe,
				showWorldTargetUses,
				copyIngredientName,
				copyIngredientTags,
				copyIngredientComponents,
				copyIngredientId,
				transferRecipeBookmark,
				maxTransferRecipeBookmark,
				transferRecipeGui,
				maxTransferRecipeGui,
				quickMove,
				shareToChat,
				toggleOverlay,
				toggleBookmarkOverlay,
				previousPage,
				nextPage,
				recipeBack,
				recipeForward,
				previousRecipePage,
				nextRecipePage,
				pauseRecipeCycling,
				previousCategory,
				nextCategory,
				closeRecipeGui,
				toggleCheatMode,
				toggleCheatModeConfigButton,
				toggleHideIngredientsWhenNotSearching,
				cheatOneItem1,
				cheatOneItem2,
				cheatItemStack1,
				cheatItemStack2,
				cheatOneItemToInventory,
				cheatItemStackToInventory,
				toggleEditMode,
				toggleHideIngredient,
				toggleWildcardHideIngredient,
				copyRecipeId
			)
			.map(IJeiKeyMappingInternal::getKeyMapping)
			.toList();

		showRecipe = new JeiMultiKeyMapping(showRecipe1, showRecipe2);
		showUses = new JeiMultiKeyMapping(showUses1, showUses2);
		cheatOneItem = new JeiMultiKeyMapping(cheatOneItem1, cheatOneItem2);
		cheatItemStack = new JeiMultiKeyMapping(cheatItemStack1, cheatItemStack2);

		String jeiHiddenInternalCategoryName = "jei.key.category.hidden.internal";
		IJeiKeyMappingCategoryBuilder jeiHidden = inputHelper.createKeyMappingCategoryBuilder(jeiHiddenInternalCategoryName);

		escapeKey = jeiHidden.createMapping("key.jei.internal.escape.key")
			.setContext(JeiKeyConflictContext.GUI)
			.buildKeyboardKey(GLFW.GLFW_KEY_ESCAPE);

		leftClick = jeiHidden.createMapping("key.jei.internal.left.click")
			.setContext(JeiKeyConflictContext.GUI)
			.buildMouseLeft();

		rightClick = jeiHidden.createMapping("key.jei.internal.right.click")
			.setContext(JeiKeyConflictContext.GUI)
			.buildMouseRight();

		enterKey = new JeiMultiKeyMapping(
			jeiHidden.createMapping("key.jei.internal.enter.key")
				.setContext(JeiKeyConflictContext.GUI)
				.buildKeyboardKey(GLFW.GLFW_KEY_ENTER),

			jeiHidden.createMapping("key.jei.internal.enter.key2")
				.setContext(JeiKeyConflictContext.GUI)
				.buildKeyboardKey(GLFW.GLFW_KEY_KP_ENTER)
		);

		tabKey = jeiHidden.createMapping("key.jei.internal.tab.key")
			.setContext(JeiKeyConflictContext.GUI)
			.buildKeyboardKey(GLFW.GLFW_KEY_TAB);
	}

	@Override
	public IJeiKeyMapping getToggleOverlay() {
		return toggleOverlay;
	}

	@Override
	public IJeiKeyMapping getFocusSearch() {
		return focusSearch;
	}

	@Override
	public IJeiKeyMapping getToggleCheatMode() {
		return toggleCheatMode;
	}

	@Override
	public IJeiKeyMapping getToggleEditMode() {
		return toggleEditMode;
	}

	@Override
	public IJeiKeyMapping getToggleCheatModeConfigButton() {
		return toggleCheatModeConfigButton;
	}

	@Override
	public IJeiKeyMapping getToggleHideIngredientsWhenNotSearching() {
		return toggleHideIngredientsWhenNotSearching;
	}

	@Override
	public IJeiKeyMapping getRecipeBack() {
		return recipeBack;
	}

	@Override
	public IJeiKeyMapping getRecipeForward() {
		return recipeForward;
	}

	@Override
	public IJeiKeyMapping getPreviousCategory() {
		return previousCategory;
	}

	@Override
	public IJeiKeyMapping getNextCategory() {
		return nextCategory;
	}

	@Override
	public IJeiKeyMapping getPreviousRecipePage() {
		return previousRecipePage;
	}

	@Override
	public IJeiKeyMapping getNextRecipePage() {
		return nextRecipePage;
	}

	@Override
	public IJeiKeyMappingInternal getPauseRecipeCycling() {
		return pauseRecipeCycling;
	}

	@Override
	public IJeiKeyMapping getPreviousPage() {
		return previousPage;
	}

	@Override
	public IJeiKeyMapping getNextPage() {
		return nextPage;
	}

	@Override
	public IJeiKeyMapping getCloseRecipeGui() {
		return closeRecipeGui;
	}

	@Override
	public IJeiKeyMappingWithExtraModifiers getBookmark() {
		return bookmark;
	}

	@Override
	public IJeiKeyMappingWithExtraModifiers getSelectRecipeInput() {
		return selectRecipeInput;
	}

	@Override
	public IJeiKeyMapping getFavoriteRecipe() {
		return favoriteRecipe;
	}

	@Override
	public IJeiKeyMapping getBookmarkPullItems() {
		return bookmarkPullItems;
	}

	@Override
	public IJeiKeyMapping getOverlayRecipe() {
		return overlayRecipe;
	}

	@Override
	public IJeiKeyMapping getCraftItems() {
		return craftItems;
	}

	@Override
	public IJeiKeyMapping getEncodeRecipeChainPatterns() {
		return encodeRecipeChainPatterns;
	}

	@Override
	public IJeiKeyMapping getShowRecipeTree() {
		return showRecipeTree;
	}

	@Override
	public IJeiKeyMapping getToggleBookmarkOverlay() {
		return toggleBookmarkOverlay;
	}

	@Override
	public IJeiKeyMappingWithExtraModifiers getShowRecipe() {
		return showRecipe;
	}

	@Override
	public IJeiKeyMappingWithExtraModifiers getShowUses() {
		return showUses;
	}

	@Override
	public IJeiKeyMapping getCopyIngredientName() {
		return copyIngredientName;
	}

	@Override
	public IJeiKeyMapping getCopyIngredientTags() {
		return copyIngredientTags;
	}

	@Override
	public IJeiKeyMapping getCopyIngredientComponents() {
		return copyIngredientComponents;
	}

	@Override
	public IJeiKeyMapping getCopyIngredientId() {
		return copyIngredientId;
	}

	@Override
	public IJeiKeyMapping getTransferRecipeBookmark() {
		return transferRecipeBookmark;
	}

	@Override
	public IJeiKeyMapping getMaxTransferRecipeBookmark() {
		return maxTransferRecipeBookmark;
	}

	@Override
	public IJeiKeyMappingInternal getShowBookmarkTooltipFeatures() {
		return showBookmarkTooltipFeatures;
	}

	@Override
	public IJeiKeyMapping getTransferRecipeGui() {
		return transferRecipeGui;
	}

	@Override
	public IJeiKeyMapping getMaxTransferRecipeGui() {
		return maxTransferRecipeGui;
	}

	@Override
	public IJeiKeyMapping getQuickMove() {
		return quickMove;
	}

	@Override
	public IJeiKeyMapping getSearchIngredientInTerminal() {
		return searchIngredientInTerminal;
	}

	@Override
	public IJeiKeyMapping getShareToChat() {
		return shareToChat;
	}

	@Override
	public IJeiKeyMapping getBookmarkWorldTarget() {
		return bookmarkWorldTarget;
	}

	@Override
	public IJeiKeyMapping getShowWorldTargetRecipe() {
		return showWorldTargetRecipe;
	}

	@Override
	public IJeiKeyMapping getShowWorldTargetUses() {
		return showWorldTargetUses;
	}

	@Override
	public IJeiKeyMapping getCheatOneItem() {
		return cheatOneItem;
	}

	@Override
	public IJeiKeyMapping getCheatItemStack() {
		return cheatItemStack;
	}

	@Override
	public IJeiKeyMapping getCheatOneItemToInventory() {
		return cheatOneItemToInventory;
	}

	@Override
	public IJeiKeyMapping getCheatItemStackToInventory() {
		return cheatItemStackToInventory;
	}

	@Override
	public IJeiKeyMapping getToggleHideIngredient() {
		return toggleHideIngredient;
	}

	@Override
	public IJeiKeyMapping getToggleWildcardHideIngredient() {
		return toggleWildcardHideIngredient;
	}

	@Override
	public IJeiKeyMapping getHoveredClearSearchBar() {
		return hoveredClearSearchBar;
	}

	@Override
	public IJeiKeyMapping getPreviousSearch() {
		return previousSearch;
	}

	@Override
	public IJeiKeyMapping getNextSearch() {
		return nextSearch;
	}

	@Override
	public IJeiKeyMapping getCopyRecipeId() {
		return copyRecipeId;
	}

	@Override
	public List<KeyMapping> getConfigKeyMappings() {
		return configKeyMappings;
	}

	@Override
	public IJeiKeyMapping getEscapeKey() {
		return escapeKey;
	}

	@Override
	public IJeiKeyMapping getLeftClick() {
		return leftClick;
	}

	@Override
	public IJeiKeyMapping getRightClick() {
		return rightClick;
	}

	@Override
	public IJeiKeyMapping getEnterKey() {
		return enterKey;
	}

	@Override
	public IJeiKeyMapping getTabKey() {
		return tabKey;
	}
}
