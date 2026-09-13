package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.common.Internal;
import mezz.jei.common.chat.JeiChatRecipeLinks;
import mezz.jei.common.network.packets.PacketShareRecipe;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

final class RecipeLayoutWithExtras<R> implements IRecipeLayoutWithButtons<R> {
	private final RecipeLayoutWithButtons<R> delegate;
	private final IRecipeLayoutDrawable<R> recipeLayout;
	private final @Nullable RecipeBookmark<?, ?> recipeBookmark;
	private final BookmarkList bookmarks;
	private final @Nullable RecipeFavoriteButton favoriteButton;
	private final InputSlotSelectionState inputSlotSelectionState;
	private final Runnable showBookmarkPanel;
	private final IconButton transferButton;
	private final IconButton bookmarkButton;

	RecipeLayoutWithExtras(RecipeLayoutWithButtons<R> delegate, BookmarkList bookmarks, RecipesGui.RecipeLayoutForkExtras extras, IconButton transferButton, IconButton bookmarkButton) {
		this.delegate = delegate;
		this.recipeLayout = delegate.getRecipeLayout();
		this.recipeBookmark = delegate.getRecipeBookmark();
		this.bookmarks = bookmarks;
		this.favoriteButton = extras.favoriteButton();
		this.inputSlotSelectionState = extras.inputSlotSelectionState();
		this.showBookmarkPanel = extras.showBookmarkPanel();
		this.transferButton = transferButton;
		this.bookmarkButton = bookmarkButton;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		delegate.draw(guiGraphics, mouseX, mouseY, partialTicks);
		if (favoriteButton != null && favoriteButton.isVisible()) {
			favoriteButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
		}
	}

	@Override
	public void updateBounds(int recipeXOffset, int recipeYOffset) {
		int buttonCount = delegate.updateBoundsAndGetButtonCount(recipeXOffset, recipeYOffset);
		Rect2i layoutRect = recipeLayout.getRect();
		if (favoriteButton != null) {
			favoriteButton.updateBounds(toImmutable(offset(recipeLayout.getSideButtonArea(buttonCount), layoutRect)));
		}
	}

	private static ImmutableRect2i toImmutable(Rect2i rect2i) {
		return new ImmutableRect2i(rect2i.getX(), rect2i.getY(), rect2i.getWidth(), rect2i.getHeight());
	}

	private static Rect2i offset(Rect2i area, Rect2i offset) {
		return new Rect2i(
			area.getX() + offset.getX(),
			area.getY() + offset.getY(),
			area.getWidth(),
			area.getHeight()
		);
	}

	@Override
	public int totalWidth() {
		int width = delegate.totalWidth();
		if (favoriteButton != null && favoriteButton.isVisible()) {
			Rect2i buttonArea = recipeLayout.getRecipeBookmarkButtonArea();
			int leftBorder = recipeLayout.getRect().getX() - recipeLayout.getRectWithBorder().getX();
			width = Math.max(width, leftBorder + buttonArea.getX() + buttonArea.getWidth());
		}
		return width;
	}

	@Override
	public IUserInputHandler createUserInputHandler() {
		List<IUserInputHandler> inputHandlers = new ArrayList<>();
		if (favoriteButton != null) {
			inputHandlers.add(favoriteButton.createInputHandler());
		}
		if (recipeBookmark != null) {
			BiPredicate<Double, Double> bookmarkButtonIsMouseOver = bookmarkButton::isMouseOver;
			inputHandlers.add(new IUserInputHandler() {
				@Override
				public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
					if (input.is(keyBindings.getRightClick()) && bookmarkButtonIsMouseOver.test(input.getMouseX(), input.getMouseY())) {
						shareRecipe(input);
						return Optional.of(this);
					}
					return Optional.empty();
				}
			});
			BiPredicate<Double, Double> transferButtonIsMouseOver = transferButton::isMouseOver;
			BiFunction<UserInput, Boolean, Boolean> addRecipeBookmarkGroup = this::addRecipeBookmarkGroup;
			inputHandlers.add(new RecipeBookmarkButtonHotkeyInputHandler(
				bookmarkButtonIsMouseOver,
				addRecipeBookmarkGroup,
				JeiClientSoundUtil::playClickSound
			));
			inputHandlers.add(new RecipeBookmarkButtonHotkeyInputHandler(
				transferButtonIsMouseOver,
				addRecipeBookmarkGroup,
				JeiClientSoundUtil::playClickSound
			));
			inputHandlers.add(new RecipeGhostOverlayInputHandler(recipeLayout, recipeLayout::isMouseOver));
			inputHandlers.add(new RecipeGhostOverlayInputHandler(recipeLayout, transferButtonIsMouseOver));
			inputHandlers.add(new RecipeAutoCraftingInputHandler(recipeLayout));
		}
		inputHandlers.add(new CandidateScrollInputHandler(recipeLayout, inputSlotSelectionState));

