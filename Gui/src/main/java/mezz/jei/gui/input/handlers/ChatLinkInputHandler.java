package mezz.jei.gui.input.handlers;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.chat.JeiChatItemLinkHover;
import mezz.jei.common.chat.JeiChatRecipeLinks;
import mezz.jei.common.chat.JeiChatRecipeLinks.RecipeLink;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.chat.ChatRecipeTooltip;
import mezz.jei.common.chat.SharedChatIngredient;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.config.BookmarkJsonSerializer;
import mezz.jei.gui.config.BookmarkConfigEntry;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import mezz.jei.common.input.interaction.ConsumedInput;
import mezz.jei.gui.input.InputArea;
import net.minecraft.client.Minecraft;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers;
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Optional;

public class ChatLinkInputHandler implements IInputTarget {
	private final IRecipesGui recipesGui;
	private final FocusUtil focusUtil;
	private final IScreenHelper screenHelper;
	private final BookmarkList bookmarkList;
	private final Codec<BookmarkConfigEntry> bookmarkEntryCodec;
	private final DynamicOps<JsonElement> bookmarkRegistryOps;

	public ChatLinkInputHandler(
		IRecipesGui recipesGui,
		FocusUtil focusUtil,
		IScreenHelper screenHelper,
		BookmarkList bookmarkList,
		Codec<BookmarkConfigEntry> bookmarkEntryCodec,
		DynamicOps<JsonElement> bookmarkRegistryOps
	) {
		this.recipesGui = recipesGui;
		this.focusUtil = focusUtil;
		this.screenHelper = screenHelper;
		this.bookmarkList = bookmarkList;
		this.bookmarkEntryCodec = bookmarkEntryCodec;
		this.bookmarkRegistryOps = bookmarkRegistryOps;
	}

	public InputArea createInputArea() {
		return InputArea.builder("JEIU chat links", (x, y) -> Minecraft.getInstance().screen instanceof ChatScreen)
			.controls(this)
			.build();
	}

	@Override
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (!(screen instanceof ChatScreen chatScreen)) {
			return Optional.empty();
		}
		var preview = ChatRecipeTooltip.INSTANCE;
		if (preview.isPinned()) {
			UserInput previewInput = new UserInput(input.getKey(), input.getMouseX(), input.getMouseY(), input.getModifiers(), input.getPhase()) {
				@Override
				public boolean is(IJeiKeyMapping mapping) {
					if (isKeyboardInput() && mapping instanceof IJeiKeyMappingWithExtraModifiers key) {
						return key.isActiveAndMatchesAllowingExtraModifiers(getKey());
					}
					return super.is(mapping);
				}
			};
			Optional<ITypedIngredient<?>> ingredient = getHoveredIngredient(chatScreen, input);
			if (ingredient.isPresent() && preview.copy(ingredient.get(), previewInput, keyBindings)) {
				return Optional.of(input.isKeyboardInput() ? ConsumedInput.INSTANCE : new InputAction(release -> preview.copy(ingredient.get(), release, keyBindings)));
			}
			if (input.isKeyboardInput() && ingredient.isPresent()) {
				Optional<Action> action = getAction(previewInput, keyBindings);
				if (action.isPresent()) {
					return Optional.of(InputAction.run(() -> executeAction(ingredient.get(), action.get())));
				}
			}
			return input.isMouseInput() ? Optional.of(ConsumedInput.INSTANCE) : Optional.empty();
		}
		var style = JeiChatItemLinkHover.getHoveredStyle(chatScreen, input.getMouseX(), input.getMouseY());
		Optional<RecipeLink> recipe = style.flatMap(JeiChatRecipeLinks::parse);
		boolean leftClick = input.is(keyBindings.getLeftClick());
		if (recipe.isPresent() && (input.isKeyboardInput() ? input.is(keyBindings.getShowRecipe()) : leftClick)) {
			return Optional.of(new InputAction(release -> ChatRecipeTooltip.resolve(recipe.get()).ifPresent(this::showRecipe),
				release -> release.isKeyboardInput() || JeiChatItemLinkHover.getHoveredStyle(chatScreen, release.getMouseX(), release.getMouseY())
					.flatMap(JeiChatRecipeLinks::parse).filter(recipe.get()::equals).isPresent()));
		}
		Optional<String> groupSnapshot = style.flatMap(JeiChatItemLinkHover::getBookmarkGroupSnapshot);
		if (leftClick && groupSnapshot.isPresent()) {
			return Optional.of(new InputAction(release -> importBookmarkGroup(groupSnapshot.get()),
				release -> getHoveredBookmarkGroupSnapshot(chatScreen, release).filter(groupSnapshot.get()::equals).isPresent()));
		}
		if (leftClick && style.flatMap(SharedChatIngredient::getSnapshot).isPresent()) {
			return getHoveredIngredient(chatScreen, input)
				.map(ingredient -> new InputAction(release -> executeAction(ingredient, Action.SHOW_RECIPE),
					release -> JeiChatItemLinkHover.getHoveredStyle(chatScreen, release.getMouseX(), release.getMouseY()).equals(style)));
		}
		// Ordinary chat ingredients use the upstream ChatCommands area.
		return Optional.empty();
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, double scrollX, double scrollY) {
		return ChatRecipeTooltip.INSTANCE.scroll(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void resetInput() {
		ChatRecipeTooltip.INSTANCE.clear();
	}

	private <R> void showRecipe(IRecipeLayoutDrawable<R> layout) {
		recipesGui.showRecipes(layout.getRecipeCategory(), List.of(layout.getRecipe()), List.of());
	}

	private Optional<String> getHoveredBookmarkGroupSnapshot(ChatScreen chatScreen, UserInput input) {
		return JeiChatItemLinkHover.getHoveredStyle(chatScreen, input.getMouseX(), input.getMouseY())
			.flatMap(JeiChatItemLinkHover::getBookmarkGroupSnapshot);
	}

	private void importBookmarkGroup(String snapshot) {
		if (BookmarkJsonSerializer.deserializeGroupSnapshot(snapshot, bookmarkList, bookmarkEntryCodec, bookmarkRegistryOps)
			.isPresent()
		) {
			JeiClientSoundUtil.playClickSound();
		}
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

}
