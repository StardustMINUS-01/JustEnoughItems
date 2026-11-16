package mezz.jei.gui.input.handlers;

import mezz.jei.common.bookmarks.ProcessingStations;
import mezz.jei.common.util.SaturatedMath;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.packets.PacketShareBookmarkGroup;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyContext;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeySubject;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import mezz.jei.common.input.interaction.ConsumedInput;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.tree.RecipeTreeScreen;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.bookmarks.chain.BookmarkContainerPacketHandler;
import mezz.jei.gui.bookmarks.chain.BookmarkContainerPullExecutor;
import mezz.jei.gui.bookmarks.chain.BookmarkContainerStorageScanner;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipSectionType;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingRunner;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridge;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridgeRegistry;
import mezz.jei.gui.compat.ae2.RecipeChainPatternEncodeController;
import mezz.jei.gui.input.BookmarkKeyInputs;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.PinnedTooltipManager;
import mezz.jei.gui.input.IPinnedTooltipHolder;
import mezz.jei.gui.input.InputArea;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.bookmarks.PlayerInventoryRecipeChainTooltipInventoryProvider;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.config.BookmarkJsonSerializer;
import net.minecraft.client.Minecraft;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import mezz.jei.gui.input.GuiInputScene;
import mezz.jei.gui.input.IngredientFocusSource;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class BookmarkInputHandler implements mezz.jei.common.input.IInputTarget {
	private final IngredientFocusSource focusSource;
	private final BookmarkList bookmarkList;
	private final BookmarkOverlay bookmarkOverlay;
	private final IIngredientManager ingredientManager;
	private final PlayerInventoryRecipeChainTooltipInventoryProvider recipeChainInventoryProvider;
	private final IConnectionToServer serverConnection;
	private final BookmarkAutoCraftingRunner autoCraftingRunner;
	private final Function<FocusedRecipe, Optional<Integer>> favoriteTreeSaver;
	private final Function<BookmarkIngredientKey, Optional<FocusedRecipe>> favoriteRecipeLookup;
	private final GuiInputScene inputScene;
	private final IClientConfig clientConfig;
	private final RecipesGui recipesGui;

	public BookmarkInputHandler(
		IngredientFocusSource focusSource,
		BookmarkList bookmarkList,
		BookmarkOverlay bookmarkOverlay,
		IIngredientManager ingredientManager,
		IConnectionToServer serverConnection,
		BookmarkAutoCraftingRunner autoCraftingRunner,
		Function<FocusedRecipe, Optional<Integer>> favoriteTreeSaver,
		Function<BookmarkIngredientKey, Optional<FocusedRecipe>> favoriteRecipeLookup,
		GuiInputScene inputScene,
		IClientConfig clientConfig,
		RecipesGui recipesGui
	) {
		this.focusSource = focusSource;
		this.bookmarkList = bookmarkList;
		this.bookmarkOverlay = bookmarkOverlay;
		this.ingredientManager = ingredientManager;
		this.recipeChainInventoryProvider = new PlayerInventoryRecipeChainTooltipInventoryProvider(Minecraft.getInstance(), ingredientManager);
		this.serverConnection = serverConnection;
		this.autoCraftingRunner = autoCraftingRunner;
		this.favoriteTreeSaver = favoriteTreeSaver;
		this.favoriteRecipeLookup = favoriteRecipeLookup;
		this.inputScene = inputScene;
		this.clientConfig = clientConfig;
		this.recipesGui = recipesGui;
	}

	@Override
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (input.is(keyBindings.getShowRecipeTree())) {
			return bookmarkOverlay.getPatternEncodeGroupIdUnderMouse(input.getMouseX(), input.getMouseY())
				.filter(bookmarkList::isGroupCraftingMode)
				.map(groupId -> InputAction.run(() -> Minecraft.getInstance().setScreen(new RecipeTreeScreen(screen, bookmarkList, groupId, ingredientManager)))
					.within(bookmarkOverlay::isMouseOver));
		}
		if (input.is(keyBindings.getShareToChat())) {
			Optional<IInputInteraction> shareHandler = handleGroupShare(input);
			if (shareHandler.isPresent()) {
				return shareHandler;
			}
		}
		if (!InputModifiers.hasShift(input) && !InputModifiers.hasControl(input) &&
			PinnedTooltipManager.matchesInput(input.getKey(), keyBindings.getBookmark(), keyBindings.getPauseRecipeCycling())
		) {
			Optional<IInputInteraction> recipeHandler = prepareRecipeBookmarkAction(input);
			if (recipeHandler.isPresent()) {
				return recipeHandler;
			}
		}
		if (isSaveMissingInput(input, keyBindings.getBookmarkPullItems())) {
			return handleSaveMissingRecipeChain(input);
		}
		if (BookmarkAutoCraftingActivator.isAutoCraftingInput(input, keyBindings.getCraftItems())) {
			return handleBookmarkAutoCrafting(input, keyBindings.getCraftItems());
		}
		if (isBookmarkPullInput(input, keyBindings.getBookmarkPullItems())) {
			return handleBookmarkPull(input);
		}
		Optional<IInputInteraction> patternEncodeHandler = handleRecipeChainPatternEncode(input, keyBindings);
		if (patternEncodeHandler.isPresent()) {
			return patternEncodeHandler;
		}
		if (isFavoriteActionInput(input, keyBindings)) {
			Optional<IInputInteraction> favoriteHandler = handleFavoriteRecipe(input, keyBindings);
			if (favoriteHandler.isPresent()) {
				return favoriteHandler;
			}
		}
		boolean pinnedBookmark = input.isKeyboardInput() &&
			PinnedTooltipManager.matchesInput(input.getKey(), keyBindings.getBookmark(), keyBindings.getPauseRecipeCycling()) &&
			inputScene.getTarget(input).map(InputArea::area).filter(IPinnedTooltipHolder.class::isInstance).isPresent();
		if (pinnedBookmark || isBookmarkInput(input, keyBindings.getBookmark())) {
			return handleBookmark(input, keyBindings, !pinnedBookmark && InputModifiers.hasShift(input));
		}
		return Optional.empty();
	}

	private Optional<IInputInteraction> handleBookmarkAutoCrafting(UserInput input, IJeiKeyMapping craftItemsKey) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen) || minecraft.player == null) {
			return Optional.empty();
		}
		Optional<Integer> groupId = bookmarkOverlay.getPullGroupIdUnderMouse(input.getMouseX(), input.getMouseY())
			.filter(bookmarkList::isGroupCraftingMode);
		if (groupId.isEmpty()) {
			return Optional.empty();
		}

		int targetSlotCount = Math.max(BookmarkGhostOverlayTargetSlots.fromMenu(containerScreen.getMenu()).size(),
			ProcessingStations.getInputSlotCount(containerScreen.getMenu()));
		if (targetSlotCount <= 0) {
			return Optional.empty();
		}
		if (!serverConnection.canCraftBookmarks() && !serverConnection.canProcessBookmarks()
		) {
			if (!input.isSimulate()) {
				minecraft.player.displayClientMessage(Component.translatable("jei.message.server.feature_unavailable"), false);
			}
			return Optional.empty();
		}
		int hoveredGroupId = groupId.get();
		AbstractContainerMenu menu = containerScreen.getMenu();
		boolean craftAll = isCraftAllModifier(input.getModifiers());
		return Optional.of(new InputAction(release -> {
				if (!BookmarkAutoCraftingActivator.claimAutoCraftingInput(release, craftItemsKey)) {
					return;
				}
				boolean handled = BookmarkAutoCraftingBridge.createTask(
						bookmarkList.getRecipeChainInputs(hoveredGroupId),
						bookmarkList.getCollapsedRecipeIds(hoveredGroupId),
						targetSlotCount,
						menu.containerId,
						() -> getAutoCraftingInventoryInputs(hoveredGroupId),
						recipeChainInventoryProvider::getAvailableStacks,
						recipeUid -> bookmarkList.createRecipeLayoutDrawable(hoveredGroupId, recipeUid),
						serverConnection::sendPacketToServer,
						() -> Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> activeScreen &&
							activeScreen.getMenu() == menu,
						craftAll
					)
					.map(autoCraftingRunner::start)
					.orElse(false);
				if (handled) {
					JeiClientSoundUtil.playClickSound();
				} else {
					BookmarkAutoCraftingActivator.releaseAutoCraftingInput(release.getKey());
				}
			})
			.within(bookmarkOverlay::isMouseOver).when(() -> minecraft.screen == containerScreen));
	}

	private List<RecipeChainInput> getAutoCraftingInventoryInputs(int groupId) {
		return recipeChainInventoryProvider.getInventoryInputs(groupId, -1);
	}

	private Optional<IInputInteraction> handleRecipeChainPatternEncode(UserInput input, IInternalKeyMappings keyBindings) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!input.is(keyBindings.getEncodeRecipeChainPatterns()) ||
			!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen) || minecraft.player == null
		) {
			return Optional.empty();
		}
		Ae2RecipeChainPatternEncodingBridge bridge = Ae2RecipeChainPatternEncodingBridgeRegistry.getBridge();
		if (!bridge.isAvailable() || !bridge.isPatternEncodingTerminal(containerScreen.getMenu())) {
			return Optional.empty();
		}
		Optional<Integer> groupId = bookmarkOverlay.getPatternEncodeGroupIdUnderMouse(input.getMouseX(), input.getMouseY());
		if (groupId.isEmpty()) {
			return handleSingleBookmarkPatternEncode(input, keyBindings, containerScreen.getMenu(), bridge);
		}
		return Optional.of(new InputAction(release -> RecipeChainPatternEncodeController.handle(
				release,
				keyBindings.getEncodeRecipeChainPatterns(),
				containerScreen.getMenu(),
				groupId,
				groupId.filter(bookmarkList::isGroupCraftingMode).isPresent(),
				true,
				bridge,
				() -> bookmarkList.getGroupRecipeInputs(groupId.get()),
				() -> bookmarkList.getCollapsedRecipeIds(groupId.get()),
				recipeUid -> bookmarkList.createRecipeLayoutDrawable(groupId.get(), recipeUid),
				ingredientManager,
				message -> minecraft.player.displayClientMessage(message, false)
			)
			.filter(RecipeChainPatternEncodeController.HandleResult::sent).ifPresent(result -> JeiClientSoundUtil.playClickSound()))
			.within(bookmarkOverlay::isMouseOver));
	}

	private Optional<IInputInteraction> handleSingleBookmarkPatternEncode(
		UserInput input,
		IInternalKeyMappings keyBindings,
		AbstractContainerMenu menu,
		Ae2RecipeChainPatternEncodingBridge bridge
	) {
		if (!bookmarkOverlay.isMouseOver(input.getMouseX(), input.getMouseY())) {
			return Optional.empty();
		}
		return focusSource.getIngredientForInput(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.map(clicked -> {
				Optional<IRecipeLayoutDrawable<?>> layout = resolveSingleBookmarkPatternLayout(clicked.getElement(), clicked.getTypedIngredient());
				return new InputAction(release -> {
						Minecraft minecraft = Minecraft.getInstance();
						if (layout.isEmpty()) {
							if (minecraft.player != null) {
								minecraft.player.displayClientMessage(Component.translatable("jei.message.ae2.pattern_encoding.no_recipe_request"), false);
							}
							return;
						}
						RecipeChainPatternEncodeController.handleSingleRecipe(
							release,
							keyBindings.getEncodeRecipeChainPatterns(),
							menu,
							bridge,
							layout,
							Optional.empty(),
							() -> clicked.getElement().getBookmark().map(bookmarkList::getRecipeInputs).orElseGet(List::of),
							ingredientManager,
							message -> {
								if (minecraft.player != null) {
									minecraft.player.displayClientMessage(message, false);
								}
							}
						).filter(RecipeChainPatternEncodeController.HandleResult::sent).ifPresent(result -> JeiClientSoundUtil.playClickSound());
					})
					.within(clicked::isMouseOver).when(clicked.getElement()::isVisible);
			});
	}

	private Optional<IRecipeLayoutDrawable<?>> resolveSingleBookmarkPatternLayout(IElement<?> element, ITypedIngredient<?> ingredient) {
		return element.getBookmark()
			.flatMap(bookmark -> {
				if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
					return bookmarkList.createRecipeLayoutDrawable(recipeBookmark);
				}
				if (bookmark instanceof IngredientBookmark<?>) {
					return bookmarkList.createUniqueRecipeLayoutDrawable(ingredient);
				}
				return Optional.empty();
			});
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

	private static boolean isSaveMissingInput(UserInput input, IJeiKeyMapping bookmarkPullKey) {
		int modifiers = input.getModifiers();
		return hasAlt(modifiers) && !hasShift(modifiers) && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0 &&
			bookmarkPullKey.matchesIgnoringModifiers(input.getKey());
	}

	private Optional<IInputInteraction> handleGroupShare(UserInput input) {
		if (!serverConnection.canShareBookmarkGroup()) {
			return Optional.empty();
		}
		Optional<String> snapshot = bookmarkOverlay.getPatternEncodeGroupIdUnderMouse(input.getMouseX(), input.getMouseY())
			.flatMap(groupId -> BookmarkJsonSerializer.serializeGroupSnapshot(
				bookmarkList,
				groupId,
				ingredientManager
			));
		if (snapshot.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(InputAction.run(() -> {
				serverConnection.sendPacketToServer(new PacketShareBookmarkGroup(snapshot.get()));
				JeiClientSoundUtil.playClickSound();
			})
			.within(bookmarkOverlay::isMouseOver));
	}

	private Optional<IInputInteraction> handleSaveMissingRecipeChain(UserInput input) {
		Optional<Integer> optionalGroupId = bookmarkOverlay.getPatternEncodeGroupIdUnderMouse(input.getMouseX(), input.getMouseY())
			.filter(bookmarkList::isGroupCraftingMode);
		if (optionalGroupId.isEmpty()) {
			return Optional.empty();
		}
		int groupId = optionalGroupId.get();
		List<RecipeChainInput> inventoryInputs = recipeChainInventoryProvider.getTooltipInventoryInputs(groupId);
		List<RecipeChainInput> groupInputs = bookmarkList.getRecipeChainTooltipInputs(groupId);
		RecipeChainTooltipModel model = RecipeChainTooltipModel.create(
			groupInputs,
			bookmarkList.getRecipeChainDetails(groupId),
			bookmarkList.getCollapsedRecipeIds(groupId),
			inventoryInputs,
			true,
			false,
			ingredientManager
		);
		List<RecipeChainTooltipModel.Item> missingItems = model.sections().stream()
			.filter(section -> section.type() == RecipeChainTooltipSectionType.MISSING)
			.flatMap(section -> section.items().stream())
			.toList();
		if (missingItems.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(InputAction.run(() -> {
				bookmarkList.addMissingRecipeChainGroup(groupId, missingItems);
				bookmarkOverlay.showBookmarkPanel();
				JeiClientSoundUtil.playClickSound();
			})
			.within(bookmarkOverlay::isMouseOver));
	}

	private Optional<IInputInteraction> handleBookmarkPull(UserInput input) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen) || minecraft.player == null) {
			return Optional.empty();
		}
		Optional<Integer> groupId = bookmarkOverlay.getPullGroupIdUnderMouse(input.getMouseX(), input.getMouseY());
		if (groupId.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(InputAction.run(() -> {
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
			})
			.within(bookmarkOverlay::isMouseOver).when(() -> minecraft.screen == containerScreen));
	}

	private Optional<IInputInteraction> handleFavoriteRecipe(UserInput input, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientForInput(input, keyBindings)
			.findFirst()
			.flatMap(clicked -> {
				Optional<InputAction> action = resolveFavoriteRecipeElementAction(clicked.getElement(), input, keyBindings)
					.flatMap(favoriteAction -> prepareFavoriteRecipeAction(clicked.getElement(), input, keyBindings, favoriteAction));
				if (action.isEmpty()) {
					action = resolveFavoriteIngredientTreeRecipe(
							clicked.getElement(), input, keyBindings, ingredient -> Optional.of(createKey(ingredient)), favoriteRecipeLookup
						)
						.map(recipe -> InputAction.run(() -> saveFavoriteTree(recipe)));
				}
				return action.map(value -> value.within(clicked::isMouseOver).when(clicked.getElement()::isVisible));
			});
	}

	private Optional<IInputInteraction> prepareRecipeBookmarkAction(UserInput input) {
		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		if (!inputScene.isTarget(recipesGui, input)) {
			return Optional.empty();
		}
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
		if (!shouldBookmarkRecipe(slotUnderMouse, clientConfig.bookmarkOutputAsRecipe().get())) {
			return Optional.empty();
		}

		return Optional.of(InputAction.run(() -> bookmarkList.toggleBookmark(recipeBookmark)).within(layout::isMouseOver));
	}

	static boolean shouldBookmarkRecipe(Optional<RecipeSlotUnderMouse> slotUnderMouse, boolean bookmarkOutputAsRecipeEnabled) {
		return slotUnderMouse
			.map(slot -> shouldBookmarkRecipe(slot.slot().getRole(), bookmarkOutputAsRecipeEnabled))
			.orElse(true);
	}

	static boolean shouldBookmarkRecipe(RecipeIngredientRole role, boolean bookmarkOutputAsRecipeEnabled) {
		return role == RecipeIngredientRole.OUTPUT && bookmarkOutputAsRecipeEnabled;
	}

	private Optional<InputAction> prepareFavoriteRecipeAction(
		IElement<?> element,
		UserInput input,
		IInternalKeyMappings keyBindings,
		FavoriteRecipeHotkeyAction action
	) {
		if (!(element instanceof FavoriteRecipeElement<?> favoriteRecipeElement)) {
			return Optional.empty();
		}
		if (action == FavoriteRecipeHotkeyAction.REMOVE_FAVORITE) {
			return favoriteRecipeElement.prepareAction(input, keyBindings)
				.map(prepared -> new InputAction(release -> {
					prepared.action().accept(release);
					JeiClientSoundUtil.playClickSound();
				}, prepared.canExecute()));
		}
		return Optional.of(InputAction.run(() -> saveFavoriteTree(favoriteRecipeElement.getFocusedRecipe())));
	}

	private void saveFavoriteTree(FocusedRecipe recipe) {
		if (favoriteTreeSaver.apply(recipe).isPresent()) {
			bookmarkOverlay.showBookmarkPanel();
			JeiClientSoundUtil.playClickSound();
		}
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

	private Optional<IInputInteraction> handleBookmark(UserInput input, IInternalKeyMappings keyBindings, boolean shiftDown) {
		boolean controlDown = InputModifiers.hasControl(input);
		if (shiftDown) {
			if (bookmarkOverlay.removeGroupUnderMouseGroupPanel(input)) {
				return Optional.of(input.isKeyboardInput() ? ConsumedInput.INSTANCE : new InputAction(release -> bookmarkOverlay.removeGroupUnderMouseGroupPanel(release)).within(bookmarkOverlay::isMouseOver));
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
				return Optional.of(input.isKeyboardInput() ? ConsumedInput.INSTANCE : new InputAction(release -> {
						if (recipesGui.bookmarkRecipeUnderMouse(release, addRecipeWithAmount)) {
							bookmarkOverlay.showBookmarkPanel();
							JeiClientSoundUtil.playClickSound();
						}
					})
					.within(recipesGui::isMouseOver));
			}
		}

		return focusSource.getIngredientForInput(input, keyBindings)
			.findFirst()
			.flatMap(clicked -> resolveBookmarkAction(clicked.getElement(), input, shiftDown, controlDown)
				.map(action -> InputAction.run(() -> {
						if (bookmarkList.onElementBookmarked(clicked.getElement(), action)) {
							if (isAddBookmarkAction(action)) {
								bookmarkOverlay.showBookmarkPanel();
							}
							JeiClientSoundUtil.playClickSound();
						}
					})
					.within(clicked::isMouseOver).when(clicked.getElement()::isVisible)));
	}

	public static Optional<FavoriteRecipeHotkeyAction> resolveFavoriteRecipeElementAction(
		IElement<?> element,
		UserInput input,
		IInternalKeyMappings keyBindings
	) {
		if (!(element instanceof FavoriteRecipeElement<?>)) {
			return Optional.empty();
		}
		int modifiers = input.getModifiers();
		if (!BookmarkKeyInputs.isBookmarkKeyWithoutControlOrAlt(input, keyBindings)) {
			return Optional.empty();
		}
		if (hasShift(modifiers)) {
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
		int modifiers = input.getModifiers();
		if (!hasShift(modifiers) || !BookmarkKeyInputs.isBookmarkKeyWithoutControlOrAlt(input, keyBindings)) {
			return Optional.empty();
		}
		return keyFactory.apply(element.getTypedIngredient())
			.flatMap(favoriteRecipeLookup);
	}

	public enum FavoriteRecipeHotkeyAction {
		REMOVE_FAVORITE,
		SAVE_FAVORITE_TREE
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
				BookmarkHotkeySubject subject = bookmark.get() instanceof RecipeBookmark<?, ?> ? BookmarkHotkeySubject.RECIPE_BOOKMARK : BookmarkHotkeySubject.ITEM_BOOKMARK;
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
