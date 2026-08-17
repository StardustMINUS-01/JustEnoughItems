package mezz.jei.gui.input.handlers;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.common.util.SaturatedMath;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.bookmarks.chain.BookmarkContainerPacketHandler;
import mezz.jei.gui.bookmarks.chain.BookmarkContainerPullExecutor;
import mezz.jei.gui.bookmarks.chain.BookmarkContainerStorageScanner;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingRunner;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyContext;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeySubject;
import mezz.jei.gui.bookmarks.hotkeys.ClientCraftingGridClickRunner;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.input.BookmarkKeyInputs;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.bookmarks.PlayerInventoryRecipeChainTooltipInventoryProvider;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class BookmarkInputHandler implements IUserInputHandler {
	private static final Logger LOGGER = LogManager.getLogger();
	private final CombinedRecipeFocusSource focusSource;
	private final BookmarkList bookmarkList;
	private final BookmarkOverlay bookmarkOverlay;
	private final IIngredientManager ingredientManager;
	private final IConnectionToServer serverConnection;
	private final BookmarkAutoCraftingRunner autoCraftingRunner;
	private final ClientCraftingGridClickRunner clientCraftingGridClickRunner;
	private final Function<FocusedRecipe, Optional<String>> favoriteTreeSaver;
	private final Function<BookmarkIngredientKey, Optional<FocusedRecipe>> favoriteRecipeLookup;
	private final IClientConfig clientConfig;
	private final RecipesGui recipesGui;

	public BookmarkInputHandler(
		CombinedRecipeFocusSource focusSource,
		BookmarkList bookmarkList,
		BookmarkOverlay bookmarkOverlay,
		IIngredientManager ingredientManager,
		IConnectionToServer serverConnection,
		BookmarkAutoCraftingRunner autoCraftingRunner,
		ClientCraftingGridClickRunner clientCraftingGridClickRunner,
		Function<FocusedRecipe, Optional<String>> favoriteTreeSaver,
		Function<BookmarkIngredientKey, Optional<FocusedRecipe>> favoriteRecipeLookup,
		IClientConfig clientConfig,
		RecipesGui recipesGui
	) {
		this.focusSource = focusSource;
		this.bookmarkList = bookmarkList;
		this.bookmarkOverlay = bookmarkOverlay;
		this.ingredientManager = ingredientManager;
		this.serverConnection = serverConnection;
		this.autoCraftingRunner = autoCraftingRunner;
		this.clientCraftingGridClickRunner = clientCraftingGridClickRunner;
		this.favoriteTreeSaver = favoriteTreeSaver;
		this.favoriteRecipeLookup = favoriteRecipeLookup;
		this.clientConfig = clientConfig;
		this.recipesGui = recipesGui;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (BookmarkAutoCraftingActivator.isAutoCraftingInput(input, keyBindings.getCraftItems())) {
			return handleBookmarkAutoCrafting(input, keyBindings.getCraftItems());
		}
		if (isBookmarkPullInput(input, keyBindings.getBookmarkPullItems())) {
			return handleBookmarkPull(input);
		}
		if (isFavoriteActionInput(input, keyBindings)) {
			Optional<IUserInputHandler> favoriteHandler = handleFavoriteRecipe(input, keyBindings);
			if (favoriteHandler.isPresent()) {
				return favoriteHandler;
			}
		}
		if (isBookmarkInput(input, keyBindings.getBookmark())) {
			return handleBookmark(input, keyBindings);
		}
		return Optional.empty();
	}

	private Optional<IUserInputHandler> handleBookmarkAutoCrafting(UserInput input, IJeiKeyMapping craftItemsKey) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen) || minecraft.player == null) {
			return Optional.empty();
		}
		Optional<String> groupId = bookmarkOverlay.getPullGroupIdUnderMouse(input.getMouseX(), input.getMouseY())
			.filter(bookmarkList::isGroupCraftingMode);
		if (groupId.isEmpty()) {
			return Optional.empty();
		}

		int targetSlotCount = BookmarkGhostOverlayTargetSlots.fromMenu(containerScreen.getMenu()).size();
		if (targetSlotCount <= 0) {
			return Optional.empty();
		}
		if (!BookmarkAutoCraftingActivator.claimAutoCraftingInput(input, craftItemsKey)) {
			return Optional.empty();
		}

		PlayerInventoryRecipeChainTooltipInventoryProvider inventoryProvider = new PlayerInventoryRecipeChainTooltipInventoryProvider(minecraft, ingredientManager);
		String hoveredGroupId = groupId.get();
		AbstractContainerMenu menu = containerScreen.getMenu();
		boolean craftAll = isCraftAllModifier(input.getModifiers());
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] INPUT-CRAFT groupId={} targetSlotCount={} craftAll={} containerId={} menu={} chainInputs={}",
				hoveredGroupId, targetSlotCount, craftAll, menu.containerId, menu.getClass().getSimpleName(),
				bookmarkList.getRecipeChainInputs(hoveredGroupId).size());
		}
		boolean handled;
		if (input.isSimulate()) {
			handled = BookmarkAutoCraftingBridge.activate(
				bookmarkList.getRecipeChainInputs(hoveredGroupId),
				bookmarkList.getCollapsedRecipeIds(hoveredGroupId),
				targetSlotCount,
				menu.containerId,
				() -> getAutoCraftingInventoryInputs(hoveredGroupId, inventoryProvider),
				inventoryProvider::getAvailableStacks,
				recipeUid -> bookmarkList.createRecipeLayoutDrawable(hoveredGroupId, recipeUid),
				serverConnection::sendPacketToServer,
				true,
				craftAll
			);
		} else {
			if (serverConnection.isJeiOnServer()) {
				handled = BookmarkAutoCraftingBridge.createTask(
					bookmarkList.getRecipeChainInputs(hoveredGroupId),
					bookmarkList.getCollapsedRecipeIds(hoveredGroupId),
					targetSlotCount,
					menu.containerId,
					() -> getAutoCraftingInventoryInputs(hoveredGroupId, inventoryProvider),
					inventoryProvider::getAvailableStacks,
					recipeUid -> bookmarkList.createRecipeLayoutDrawable(hoveredGroupId, recipeUid),
					serverConnection::sendPacketToServer,
					() -> Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> activeScreen &&
						activeScreen.getMenu() == menu,
					craftAll
				)
				.map(autoCraftingRunner::start)
				.orElse(false);
			} else {
				handled = BookmarkAutoCraftingBridge.createClientFallbackTask(
						bookmarkList.getRecipeChainInputs(hoveredGroupId),
						bookmarkList.getCollapsedRecipeIds(hoveredGroupId),
						targetSlotCount,
						menu,
						() -> getAutoCraftingInventoryInputs(hoveredGroupId, inventoryProvider),
						inventoryProvider::getAvailableStacks,
						recipeUid -> bookmarkList.createRecipeLayoutDrawable(hoveredGroupId, recipeUid),
						clientCraftingGridClickRunner,
						clientCraftingGridClickRunner::consumeLastResult,
						() -> Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> activeScreen &&
							activeScreen.getMenu() == menu,
						craftAll
					)
					.map(autoCraftingRunner::start)
					.orElse(false);
			}
		}
		if (!handled) {
			BookmarkAutoCraftingActivator.releaseAutoCraftingInput(input.getKey());
			return Optional.empty();
		}
		if (!input.isSimulate()) {
			JeiClientSoundUtil.playClickSound();
		}
		IUserInputHandler handler = new SameElementInputHandler(this, bookmarkOverlay::isMouseOver);
		return Optional.of(handler);
	}

	private List<RecipeChainInput> getAutoCraftingInventoryInputs(
		String groupId,
		PlayerInventoryRecipeChainTooltipInventoryProvider inventoryProvider
	) {
		return List.copyOf(inventoryProvider.getInventoryInputs(groupId, -1));
	}

	public static boolean isCraftAllModifier(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_CONTROL) == 0;
	}

	public static boolean isBookmarkInput(UserInput input, IJeiKeyMapping bookmarkKey) {
		if (input.is(bookmarkKey)) {
			return true;
		}
		int modifiers = input.getModifiers();
		return !hasAlt(modifiers) &&
			hasShiftOrControl(modifiers) &&
			bookmarkKey.matchesIgnoringModifiers(input.getKey());
	}

	private static boolean isFavoriteActionInput(UserInput input, IInternalKeyMappings keyBindings) {
		IJeiKeyMapping recipeFavoriteKey = keyBindings.getFavoriteRecipe();
		if (input.is(recipeFavoriteKey)) {
			return true;
		}
		return BookmarkKeyInputs.isBookmarkKeyWithoutControlOrAlt(input, keyBindings);
	}

	private static boolean isBookmarkPullInput(UserInput input, IJeiKeyMapping bookmarkPullKey) {
		if (input.is(bookmarkPullKey)) {
			return true;
		}
		int modifiers = input.getModifiers();
		return hasShift(modifiers) && !hasControlOrAlt(modifiers) && bookmarkPullKey.matchesIgnoringModifiers(input.getKey());
	}

	private Optional<IUserInputHandler> handleBookmarkPull(UserInput input) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen) || minecraft.player == null) {
			return Optional.empty();
		}
		Optional<String> groupId = bookmarkOverlay.getPullGroupIdUnderMouse(input.getMouseX(), input.getMouseY());
		if (groupId.isEmpty()) {
			return Optional.empty();
		}
		if (!input.isSimulate()) {
			JeiClientSoundUtil.playClickSound();
			Inventory playerInventory = minecraft.player.getInventory();
			BookmarkContainerStorageScanner.StorageSnapshot snapshot = BookmarkContainerStorageScanner.scan(
				containerScreen.getMenu(),
				playerInventory,
				this::createKey
			);
			snapshot = BookmarkExternalStorageSnapshots.scan(containerScreen.getMenu(), containerScreen, this::createKey)
				.orElse(snapshot);
			BookmarkContainerPacketHandler handler = new BookmarkContainerPacketHandler(
				snapshot.amounts(),
				snapshot.representatives(),
				containerScreen.getMenu().containerId,
				serverConnection::sendPacketToServer
			);
			BookmarkContainerPullExecutor.pull(
				new BookmarkContainerPullExecutor.Request(
					bookmarkList.getRecipeChainInputs(groupId.get()),
					bookmarkList.getCollapsedRecipeIds(groupId.get()),
					getPlayerInventoryAmounts(playerInventory),
					getFreePlayerInventorySlots(playerInventory),
					playerInventory.getMaxStackSize(),
					InputModifiers.hasShift(input)
				),
				handler
			);
		}
		IUserInputHandler handler = new SameElementInputHandler(this, bookmarkOverlay::isMouseOver);
		return Optional.of(handler);
	}

	private Optional<IUserInputHandler> handleFavoriteRecipe(UserInput input, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.findFirst()
			.flatMap(clicked -> {
				Optional<Boolean> favoriteElementHandled = resolveFavoriteRecipeElementAction(clicked.getElement(), input, keyBindings)
					.flatMap(action -> handleFavoriteRecipeAction(clicked.getElement(), input, keyBindings, action));
				if (favoriteElementHandled.isPresent()) {
					return Optional.of(new SameElementInputHandler(this, clicked::isMouseOver));
				}

				Optional<Boolean> favoriteIngredientHandled = resolveFavoriteIngredientTreeRecipe(
					clicked.getElement(),
					input,
					keyBindings,
					ingredient -> Optional.of(createKey(ingredient)),
					favoriteRecipeLookup
				)
					.flatMap(recipe -> handleFavoriteTreeSave(input, recipe));
				return favoriteIngredientHandled
					.map(handled -> new SameElementInputHandler(this, clicked::isMouseOver));
			});
	}

	private Optional<Boolean> handleFavoriteRecipeAction(
		IElement<?> element,
		UserInput input,
		IInternalKeyMappings keyBindings,
		FavoriteRecipeHotkeyAction action
	) {
		if (!(element instanceof FavoriteRecipeElement<?> favoriteRecipeElement)) {
			return Optional.empty();
		}
		if (input.isSimulate()) {
			return Optional.of(true);
		}
		boolean handled = switch (action) {
			case REMOVE_FAVORITE -> favoriteRecipeElement.handleClick(input, keyBindings);
			case SAVE_FAVORITE_TREE -> favoriteTreeSaver.apply(favoriteRecipeElement.getFocusedRecipe()).isPresent();
		};
		if (!handled) {
			return Optional.empty();
		}
		if (action == FavoriteRecipeHotkeyAction.SAVE_FAVORITE_TREE) {
			bookmarkOverlay.showBookmarkPanel();
		}
		JeiClientSoundUtil.playClickSound();
		return Optional.of(true);
	}

	private Optional<Boolean> handleFavoriteTreeSave(UserInput input, FocusedRecipe recipe) {
		if (input.isSimulate()) {
			return Optional.of(true);
		}
		if (favoriteTreeSaver.apply(recipe).isEmpty()) {
			return Optional.empty();
		}
		bookmarkOverlay.showBookmarkPanel();
		JeiClientSoundUtil.playClickSound();
		return Optional.of(true);
	}

	public static Optional<FavoriteRecipeHotkeyAction> resolveFavoriteRecipeElementAction(
		IElement<?> element,
		UserInput input,
		IInternalKeyMappings keyBindings
	) {
		if (!(element instanceof FavoriteRecipeElement<?>)) {
			return Optional.empty();
		}
		if (!BookmarkKeyInputs.isBookmarkKeyWithoutControlOrAlt(input, keyBindings)) {
			return Optional.empty();
		}
		if (InputModifiers.hasShift(input.getModifiers())) {
			return Optional.of(FavoriteRecipeHotkeyAction.SAVE_FAVORITE_TREE);
		}
		return Optional.of(FavoriteRecipeHotkeyAction.REMOVE_FAVORITE);
	}

	public static Optional<FocusedRecipe> resolveFavoriteIngredientTreeRecipe(
		IElement<?> element,
		UserInput input,
		IInternalKeyMappings keyBindings,
		Function<ITypedIngredient<?>, Optional<BookmarkIngredientKey>> keyFactory,
		Function<BookmarkIngredientKey, Optional<FocusedRecipe>> favoriteRecipeLookup
	) {
		if (!(element instanceof FavoriteRecipeElement<?>)) {
			return Optional.empty();
		}
		if (!InputModifiers.hasShift(input.getModifiers()) || !BookmarkKeyInputs.isBookmarkKeyWithoutControlOrAlt(input, keyBindings)) {
			return Optional.empty();
		}
		return keyFactory.apply(element.getTypedIngredient())
			.flatMap(favoriteRecipeLookup);
	}

	public enum FavoriteRecipeHotkeyAction {
		REMOVE_FAVORITE,
		SAVE_FAVORITE_TREE
	}

	private Optional<IUserInputHandler> handleRecipeBookmark(UserInput input) {
		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		Optional<IRecipeLayoutWithButtons<?>> layoutWithButtons = recipesGui.getRecipeLayoutUnderMouse(mouseX, mouseY);
		if (layoutWithButtons.isEmpty()) {
			return Optional.empty();
		}

		IRecipeLayoutWithButtons<?> recipeLayoutWithButtons = layoutWithButtons.get();
		RecipeBookmark<?, ?> recipeBookmark = recipeLayoutWithButtons.getRecipeBookmark();
		if (recipeBookmark == null) {
			return Optional.empty();
		}

		IRecipeLayoutDrawable<?> layout = recipeLayoutWithButtons.getRecipeLayout();
		Optional<RecipeSlotUnderMouse> slotUnderMouse = layout.getSlotUnderMouse(mouseX, mouseY);
		if (!shouldBookmarkRecipe(slotUnderMouse, clientConfig.isBookmarkOutputAsRecipeEnabled())) {
			return Optional.empty();
		}

		if (!input.isSimulate()) {
			bookmarkList.toggleBookmark(recipeBookmark);
		}
		return Optional.of(new SameElementInputHandler(this, layout::isMouseOver));
	}

	static boolean shouldBookmarkRecipe(Optional<RecipeSlotUnderMouse> slotUnderMouse, boolean bookmarkOutputAsRecipeEnabled) {
		return slotUnderMouse
			.map(slot -> shouldBookmarkRecipe(slot.slot().getRole(), bookmarkOutputAsRecipeEnabled))
			.orElse(true);
	}

	static boolean shouldBookmarkRecipe(RecipeIngredientRole role, boolean bookmarkOutputAsRecipeEnabled) {
		return role == RecipeIngredientRole.OUTPUT && bookmarkOutputAsRecipeEnabled;
	}

	private Optional<IUserInputHandler> handleIngredientBookmark(UserInput input, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.findFirst()
			.flatMap(clicked -> {
				Optional<Boolean> favoriteElementHandled = resolveFavoriteRecipeElementAction(clicked.getElement(), input, keyBindings)
					.flatMap(action -> handleFavoriteRecipeAction(clicked.getElement(), input, keyBindings, action));
				if (favoriteElementHandled.isPresent()) {
					return Optional.of(new SameElementInputHandler(this, clicked::isMouseOver));
				}

				Optional<Boolean> favoriteIngredientHandled = resolveFavoriteIngredientTreeRecipe(
					clicked.getElement(),
					input,
					keyBindings,
					ingredient -> Optional.of(createKey(ingredient)),
					favoriteRecipeLookup
				)
					.flatMap(recipe -> handleFavoriteTreeSave(input, recipe));
				return favoriteIngredientHandled
					.map(handled -> new SameElementInputHandler(this, clicked::isMouseOver));
			});
	}

	private Map<BookmarkIngredientKey, Long> getPlayerInventoryAmounts(Inventory playerInventory) {
		Map<BookmarkIngredientKey, Long> amounts = new LinkedHashMap<>();
		for (ItemStack stack : playerInventory.items) {
			if (stack.isEmpty()) {
				continue;
			}
			createKey(stack).ifPresent(key -> amounts.merge(key, (long) stack.getCount(), SaturatedMath::add));
		}
		return Map.copyOf(amounts);
	}

	private int getFreePlayerInventorySlots(Inventory playerInventory) {
		int freeSlots = 0;
		for (ItemStack stack : playerInventory.items) {
			if (stack.isEmpty() || stack.getCount() < Math.min(playerInventory.getMaxStackSize(), stack.getMaxStackSize())) {
				freeSlots++;
			}
		}
		return freeSlots;
	}

	private Optional<BookmarkIngredientKey> createKey(ItemStack stack) {
		ItemStack normalized = stack.copy();
		normalized.setCount(1);
		return ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, normalized)
			.map(this::createKey);
	}

	private BookmarkIngredientKey createKey(ITypedIngredient<?> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
	}

	private Optional<IUserInputHandler> handleBookmark(UserInput input, IInternalKeyMappings keyBindings) {
		boolean shiftDown = InputModifiers.hasShift(input);
		boolean controlDown = InputModifiers.hasControl(input);
		if (shiftDown) {
			if (bookmarkOverlay.removeGroupUnderMouseGroupPanel(input)) {
				IUserInputHandler handler = new SameElementInputHandler(this, bookmarkOverlay::isMouseOver);
				return Optional.of(handler);
			}

			Minecraft minecraft = Minecraft.getInstance();
			Optional<BookmarkHotkeyAction> recipeBookmarkAction = BookmarkHotkeyRouter.resolveBookmarkKeyAction(
				BookmarkHotkeyContext.builder(BookmarkHotkeySubject.INGREDIENT)
					.hasIngredient(true)
					.hasRecipe(true)
					.build(),
				shiftDown,
				controlDown
			);
			boolean addRecipeWithAmount = recipeBookmarkAction
				.filter(action -> action == BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK_WITH_COUNT)
				.isPresent();
			boolean addRecipe = recipeBookmarkAction
				.filter(action -> action == BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK || action == BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK_WITH_COUNT)
				.isPresent();
			if (addRecipe && minecraft.screen instanceof RecipesGui recipesGui && recipesGui.bookmarkRecipeUnderMouse(input, addRecipeWithAmount)) {
				if (!input.isSimulate()) {
					bookmarkOverlay.showBookmarkPanel();
					JeiClientSoundUtil.playClickSound();
				}
				IUserInputHandler handler = new SameElementInputHandler(this, recipesGui::isMouseOver);
				return Optional.of(handler);
			}
		}

		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.findFirst()
			.flatMap(clicked -> {
				if (input.isSimulate()) {
					IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
					return Optional.of(handler);
				}
				Optional<BookmarkHotkeyAction> bookmarkAction = resolveBookmarkAction(clicked.getElement(), input, shiftDown, controlDown);
				boolean changed = bookmarkAction
					.map(action -> bookmarkList.onElementBookmarked(clicked.getElement(), action))
					.orElse(false);
				if (changed) {
					bookmarkAction
						.filter(BookmarkInputHandler::isAddBookmarkAction)
						.ifPresent(action -> bookmarkOverlay.showBookmarkPanel());
					JeiClientSoundUtil.playClickSound();
					IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
					return Optional.of(handler);
				}
				return Optional.empty();
			});
	}

	private Optional<BookmarkHotkeyAction> resolveBookmarkAction(
		IElement<?> element,
		UserInput input,
		boolean shiftDown,
		boolean controlDown
	) {
		BookmarkHotkeyContext context = createBookmarkContext(element, input);
		return BookmarkHotkeyRouter.resolveBookmarkKeyAction(context, shiftDown, controlDown);
	}

	private BookmarkHotkeyContext createBookmarkContext(IElement<?> element, UserInput input) {
		if (bookmarkOverlay.isMouseOver(input.getMouseX(), input.getMouseY())) {
			Optional<IBookmark> bookmark = element.getBookmark();
			if (bookmark.isPresent()) {
				BookmarkHotkeySubject subject = bookmark.get() instanceof RecipeBookmark<?, ?> ?
					BookmarkHotkeySubject.RECIPE_BOOKMARK :
					BookmarkHotkeySubject.ITEM_BOOKMARK;
				return BookmarkHotkeyContext.builder(subject)
					.hasIngredient(true)
					.hasRecipe(subject == BookmarkHotkeySubject.RECIPE_BOOKMARK)
					.isBookmarkSlot(true)
					.build();
			}
		}
		return BookmarkHotkeyContext.builder(BookmarkHotkeySubject.INGREDIENT)
			.hasIngredient(true)
			.hasRecipe(true)
			.build();
	}

	private static boolean isAddBookmarkAction(BookmarkHotkeyAction action) {
		return action == BookmarkHotkeyAction.ADD_BOOKMARK ||
			action == BookmarkHotkeyAction.ADD_BOOKMARK_WITH_COUNT ||
			action == BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK ||
			action == BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK_WITH_COUNT;
	}

	private static boolean hasShiftOrControl(int modifiers) {
		return (modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL)) != 0;
	}

	private static boolean hasShift(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
	}

	private static boolean hasAlt(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_ALT) != 0;
	}

	private static boolean hasControlOrAlt(int modifiers) {
		return (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0;
	}
}
