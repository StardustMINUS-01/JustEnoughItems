package mezz.jei.gui.input.handlers;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.chat.JeiChatItemLinks;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyContext;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeySubject;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridgeRegistry;
import mezz.jei.gui.compat.ae2.RecipeChainPatternEncodeController;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.ScrollStep;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.recipes.RecipeIdClipboardHandler;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.util.CommandUtil;
import mezz.jei.gui.util.FocusUtil;
import mezz.jei.gui.util.GiveAmount;
import mezz.jei.common.util.JeiClientSoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public class FocusInputHandler implements IUserInputHandler {
	private final CombinedRecipeFocusSource focusSource;
	private final RecipesGui recipesGui;
	private final FocusUtil focusUtil;
	private final IIngredientManager ingredientManager;
	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final IClientToggleState toggleState;
	private final CommandUtil commandUtil;
	private final IConnectionToServer serverConnection;
	private final ScrollStep scrollStep;

	public FocusInputHandler(
		CombinedRecipeFocusSource focusSource,
		RecipesGui recipesGui,
		FocusUtil focusUtil,
		IClientConfig clientConfig,
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IClientToggleState toggleState,
		IConnectionToServer serverConnection,
		ScrollStep scrollStep
	) {
		this.focusSource = focusSource;
		this.recipesGui = recipesGui;
		this.focusUtil = focusUtil;
		this.ingredientManager = ingredientManager;
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.toggleState = toggleState;
		this.commandUtil = new CommandUtil(clientConfig, serverConnection);
		this.serverConnection = serverConnection;
		this.scrollStep = scrollStep;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (toggleState.isFastPickupEnabled() &&
			input.is(keyBindings.getLeftClick()) &&
			!InputModifiers.hasShift(input) &&
			!InputModifiers.hasControl(input) &&
			!InputModifiers.hasAlt(input)) {
			Optional<IUserInputHandler> handledFastPickup = handleFastPickup(input, keyBindings);
			if (handledFastPickup.isPresent()) {
				return handledFastPickup;
			}
		}

		Optional<IUserInputHandler> handledClick = handleClick(input, keyBindings);
		if (handledClick.isPresent()) {
			return handledClick;
		}

		Optional<IUserInputHandler> handledRecipeId = handleOutputRecipeIdShortcut(input, keyBindings);
		if (handledRecipeId.isPresent()) {
			return handledRecipeId;
		}

		Optional<IUserInputHandler> handledPatternEncode = handleSingleRecipePatternEncode(input, keyBindings);
		if (handledPatternEncode.isPresent()) {
			return handledPatternEncode;
		}

		Optional<IUserInputHandler> handledIngredientShortcut = handleIngredientShortcut(input, keyBindings);
		if (handledIngredientShortcut.isPresent()) {
			return handledIngredientShortcut;
		}

		if (toggleState.isCheatItemsEnabled()) {
			if (screen instanceof AbstractContainerScreen) {
				if (input.is(keyBindings.getCheatItemStack())) {
					Optional<IUserInputHandler> handler = handleGive(input, keyBindings, GiveAmount.MAX);
					if (handler.isPresent()) {
						return handler;
					}
				}

				if (input.is(keyBindings.getCheatOneItem())) {
					Optional<IUserInputHandler> handler = handleGive(input, keyBindings, GiveAmount.ONE);
					if (handler.isPresent()) {
						return handler;
					}
				}
			}
		}

		if (input.is(keyBindings.getShowRecipe())) {
			return handleShow(input, List.of(RecipeIngredientRole.OUTPUT), keyBindings);
		}

		if (input.is(keyBindings.getShareToChat())) {
			return handleShareToChat(input, keyBindings);
		}

		if (input.is(keyBindings.getShowUses())) {
			return handleShow(input, List.of(RecipeIngredientRole.INPUT, RecipeIngredientRole.CATALYST), keyBindings);
		}

		return Optional.empty();
	}

	private Optional<IUserInputHandler> handleOutputRecipeIdShortcut(UserInput input, IInternalKeyMappings keyBindings) {
		if (!input.is(keyBindings.getCopyRecipeId())) {
			return Optional.empty();
		}

		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.flatMap(clicked -> {
				IRecipeLayoutDrawable<?> recipeLayout = recipesGui.isOpen()
					? recipesGui.getRecipeLayoutWithSlotUnderMouse(input.getMouseX(), input.getMouseY())
						.map(RecipeGuiLayouts.RecipeLayoutUnderMouse::layout)
						.orElse(null)
					: null;
				List<String> recipeIds = RecipeIdClipboardHandler.getRecipeIdsForCopy(
					recipeLayout,
					clicked.getTypedIngredient(),
					recipeManager,
					focusFactory
				);
				if (recipeIds.isEmpty()) {
					if (!input.isSimulate()) {
						displayCopyRecipeIdFailure();
					}
					return Optional.empty();
				}
				if (!input.isSimulate()) {
					copyRecipeIdsToClipboard(recipeIds);
				}
				IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
				return Optional.of(handler);
			});
	}

	private Optional<IUserInputHandler> handleSingleRecipePatternEncode(UserInput input, IInternalKeyMappings keyBindings) {
		if (!input.is(keyBindings.getEncodeRecipeChainPatterns())) {
			return Optional.empty();
		}
		AbstractContainerMenu menu = recipesGui.getParentContainerMenu();
		if (menu == null) {
			return Optional.empty();
		}
		Optional<RecipeGuiLayouts.RecipeLayoutUnderMouse> recipeUnderMouse = recipesGui.getRecipeLayoutWithSlotUnderMouse(input.getMouseX(), input.getMouseY());
		Optional<RecipeChainPatternEncodeController.HandleResult> result = RecipeChainPatternEncodeController.handleSingleRecipe(
			input,
			keyBindings.getEncodeRecipeChainPatterns(),
			menu,
			Ae2RecipeChainPatternEncodingBridgeRegistry.getBridge(),
			recipeUnderMouse.map(RecipeGuiLayouts.RecipeLayoutUnderMouse::layout),
			recipeUnderMouse.map(RecipeGuiLayouts.RecipeLayoutUnderMouse::slotUnderMouse)
				.map(mezz.jei.api.gui.inputs.RecipeSlotUnderMouse::slot),
			ingredientManager,
			this::displayClientMessage
		);
		return result
			.filter(RecipeChainPatternEncodeController.HandleResult::handled)
			.map(ignored -> new SameElementInputHandler(this, (mouseX, mouseY) -> recipeUnderMouse
				.map(RecipeGuiLayouts.RecipeLayoutUnderMouse::slotUnderMouse)
				.map(slotUnderMouse -> slotUnderMouse.isMouseOver(mouseX, mouseY))
				.orElse(false)));
	}

	private Optional<IUserInputHandler> handleIngredientShortcut(UserInput input, IInternalKeyMappings keyBindings) {
		Optional<BookmarkHotkeyAction> action = getIngredientKeyboardAction(input, keyBindings);
		if (action.isEmpty()) {
			return Optional.empty();
		}

		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.flatMap(clicked -> {
				if (!input.isSimulate()) {
					executeIngredientKeyboardShortcut(clicked.getTypedIngredient(), action.get());
				}
				IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
				return Optional.of(handler);
			});
	}

	private static Optional<BookmarkHotkeyAction> getIngredientKeyboardAction(UserInput input, IInternalKeyMappings keyBindings) {
		BookmarkHotkeyContext context = BookmarkHotkeyContext.builder(BookmarkHotkeySubject.INGREDIENT)
			.hasIngredient(true)
			.build();
		if (input.is(keyBindings.getCopyIngredientName())) {
			return BookmarkHotkeyRouter.resolveIngredientKeyboardAction(context, BookmarkHotkeyRouter.KeyboardKey.C, true);
		}
		if (input.is(keyBindings.getCopyIngredientTags())) {
			return BookmarkHotkeyRouter.resolveIngredientKeyboardAction(context, BookmarkHotkeyRouter.KeyboardKey.D, true);
		}
		if (input.is(keyBindings.getCopyIngredientId())) {
			return BookmarkHotkeyRouter.resolveIngredientKeyboardAction(context, BookmarkHotkeyRouter.KeyboardKey.X, true);
		}
		return Optional.empty();
	}

	private <T> void executeIngredientKeyboardShortcut(mezz.jei.api.ingredients.ITypedIngredient<T> typedIngredient, BookmarkHotkeyAction action) {
		Minecraft minecraft = Minecraft.getInstance();
		String text = switch (action) {
			case COPY_NAME -> IngredientClipboardText.getIngredientName(typedIngredient, ingredientManager);
			case COPY_OREDICT -> IngredientClipboardText.getIngredientTags(typedIngredient, ingredientManager);
			case COPY_ID -> IngredientClipboardText.getIngredientId(typedIngredient, ingredientManager);
			default -> "";
		};
		minecraft.keyboardHandler.setClipboard(text);
		JeiClientSoundUtil.playClickSound();
	}

	private static void copyRecipeIdsToClipboard(List<String> recipeIds) {
		Minecraft minecraft = Minecraft.getInstance();
		String text = RecipeIdClipboardHandler.toClipboardText(recipeIds);
		minecraft.keyboardHandler.setClipboard(text);
		MutableComponent message = Component.translatable("jei.message.copy.recipe.id.success", Component.literal(text));
		LocalPlayer player = minecraft.player;
		if (player != null) {
			player.displayClientMessage(message, false);
		}
		JeiClientSoundUtil.playClickSound();
	}

	private static void displayCopyRecipeIdFailure() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.displayClientMessage(Component.translatable("jei.message.copy.recipe.id.failure"), false);
		}
	}

	private void displayClientMessage(Component message) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.displayClientMessage(message, false);
		}
	}

	private Optional<IUserInputHandler> handleClick(UserInput input, IInternalKeyMappings keyBindings) {
		List<IClickableIngredientInternal<?>> ingredientUnderMouse = focusSource.getIngredientUnderMouse(input, keyBindings)
			.toList();

		for (IClickableIngredientInternal<?> clicked : ingredientUnderMouse) {
			IElement<?> element = clicked.getElement();
			if (element.handleClick(input, keyBindings)) {
				IUserInputHandler result = new SameElementInputHandler(this, clicked::isMouseOver);
				return Optional.of(result);
			}
		}
		return Optional.empty();
	}

	private Optional<IUserInputHandler> handleShow(UserInput input, List<RecipeIngredientRole> roles, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.map(clicked -> {
				if (!input.isSimulate()) {
					clicked.show(recipesGui, focusUtil, roles);
				}
				return new SameElementInputHandler(this, clicked::isMouseOver);
			});
	}

	private Optional<IUserInputHandler> handleShareToChat(UserInput input, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.map(clicked -> {
				if (!input.isSimulate()) {
					ITypedIngredient<?> typedIngredient = clicked.getTypedIngredient();
					String chatText = JeiChatItemLinks.createLinkMarker(typedIngredient, ingredientManager);
					Minecraft minecraft = Minecraft.getInstance();
					ChatScreen chatScreen = new ChatScreen(chatText);
					minecraft.setScreen(chatScreen);
				}
				return new SameElementInputHandler(this, clicked::isMouseOver);
			});
	}

	private Optional<IUserInputHandler> handleGive(UserInput input, IInternalKeyMappings keyBindings, GiveAmount giveAmount) {
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.<IUserInputHandler>mapMulti((clicked, consumer) -> {
				ItemStack itemStack = clicked.getCheatItemStack(ingredientManager);
				if (!itemStack.isEmpty()) {
					int amount = giveAmount.getAmountForStack(itemStack);
					if (!input.isSimulate()) {
						commandUtil.giveStack(itemStack, amount);
					}
					IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
					consumer.accept(handler);
				}
			})
			.findFirst();
	}

	private Optional<IUserInputHandler> handleFastPickup(UserInput input, IInternalKeyMappings keyBindings) {
		if (!serverConnection.isJeiOnServer()) {
			return Optional.empty();
		}
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.<IUserInputHandler>mapMulti((clicked, consumer) -> {
				ItemStack itemStack = clicked.getCheatItemStack(ingredientManager);
				if (!itemStack.isEmpty()) {
					int amount = resolveFastPickupAmount(itemStack, clicked.getCheatGiveAmount(), scrollStep);
					if (!input.isSimulate()) {
						commandUtil.fastPickupStack(itemStack.copyWithCount(amount));
					}
					IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
					consumer.accept(handler);
				}
			})
			.findFirst();
	}

	static int resolveFastPickupAmount(ItemStack itemStack, Optional<Long> cheatGiveAmount, ScrollStep scrollStep) {
		if (cheatGiveAmount.isPresent()) {
			long amount = Math.max(1, cheatGiveAmount.get());
			return (int) Math.min(Integer.MAX_VALUE, amount);
		}
		long amount = scrollStep.getValue() == 0 ? itemStack.getMaxStackSize() : scrollStep.getValue();
		return (int) Math.min(Integer.MAX_VALUE, amount);
	}
}
