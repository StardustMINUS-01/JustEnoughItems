package mezz.jei.gui.input.handlers;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
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
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.PinnedTooltipManager;
import mezz.jei.gui.input.UserInput;
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

public class IngredientShortcutInputHandler extends FocusInputHandler {
	private final CombinedRecipeFocusSource focusSource;
	private final RecipesGui recipesGui;
	private final FocusUtil focusUtil;
	private final IIngredientManager ingredientManager;
	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final IClientConfig clientConfig;
	private final IngredientTagSelectionTooltip tagSelectionTooltip = new IngredientTagSelectionTooltip();

	public IngredientTagSelectionTooltip getTagSelectionTooltip() {
		return tagSelectionTooltip;
	}

	public IngredientShortcutInputHandler(
		CombinedRecipeFocusSource focusSource,
		RecipesGui recipesGui,
		FocusUtil focusUtil,
		IClientConfig clientConfig,
		IIngredientManager ingredientManager,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory
	) {
		super(focusSource, recipesGui, focusUtil, ingredientManager);
		this.focusSource = focusSource;
		this.recipesGui = recipesGui;
		this.focusUtil = focusUtil;
		this.ingredientManager = ingredientManager;
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.clientConfig = clientConfig;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (input.is(keyBindings.getCopyIngredientComponents()) &&
			(screen.getFocused() instanceof EditBox || Internal.getJeiRuntime().getIngredientListOverlay().hasKeyboardFocus())
		) {
			return Optional.empty();
		}
		return super.handleUserInput(screen, input, keyBindings);
	}

	@Override
	protected Optional<IUserInputHandler> handleAdditionalInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
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

		if (PinnedTooltipManager.matchesInput(input.getKey(), keyBindings.getShowRecipe(), keyBindings.getPauseRecipeCycling())) {
			return handleShowRecipe(input, keyBindings);
		}

		if (!input.is(keyBindings.getShareToChat()) && input.is(keyBindings.getSearchIngredientInTerminal())) {
			return handleSearchIngredientInTerminal(screen, input, keyBindings);
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
					if (action.get() == BookmarkHotkeyAction.COPY_OREDICT) {
						tagSelectionTooltip.show(clicked.getTypedIngredient(), ingredientManager, input.getMouseX(), input.getMouseY());
					} else {
						executeIngredientKeyboardShortcut(clicked.getTypedIngredient(), action.get());
					}
				}
				IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
				return Optional.of(handler);
			});
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
				.flatMap(stack -> IngredientClipboardText.getComponentRule(stack, minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), clientConfig.copyFullComponentsEnabled().getValue()))
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

	private Optional<IUserInputHandler> handleSearchIngredientInTerminal(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.filter(clicked -> clicked.getElement().isVisible())
			.findFirst()
			.filter(clicked -> ExternalIngredientSearchHandlerRegistry.search(screen, clicked.getTypedIngredient(), input.isSimulate()))
			.map(clicked -> new SameElementInputHandler(this, clicked::isMouseOver));
	}

}
