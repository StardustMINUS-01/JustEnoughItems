package mezz.jei.gui.input.handlers;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.chat.JeiChatRecipeLinks;
import mezz.jei.common.chat.JeiChatRecipeLinks.RecipeLink;
import mezz.jei.common.chat.SharedChatIngredient;
import mezz.jei.gui.chat.ChatRecipeTooltip;
import mezz.jei.gui.input.InputType;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.chat.JeiChatItemLinkHover;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class ChatLinkInputHandler {
	private final IRecipesGui recipesGui;
	private final FocusUtil focusUtil;
	private final IScreenHelper screenHelper;
	private final BookmarkList bookmarkList;
	private final Predicate<String> groupImporter;
	@Nullable
	private InputConstants.Key pendingGroupKey;
	@Nullable
	private String pendingGroupSnapshot;

	@Nullable
	private PendingInput pendingInput;
	private @Nullable PendingRecipe pendingRecipe;
	private record PendingRecipe(InputConstants.Key key, RecipeLink recipe) {}

	public ChatLinkInputHandler(
		IRecipesGui recipesGui,
		FocusUtil focusUtil,
		IScreenHelper screenHelper,
		BookmarkList bookmarkList,
		Predicate<String> groupImporter
	) {
		this.recipesGui = recipesGui;
		this.focusUtil = focusUtil;
		this.screenHelper = screenHelper;
		this.bookmarkList = bookmarkList;
		this.groupImporter = groupImporter;
	}

	public boolean handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (!(screen instanceof ChatScreen chatScreen)) {
			this.pendingInput = null;
			this.pendingRecipe = null;
			clearPendingGroupInput();
			return false;
		}
		var preview = ChatRecipeTooltip.INSTANCE;
		if (preview.handleTags(screen, input, keyBindings)) {
			return true;
		}
		if (preview.isPinned()) {
			pendingInput = null;
			pendingRecipe = null;
			clearPendingGroupInput();
			UserInput previewInput = new UserInput(input.getKey(), input.getMouseX(), input.getMouseY(), input.getModifiers(), input.getInputType()) {
				@Override
				public boolean is(IJeiKeyMapping mapping) {
					if (getKey().getType() != InputConstants.Type.MOUSE && mapping instanceof IJeiKeyMappingWithExtraModifiers key) {
						return key.isActiveAndMatchesAllowingExtraModifiers(getKey());
					}
					return super.is(mapping);
				}
			};
			var ingredient = getHoveredIngredient(chatScreen, input);
			if (ingredient.isPresent() && preview.copy(ingredient.get(), previewInput, keyBindings)) {
				return true;
			}
			if (input.getInputType() == InputType.IMMEDIATE && handleImmediateInput(chatScreen, previewInput, keyBindings)) {
				return true;
			}
			// The preview owns mouse input while pinned, including blank areas above chat links.
			return input.getKey().getType() == InputConstants.Type.MOUSE;
		}
		if (handleRecipeInput(chatScreen, input, keyBindings) || handleBookmarkGroupInput(chatScreen, input, keyBindings)) {
			return true;
		}

		return switch (input.getInputType()) {
			case IMMEDIATE -> handleImmediateInput(chatScreen, input, keyBindings);
			case SIMULATE -> handleSimulateInput(chatScreen, input, keyBindings);
			case EXECUTE -> handleExecuteInput(chatScreen, input);
		};
	}

	public void handleGuiChange() {
		ChatRecipeTooltip.INSTANCE.clear();
		this.pendingInput = null;
		this.pendingRecipe = null;
		clearPendingGroupInput();
	}

	private boolean handleRecipeInput(ChatScreen screen, UserInput input, IInternalKeyMappings keyBindings) {
		Optional<RecipeLink> recipe = JeiChatItemLinkHover.getHoveredStyle(screen, input.getMouseX(), input.getMouseY())
			.flatMap(JeiChatRecipeLinks::parse);
		return switch (input.getInputType()) {
			case IMMEDIATE -> {
				if (recipe.isEmpty() || !input.is(keyBindings.getShowRecipe())) {
					yield false;
				}
				ChatRecipeTooltip.resolve(recipe.get()).ifPresent(this::showRecipe);
				yield true;
			}
			case SIMULATE -> {
				pendingRecipe = null;
				if (recipe.isEmpty() || !input.is(keyBindings.getLeftClick())) {
					yield false;
				}
				pendingInput = null;
				clearPendingGroupInput();
				pendingRecipe = new PendingRecipe(input.getKey(), recipe.get());
				yield true;
			}
			case EXECUTE -> {
				PendingRecipe pending = pendingRecipe;
				pendingRecipe = null;
				if (pending == null || !pending.key().equals(input.getKey()) || recipe.filter(pending.recipe()::equals).isEmpty()) {
					yield false;
				}
				ChatRecipeTooltip.resolve(pending.recipe()).ifPresent(this::showRecipe);
				yield true;
			}
		};
	}

	private <R> void showRecipe(IRecipeLayoutDrawable<R> layout) {
		recipesGui.showRecipes(layout.getRecipeCategory(), List.of(layout.getRecipe()), List.of());
	}

	private boolean handleBookmarkGroupInput(ChatScreen screen, UserInput input, IInternalKeyMappings keyBindings) {
		return switch (input.getInputType()) {
			case IMMEDIATE -> false;
			case SIMULATE -> {
				clearPendingGroupInput();
				if (!input.is(keyBindings.getLeftClick())) {
					yield false;
				}
				Optional<String> snapshot = getHoveredBookmarkGroupSnapshot(screen, input);
				if (snapshot.isEmpty()) {
					yield false;
				}
				pendingInput = null;
				pendingGroupKey = input.getKey();
				pendingGroupSnapshot = snapshot.get();
				yield true;
			}
			case EXECUTE -> {
				InputConstants.Key key = pendingGroupKey;
				String snapshot = pendingGroupSnapshot;
				clearPendingGroupInput();
				if (key == null || snapshot == null || !key.equals(input.getKey()) ||
					getHoveredBookmarkGroupSnapshot(screen, input).filter(snapshot::equals).isEmpty()
				) {
					yield false;
				}
				if (groupImporter.test(snapshot)) {
					JeiClientSoundUtil.playClickSound();
				}
				yield true;
			}
		};
	}

	private Optional<String> getHoveredBookmarkGroupSnapshot(ChatScreen screen, UserInput input) {
		return JeiChatItemLinkHover.getHoveredStyle(screen, input.getMouseX(), input.getMouseY())
			.flatMap(JeiChatItemLinkHover::getBookmarkGroupSnapshot);
	}

	private void clearPendingGroupInput() {
		pendingGroupKey = null;
		pendingGroupSnapshot = null;
	}

	private boolean handleImmediateInput(ChatScreen chatScreen, UserInput input, IInternalKeyMappings keyBindings) {
		Optional<Action> optionalAction = getAction(input, keyBindings);
		if (optionalAction.isEmpty()) {
			return false;
		}

		Optional<ITypedIngredient<?>> optionalIngredient = getHoveredIngredient(chatScreen, input);
		if (optionalIngredient.isEmpty()) {
			return false;
		}

		Action action = optionalAction.get();
		ITypedIngredient<?> typedIngredient = optionalIngredient.get();
		executeAction(typedIngredient, action);
		return true;
	}

	private boolean handleSimulateInput(ChatScreen chatScreen, UserInput input, IInternalKeyMappings keyBindings) {
		this.pendingInput = null;

		Optional<Action> optionalAction = getAction(input, keyBindings);
		if (input.is(keyBindings.getLeftClick()) && JeiChatItemLinkHover.getHoveredStyle(chatScreen, input.getMouseX(), input.getMouseY())
			.flatMap(SharedChatIngredient::getSnapshot).isPresent()
		) {
			optionalAction = Optional.of(Action.SHOW_RECIPE);
		}
		if (optionalAction.isEmpty()) {
			return false;
		}

		Optional<ITypedIngredient<?>> optionalIngredient = getHoveredIngredient(chatScreen, input);
		if (optionalIngredient.isEmpty()) {
			return false;
		}

		Action action = optionalAction.get();
		ITypedIngredient<?> typedIngredient = optionalIngredient.get();
		this.pendingInput = new PendingInput(input.getKey(), typedIngredient, action);
		return true;
	}

	private boolean handleExecuteInput(ChatScreen chatScreen, UserInput input) {
		PendingInput pendingInput = this.pendingInput;
		this.pendingInput = null;
		if (pendingInput == null) {
			return false;
		}
		if (!pendingInput.key().equals(input.getKey())) {
			return false;
		}

		Optional<ITypedIngredient<?>> optionalIngredient = getHoveredIngredient(chatScreen, input);
		if (optionalIngredient.isEmpty()) {
			return false;
		}

		ITypedIngredient<?> typedIngredient = optionalIngredient.get();
		if (typedIngredient != pendingInput.typedIngredient()) {
			return false;
		}

		executeAction(typedIngredient, pendingInput.action());
		return true;
	}

	private Optional<ITypedIngredient<?>> getHoveredIngredient(ChatScreen chatScreen, UserInput input) {
		return screenHelper.getClickableIngredientUnderMouse(chatScreen, input.getMouseX(), input.getMouseY())
			.map(ChatLinkInputHandler::getTypedIngredient)
			.findFirst();
	}

	private static ITypedIngredient<?> getTypedIngredient(IClickableIngredient<?> clickableIngredient) {
		return clickableIngredient.getTypedIngredient();
	}

	private static Optional<Action> getAction(UserInput input, IInternalKeyMappings keyBindings) {
		if (input.is(keyBindings.getShowRecipe())) {
			return Optional.of(Action.SHOW_RECIPE);
		}
		if (input.is(keyBindings.getShowUses())) {
			return Optional.of(Action.SHOW_USES);
		}
		if (input.is(keyBindings.getBookmark())) {
			return Optional.of(Action.BOOKMARK);
		}
		return Optional.empty();
	}

	private void executeAction(ITypedIngredient<?> typedIngredient, Action action) {
		switch (action) {
			case SHOW_RECIPE -> show(typedIngredient, List.of(RecipeIngredientRole.OUTPUT));
			case SHOW_USES -> show(typedIngredient, List.of(RecipeIngredientRole.INPUT, RecipeIngredientRole.CATALYST));
			case BOOKMARK -> bookmarkList.add(typedIngredient);
		}
	}

	private void show(ITypedIngredient<?> typedIngredient, List<RecipeIngredientRole> roles) {
		IngredientElement<?> element = new IngredientElement<>(typedIngredient);
		element.show(recipesGui, focusUtil, roles);
	}

	private enum Action {
		SHOW_RECIPE,
		SHOW_USES,
		BOOKMARK
	}

	private record PendingInput(InputConstants.Key key, ITypedIngredient<?> typedIngredient, Action action) {
	}
}
