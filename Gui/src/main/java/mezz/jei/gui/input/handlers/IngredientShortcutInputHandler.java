package mezz.jei.gui.input.handlers;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.chat.SharedChatIngredient;
import mezz.jei.common.network.packets.PacketShareIngredient;
import mezz.jei.common.platform.Services;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.NbtOps;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyContext;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeySubject;
import mezz.jei.gui.compat.ExternalIngredientSearchHandlerRegistry;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridgeRegistry;
import mezz.jei.gui.compat.ae2.RecipeChainPatternEncodeController;
import mezz.jei.gui.compat.ae2.PatternEncodingFeedback;
import mezz.jei.gui.input.IngredientFocusSource;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import mezz.jei.gui.input.PinnedTooltipManager;
import mezz.jei.common.input.UserInput;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.ProjectedBookmarkElement;
import mezz.jei.gui.recipes.RecipeIdClipboardHandler;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.util.FocusUtil;
import mezz.jei.common.util.JeiClientSoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.Optional;

public class IngredientShortcutInputHandler implements IInputTarget {
	private final IngredientFocusSource focusSource;
	private final RecipesGui recipesGui;
	private final FocusUtil focusUtil;
	private final IIngredientManager ingredientManager;
	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final IClientConfig clientConfig;
	private final IngredientTagSelectionTooltip tagSelectionTooltip;

	public IngredientTagSelectionTooltip getTagSelectionTooltip() {
		return tagSelectionTooltip;
	}

	public IngredientShortcutInputHandler(
		IngredientFocusSource focusSource,
		RecipesGui recipesGui,
		FocusUtil focusUtil,
		IClientConfig clientConfig,
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory
	) {
		this(focusSource, recipesGui, focusUtil, clientConfig, ingredientManager, recipeManager, focusFactory, new IngredientTagSelectionTooltip());
	}

	public IngredientShortcutInputHandler(
		IngredientFocusSource focusSource,
		RecipesGui recipesGui,
		FocusUtil focusUtil,
		IClientConfig clientConfig,
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IngredientTagSelectionTooltip tagSelectionTooltip
	) {
		this.tagSelectionTooltip = tagSelectionTooltip;
		this.focusSource = focusSource;
		this.recipesGui = recipesGui;
		this.focusUtil = focusUtil;
		this.ingredientManager = ingredientManager;
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.clientConfig = clientConfig;
	}

