package mezz.jei.gui.input.handlers;

import net.minecraft.client.gui.components.EditBox;
import mezz.jei.common.network.packets.PacketShareIngredient;
import mezz.jei.common.chat.SharedChatIngredient;
import mezz.jei.common.Internal;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.compat.ExternalIngredientSearchHandlerRegistry;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyContext;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeySubject;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridgeRegistry;
import mezz.jei.gui.compat.ae2.RecipeChainPatternEncodeController;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.PinnedTooltipManager;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.ProjectedBookmarkElement;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeIdClipboardHandler;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.Optional;

public class FocusInputHandler implements IUserInputHandler {
	private final CombinedRecipeFocusSource focusSource;
	private final RecipesGui recipesGui;
	private final FocusUtil focusUtil;
	private final IIngredientManager ingredientManager;
	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final IngredientTagSelectionTooltip tagTooltip = new IngredientTagSelectionTooltip();

	public IngredientTagSelectionTooltip getTagTooltip() {
		return tagTooltip;
	}

	public FocusInputHandler(
		CombinedRecipeFocusSource focusSource,
		RecipesGui recipesGui,
		FocusUtil focusUtil,
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory
	) {
		this.focusSource = focusSource;
		this.recipesGui = recipesGui;
		this.focusUtil = focusUtil;
		this.ingredientManager = ingredientManager;
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (input.is(keyBindings.getCopyIngredientNbt()) && (screen.getFocused() instanceof EditBox ||
			Internal.getJeiRuntime().getIngredientListOverlay().hasKeyboardFocus())
		) {
			return Optional.empty();
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

		// 1.21.1 parity: while a tooltip is pinned by the pin key, the show-recipe and
		// show-uses keys must still work even though the pin key is an extra modifier.
		if (PinnedTooltipManager.matchesInput(input.getKey(), keyBindings.getShowRecipe(), keyBindings.getPauseRecipeCycling())) {
			return handleShowRecipe(input, keyBindings);
		}

		if (!input.is(keyBindings.getShareToChat()) && input.is(keyBindings.getSearchIngredientInTerminal())) {
			return focusSource.getIngredientUnderMouse(input, keyBindings)
				.filter(clicked -> clicked.getElement().isVisible())
				.findFirst()
				.filter(clicked -> ExternalIngredientSearchHandlerRegistry.search(screen, clicked.getTypedIngredient(), input.isSimulate()))
				.map(clicked -> new SameElementInputHandler(this, clicked::isMouseOver));
		}
		if (input.is(keyBindings.getShareToChat())) {
			return handleShareToChat(input, keyBindings);
		}

		if (PinnedTooltipManager.matchesInput(input.getKey(), keyBindings.getShowUses(), keyBindings.getPauseRecipeCycling())) {
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
					? recipesGui.getRecipeLayoutUnderMouse(input.getMouseX(), input.getMouseY())
						.map(IRecipeLayoutWithButtons::getRecipeLayout)
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
		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		Optional<IRecipeLayoutWithButtons<?>> recipeUnderMouse = recipesGui.getRecipeLayoutUnderMouse(mouseX, mouseY);
		Optional<IRecipeLayoutDrawable<?>> layout = recipeUnderMouse.map(IRecipeLayoutWithButtons::getRecipeLayout);
		Optional<RecipeSlotUnderMouse> slotUnderMouse = layout.flatMap(recipeLayout -> recipeLayout.getSlotUnderMouse(mouseX, mouseY));
		Optional<RecipeChainPatternEncodeController.HandleResult> result = RecipeChainPatternEncodeController.handleSingleRecipe(
			input,
			keyBindings.getEncodeRecipeChainPatterns(),
			menu,
			Ae2RecipeChainPatternEncodingBridgeRegistry.getBridge(),
			layout,
			slotUnderMouse.map(RecipeSlotUnderMouse::slot),
			ingredientManager,
			this::displayClientMessage
		);
		return result
			.filter(RecipeChainPatternEncodeController.HandleResult::handled)
			.map(ignored -> new SameElementInputHandler(this, (mouseX2, mouseY2) -> slotUnderMouse
				.map(slot -> slot.isMouseOver(mouseX2, mouseY2))
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
					if (action.get() == BookmarkHotkeyAction.COPY_OREDICT) {
						tagTooltip.show(clicked.getTypedIngredient(), ingredientManager, input.getMouseX(), input.getMouseY());
					} else {
						executeIngredientKeyboardShortcut(clicked.getTypedIngredient(), action.get());
					}
				}
				IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
				return Optional.of(handler);
			});
	}

	public boolean handlePreviewCopy(ITypedIngredient<?> ingredient, UserInput input, IInternalKeyMappings keys) {
		Optional<BookmarkHotkeyAction> action = getIngredientKeyboardAction(input, keys);
		if (action.isEmpty()) {
			return false;
		}
		if (!input.isSimulate()) {
			if (action.get() == BookmarkHotkeyAction.COPY_OREDICT) {
				tagTooltip.show(ingredient, ingredientManager, input.getMouseX(), input.getMouseY());
			} else {
				executeIngredientKeyboardShortcut(ingredient, action.get());
			}
		}
		return true;
	}

	private static Optional<BookmarkHotkeyAction> getIngredientKeyboardAction(UserInput input, IInternalKeyMappings keyBindings) {
		if (input.is(keyBindings.getCopyIngredientNbt())) {
			return Optional.of(BookmarkHotkeyAction.COPY_NBT);
		}
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

	private <T> void executeIngredientKeyboardShortcut(ITypedIngredient<T> typedIngredient, BookmarkHotkeyAction action) {
		Minecraft minecraft = Minecraft.getInstance();
		String text = switch (action) {
			case COPY_NBT -> typedIngredient.getItemStack().map(IngredientClipboardText::getNbtRule).orElse("");
			case COPY_NAME -> IngredientClipboardText.getIngredientName(typedIngredient, ingredientManager);
			case COPY_ID -> IngredientClipboardText.getIngredientId(typedIngredient, ingredientManager);
			default -> "";
		};
		if (action == BookmarkHotkeyAction.COPY_NBT && text.isEmpty()) {
			displayClientMessage(Component.translatable("jei.message.copy.nbt.failure"));
			return;
		}
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

	private Optional<IUserInputHandler> handleShowRecipe(UserInput input, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.map(clicked -> {
				IElement<?> element = clicked.getElement();
				boolean handled = element instanceof ProjectedBookmarkElement<?> projectedElement &&
					projectedElement.handleShowRecipeClick(input, keyBindings, recipesGui, focusUtil);
				if (!handled && !input.isSimulate()) {
					clicked.show(recipesGui, focusUtil, List.of(RecipeIngredientRole.OUTPUT));
				}
				return new SameElementInputHandler(this, clicked::isMouseOver);
			});
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
					shareIngredient(clicked.getTypedIngredient());
				}
				return new SameElementInputHandler(this, clicked::isMouseOver);
			});
	}

	private static void shareIngredient(ITypedIngredient<?> ingredient) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (!Internal.getServerConnection().canShareChat()) {
			minecraft.player.displayClientMessage(Component.translatable("jei.chat.share.unsupported"), false);
			return;
		}
		Optional<String> snapshot = SharedChatIngredient.from(ingredient)
			.flatMap(shared -> shared.encode());
		if (snapshot.isEmpty()) {
			minecraft.player.displayClientMessage(Component.translatable("jei.chat.share.too_large"), false);
			return;
		}
		Internal.getServerConnection().sendPacketToServer(new PacketShareIngredient(snapshot.get()));
		JeiClientSoundUtil.playClickSound();
	}

}