		return delegate.createUserInputHandler(inputHandlers);
	}

	private void shareRecipe(UserInput input) {
		if (input.isSimulate() || recipeBookmark == null || !Internal.getServerConnection().canSendPacket(PacketShareRecipe.TYPE)) {
			return;
		}
		var recipe = new JeiChatRecipeLinks.RecipeLink(recipeLayout.getRecipeCategory().getRecipeType().getUid(), recipeBookmark.getRecipeUid());
		if (recipe.recipeType().toString().length() > JeiChatRecipeLinks.MAX_ID_LENGTH || recipe.recipeId().toString().length() > JeiChatRecipeLinks.MAX_ID_LENGTH) {
			return;
		}
		String title = getShareTitle(recipeBookmark.getDisplayIngredient());
		title = title.substring(0, Math.min(title.length(), JeiChatRecipeLinks.MAX_TITLE_LENGTH));
		Internal.getServerConnection().sendPacketToServer(new PacketShareRecipe(recipe, title));
		JeiClientSoundUtil.playClickSound();
	}

	private static <T> String getShareTitle(ITypedIngredient<T> ingredient) {
		return Internal.getJeiRuntime().getIngredientManager().getIngredientHelper(ingredient.getType())
			.getDisplayName(ingredient.getIngredient());
	}

	@Override
	public void tick() {
		delegate.tick();
		inputSlotSelectionState.apply(recipeLayout);
		if (favoriteButton != null) {
			favoriteButton.tick();
		}
	}

	boolean addRecipeBookmarkGroup(UserInput input, boolean preserveAmount) {

		if (!input.isSimulate()) {
			boolean added = bookmarks.addRecipeBookmarks(
				recipeLayout,
				preserveAmount,
				inputSlotSelectionState.selectedKeys(),
				inputSlotSelectionState.filteredCandidates()
			);
			if (added) {
				showBookmarkPanel.run();
			}
			return added;
		}
		return recipeBookmark != null;
	}

	@Override
	public IRecipeLayoutDrawable<R> getRecipeLayout() {
		return recipeLayout;
	}

	@Override
	public @Nullable RecipeBookmark<?, ?> getRecipeBookmark() {
		return recipeBookmark;
	}

	boolean selectInputCandidate(IRecipeSlotView slot, ITypedIngredient<?> ingredient, boolean synchronizeFamily) {
		return inputSlotSelectionState.select(recipeLayout, slot, ingredient, synchronizeFamily, true);
	}

	Map<Integer, BookmarkIngredientKey> getInputSelections() {
		return inputSlotSelectionState.selectedKeys();
	}

	void restoreInputSelections(Map<Integer, BookmarkIngredientKey> selections) {
		inputSlotSelectionState.setSelectedKeys(selections);
		inputSlotSelectionState.apply(recipeLayout);
	}

	@Override
	public void drawTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		delegate.drawTooltips(guiGraphics, mouseX, mouseY);
	}

	@Override
	public int getMissingCountHint() {
		return delegate.getMissingCountHint();
	}

	private record CandidateScrollInputHandler(IRecipeLayoutDrawable<?> recipeLayout, InputSlotSelectionState selections) implements IUserInputHandler {
		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			return Optional.empty();
		}

		@Override
		public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
			boolean synchronizeFamily = Screen.hasShiftDown();
			if ((synchronizeFamily || Screen.hasControlDown()) &&
				selections.scroll(recipeLayout, mouseX, mouseY, scrollDeltaY, synchronizeFamily)
			) {
				return Optional.of(this);
			}
			return Optional.empty();
		}
	}
}