	@Override
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (input.is(keyBindings.getShareToChat())) {
			var ingredient = focusSource.getIngredientForInput(input, keyBindings)
				.filter(clicked -> clicked.getElement().isVisible())
				.filter(clicked -> clicked.getTypedIngredient().getType() == VanillaTypes.ITEM_STACK ||
					clicked.getTypedIngredient().getType() == Services.PLATFORM.getFluidHelper().getFluidIngredientType())
				.findFirst();
			if (ingredient.isPresent()) {
				return Optional.of(InputAction.run(() -> shareIngredient(ingredient.get().getTypedIngredient()))
					.within(ingredient.get()::isMouseOver).when(ingredient.get().getElement()::isVisible));
			}
		}
		if (input.is(keyBindings.getCopyIngredientComponents()) &&
			(screen.getFocused() instanceof EditBox || Internal.getJeiRuntime().getIngredientListOverlay().hasKeyboardFocus())
		) {
			return Optional.empty();
		}
		return handleAdditionalInput(screen, input, keyBindings);
	}

	private static void shareIngredient(ITypedIngredient<?> ingredient) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (!Internal.getServerConnection().canSendPacket(PacketShareIngredient.TYPE)) {
			minecraft.player.displayClientMessage(Component.translatable("jei.chat.share.unsupported"), false);
			return;
		}
		Optional<String> snapshot = SharedChatIngredient.from(ingredient)
			.flatMap(shared -> shared.encode(minecraft.player.registryAccess()));
		if (snapshot.isEmpty()) {
			minecraft.player.displayClientMessage(Component.translatable("jei.chat.share.too_large"), false);
			return;
		}
		Internal.getServerConnection().sendPacketToServer(new PacketShareIngredient(snapshot.get()));
		JeiClientSoundUtil.playClickSound();
	}

	private Optional<IInputInteraction> handleAdditionalInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		Optional<IInputInteraction> handledRecipeId = handleOutputRecipeIdShortcut(input, keyBindings);
		if (handledRecipeId.isPresent()) {
			return handledRecipeId;
		}

		Optional<IInputInteraction> handledPatternEncode = handleSingleRecipePatternEncode(input, keyBindings);
		if (handledPatternEncode.isPresent()) {
			return handledPatternEncode;
		}

		Optional<IInputInteraction> handledIngredientShortcut = handleIngredientShortcut(input, keyBindings);
		if (handledIngredientShortcut.isPresent()) {
			return handledIngredientShortcut;
		}

		if (PinnedTooltipManager.matchesInput(input.getKey(), keyBindings.getShowRecipe(), keyBindings.getPauseRecipeCycling())) {
			return handleShowRecipe(input, keyBindings);
		}

		if (!input.is(keyBindings.getShareToChat()) && input.is(keyBindings.getSearchIngredientInTerminal())) {
			return handleSearchIngredientInTerminal(screen, input, keyBindings);
		}

		return Optional.empty();
	}

	private Optional<IInputInteraction> handleOutputRecipeIdShortcut(UserInput input, IInternalKeyMappings keyBindings) {
		if (!input.is(keyBindings.getCopyRecipeId())) {
			return Optional.empty();
		}

		return focusSource.getIngredientForInput(input, keyBindings)
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
				return Optional.of(InputAction.run(() -> copyRecipeIdsToClipboard(recipeIds))
					.within(clicked::isMouseOver).when(clicked.getElement()::isVisible));
			});
	}

	private Optional<IInputInteraction> handleSingleRecipePatternEncode(UserInput input, IInternalKeyMappings keyBindings) {
		if (!input.is(keyBindings.getEncodeRecipeChainPatterns())) {
			return Optional.empty();
		}
		AbstractContainerMenu menu = recipesGui.getParentContainerMenu();
		if (menu == null) {
			return Optional.empty();
		}
		Optional<RecipeGuiLayouts.RecipeLayoutUnderMouse> recipeUnderMouse = recipesGui.getRecipeLayoutWithSlotUnderMouse(input.getMouseX(), input.getMouseY());
		var bridge = Ae2RecipeChainPatternEncodingBridgeRegistry.getBridge();
		if (!bridge.isAvailable() || !bridge.isPatternEncodingTerminal(menu) || recipeUnderMouse.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new InputAction(release -> RecipeChainPatternEncodeController.handleSingleRecipe(
				release,
				keyBindings.getEncodeRecipeChainPatterns(),
				menu,
				bridge,
				recipeUnderMouse.map(RecipeGuiLayouts.RecipeLayoutUnderMouse::layout),
				recipeUnderMouse.map(RecipeGuiLayouts.RecipeLayoutUnderMouse::slotUnderMouse)
					.map(mezz.jei.api.gui.inputs.RecipeSlotUnderMouse::slot),
				ingredientManager,
				message -> PatternEncodingFeedback.INSTANCE.showRecipeFailure(message, recipeUnderMouse.get().layout())
			)
			.filter(RecipeChainPatternEncodeController.HandleResult::sent).ifPresent(result -> JeiClientSoundUtil.playClickSound()))
			.within(recipeUnderMouse.get().slotUnderMouse()::isMouseOver));
	}

	private Optional<IInputInteraction> handleIngredientShortcut(UserInput input, IInternalKeyMappings keyBindings) {
		Optional<BookmarkHotkeyAction> action = getIngredientKeyboardAction(input, keyBindings);
		if (action.isEmpty()) {
			return Optional.empty();
		}

		return focusSource.getIngredientForInput(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.map(clicked -> InputAction.run(() -> {
					if (action.get() == BookmarkHotkeyAction.COPY_OREDICT) {
						tagSelectionTooltip.show(clicked.getTypedIngredient(), ingredientManager, input.getMouseX(), input.getMouseY());
					} else {
						executeIngredientKeyboardShortcut(clicked.getTypedIngredient(), action.get());
					}
				})
				.within(clicked::isMouseOver).when(clicked.getElement()::isVisible));
	}

	public boolean handlePreviewCopy(ITypedIngredient<?> ingredient, UserInput input, IInternalKeyMappings keys) {
		Optional<BookmarkHotkeyAction> action = getIngredientKeyboardAction(input, keys);
		if (action.isEmpty()) {
			return false;
		}
		if (!input.isSimulate()) {
			if (action.get() == BookmarkHotkeyAction.COPY_OREDICT) {
				tagSelectionTooltip.show(ingredient, ingredientManager, input.getMouseX(), input.getMouseY());
			} else {
				executeIngredientKeyboardShortcut(ingredient, action.get());
			}
		}
		return true;
	}

	private static Optional<BookmarkHotkeyAction> getIngredientKeyboardAction(UserInput input, IInternalKeyMappings keyBindings) {
		if (input.is(keyBindings.getCopyIngredientComponents())) {
			return Optional.of(BookmarkHotkeyAction.COPY_COMPONENTS);
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

	private <T> void executeIngredientKeyboardShortcut(mezz.jei.api.ingredients.ITypedIngredient<T> typedIngredient, BookmarkHotkeyAction action) {
		Minecraft minecraft = Minecraft.getInstance();
		String text = switch (action) {
			case COPY_COMPONENTS -> typedIngredient.getItemStack()
				.flatMap(stack -> IngredientClipboardText.getComponentRule(stack, minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), clientConfig.copyFullComponentsEnabled().get()))
				.orElse("");
			case COPY_NAME -> IngredientClipboardText.getIngredientName(typedIngredient, ingredientManager);
			case COPY_ID -> IngredientClipboardText.getIngredientId(typedIngredient, ingredientManager);
			default -> "";
		};
		if (action == BookmarkHotkeyAction.COPY_COMPONENTS && text.isEmpty()) {
			displayClientMessage(Component.translatable("jei.message.copy.components.failure"));
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

	private Optional<IInputInteraction> handleShowRecipe(UserInput input, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientForInput(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.map(clicked -> new InputAction(release -> {
					IElement<?> element = clicked.getElement();
					boolean handled = element instanceof ProjectedBookmarkElement<?> projectedElement &&
						projectedElement.handleShowRecipeClick(release, keyBindings, recipesGui, focusUtil);
					if (!handled) {
						clicked.show(recipesGui, focusUtil, List.of(RecipeIngredientRole.OUTPUT));
					}
				})
				.within(clicked::isMouseOver).when(clicked.getElement()::isVisible));
	}

	private Optional<IInputInteraction> handleSearchIngredientInTerminal(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientForInput(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.filter(clicked -> ExternalIngredientSearchHandlerRegistry.search(screen, clicked.getTypedIngredient(), true))
			.map(clicked -> InputAction.run(() -> ExternalIngredientSearchHandlerRegistry.search(screen, clicked.getTypedIngredient(), false))
				.within(clicked::isMouseOver).when(clicked.getElement()::isVisible));
	}

}
