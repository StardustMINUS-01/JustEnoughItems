package mezz.jei.gui.recipes;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.Internal;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator.ClientFallbackStarter;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public final class RecipeLayoutWithButtons<R> implements IRecipeLayoutWithButtons<R> {

	public static <T> IRecipeLayoutWithButtons<T> create(
		IRecipeLayoutDrawable<T> recipeLayoutDrawable,
		@Nullable RecipeBookmark<?, ?> recipeBookmark,
		BookmarkList bookmarks,
		RecipesGui recipesGui,
		List<IRecipeButtonControllerFactory> extraButtonControllerFactories
	) {
		RecipesGui.RecipeLayoutForkExtras forkExtras = recipesGui.createRecipeLayoutForkExtras(recipeLayoutDrawable);
		RecipeTransferButtonController transferButton = new RecipeTransferButtonController(
			recipeLayoutDrawable,
			recipesGui,
			forkExtras.inputSlotSelectionState()
		);
		RecipeBookmarkButtonController bookmarkButton = new RecipeBookmarkButtonController(bookmarks, recipeLayoutDrawable, recipeBookmark);

		List<IconButton> buttons = new ArrayList<>();
		buttons.add(new IconButton(transferButton));
		buttons.add(new IconButton(bookmarkButton));
		for (IRecipeButtonControllerFactory buttonControllerFactory : extraButtonControllerFactories) {
			IIconButtonController buttonController = buttonControllerFactory.createButtonController(recipeLayoutDrawable);
			if (buttonController != null) {
				buttons.add(new IconButton(buttonController));
			}
		}

		return new RecipeLayoutWithButtons<>(
			recipeLayoutDrawable,
			transferButton,
			recipeBookmark,
			bookmarks,
			buttons,
			forkExtras.favoriteButton(),
			forkExtras.inputSlotSelectionState(),
			forkExtras.clientFallbackStarter(),
			forkExtras.showBookmarkPanel()
		);
	}

	private final IRecipeLayoutDrawable<R> recipeLayout;
	private final RecipeTransferButtonController transferButton;
	private final @Nullable RecipeBookmark<?, ?> recipeBookmark;
	private final BookmarkList bookmarks;
	private final List<IconButton> buttons;
	private final @Nullable RecipeFavoriteButton favoriteButton;
	private final @Nullable InputSlotSelectionState inputSlotSelectionState;
	private final @Nullable ClientFallbackStarter clientFallbackStarter;
	private final Runnable showBookmarkPanel;
	private ImmutableRect2i transferButtonArea = ImmutableRect2i.EMPTY;
	private ImmutableRect2i bookmarkButtonArea = ImmutableRect2i.EMPTY;

	private RecipeLayoutWithButtons(
		IRecipeLayoutDrawable<R> recipeLayout,
		RecipeTransferButtonController transferButton,
		@Nullable RecipeBookmark<?, ?> recipeBookmark,
		BookmarkList bookmarks,
		List<IconButton> buttons,
		@Nullable RecipeFavoriteButton favoriteButton,
		@Nullable InputSlotSelectionState inputSlotSelectionState,
		@Nullable ClientFallbackStarter clientFallbackStarter,
		Runnable showBookmarkPanel
	) {
		this.recipeLayout = recipeLayout;
		this.transferButton = transferButton;
		this.recipeBookmark = recipeBookmark;
		this.bookmarks = bookmarks;
		this.buttons = buttons;
		this.favoriteButton = favoriteButton;
		this.inputSlotSelectionState = inputSlotSelectionState;
		this.clientFallbackStarter = clientFallbackStarter;
		this.showBookmarkPanel = showBookmarkPanel;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		recipeLayout.drawRecipe(guiGraphics, mouseX, mouseY);

		for (IconButton button : buttons) {
			if (button.isVisible()) {
				button.draw(guiGraphics, mouseX, mouseY, partialTicks);
			}
		}
		if (favoriteButton != null && favoriteButton.isVisible()) {
			favoriteButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
		}
	}

	private ImmutableRect2i getAbsoluteButtonArea(int buttonIndex) {
		Rect2i recipeLayoutRect = recipeLayout.getRect();
		Rect2i buttonArea = recipeLayout.getSideButtonArea(buttonIndex);
		return new ImmutableRect2i(
			buttonArea.getX() + recipeLayoutRect.getX(),
			buttonArea.getY() + recipeLayoutRect.getY(),
			buttonArea.getWidth(),
			buttonArea.getHeight()
		);
	}

	@Override
	public void updateBounds(int recipeXOffset, int recipeYOffset) {
		Rect2i rectWithBorder = recipeLayout.getRectWithBorder();
		Rect2i rect = recipeLayout.getRect();
		recipeLayout.setPosition(
			recipeXOffset - rectWithBorder.getX() + rect.getX(),
			recipeYOffset - rectWithBorder.getY() + rect.getY()
		);

		int i = 0;
		for (IconButton button : buttons) {
			if (button.isVisible()) {
				ImmutableRect2i buttonArea = getAbsoluteButtonArea(i);
				if (buttonArea.getWidth() * buttonArea.getHeight() > 0) {
					button.updateBounds(buttonArea);
				}
				i++;
			}
		}
		Rect2i layoutRect = recipeLayout.getRect();
		this.transferButtonArea = toImmutable(offset(recipeLayout.getRecipeTransferButtonArea(), layoutRect));
		this.bookmarkButtonArea = toImmutable(offset(recipeLayout.getRecipeBookmarkButtonArea(), layoutRect));
		if (favoriteButton != null) {
			favoriteButton.updateBounds(toImmutable(offset(recipeLayout.getSideButtonArea(i), layoutRect)));
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
		Rect2i area = recipeLayout.getRect();
		Rect2i areaWithBorder = recipeLayout.getRectWithBorder();
		int leftBorderWidth = area.getX() - areaWithBorder.getX();
		int rightAreaWidth = areaWithBorder.getWidth() - leftBorderWidth;

		int i = 0;
		for (IconButton button : buttons) {
			if (button.isVisible()) {
				Rect2i buttonArea = recipeLayout.getSideButtonArea(i);
				int buttonRight = buttonArea.getX() + buttonArea.getWidth();
				rightAreaWidth = Math.max(buttonRight, rightAreaWidth);
				i++;
			}
		}
		if (favoriteButton != null && favoriteButton.isVisible()) {
			Rect2i buttonArea = recipeLayout.getRecipeBookmarkButtonArea();
			int buttonRight = buttonArea.getX() + buttonArea.getWidth();
			rightAreaWidth = Math.max(buttonRight, rightAreaWidth);
		}

		return leftBorderWidth + rightAreaWidth;
	}

	@Override
	public IUserInputHandler createUserInputHandler() {
		List<IUserInputHandler> inputHandlers = new ArrayList<>();
		for (IconButton button : buttons) {
			inputHandlers.add(button.createInputHandler());
		}
		if (favoriteButton != null) {
			inputHandlers.add(favoriteButton.createInputHandler());
		}
		if (inputSlotSelectionState != null && recipeBookmark != null) {
			BiPredicate<Double, Double> bookmarkButtonIsMouseOver = (mouseX, mouseY) -> this.bookmarkButtonArea.contains(mouseX, mouseY);
			BiPredicate<Double, Double> transferButtonIsMouseOver = (mouseX, mouseY) -> this.transferButtonArea.contains(mouseX, mouseY);
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
			inputHandlers.add(new RecipeAutoCraftingInputHandler(recipeLayout, clientFallbackStarter));
		}
		inputHandlers.add(new RecipeLayoutUserInputHandler<>(recipeLayout, inputSlotSelectionState));

		return new CombinedInputHandler("RecipeLayoutWithButtons", inputHandlers);
	}

	public void tick(@Nullable AbstractContainerMenu parentContainer, @Nullable Player player) {
		tick();
		if (inputSlotSelectionState != null) {
			inputSlotSelectionState.apply(recipeLayout);
		}
		if (favoriteButton != null) {
			favoriteButton.tick();
		}
	}

	@Override
	public void tick() {
		recipeLayout.tick();
		for (IconButton button : buttons) {
			button.tick();
		}
	}

	boolean addRecipeBookmarkGroup(UserInput input, boolean preserveAmount) {
		if (inputSlotSelectionState == null) {
			return false;
		}
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

	Map<Integer, BookmarkIngredientKey> getInputSelections() {
		return inputSlotSelectionState == null ? Map.of() : inputSlotSelectionState.selectedKeys();
	}

	void restoreInputSelections(Map<Integer, BookmarkIngredientKey> selections) {
		if (inputSlotSelectionState != null) {
			inputSlotSelectionState.setSelectedKeys(selections);
			inputSlotSelectionState.apply(recipeLayout);
		}
	}

	@Override
	public void drawTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		for (IconButton button : buttons) {
			if (button.isVisible() && button.isMouseOver(mouseX, mouseY)) {
				button.drawTooltips(guiGraphics, mouseX, mouseY);
				return;
			}
		}
	}

	@Override
	public int getMissingCountHint() {
		return transferButton.getMissingCountHint();
	}

	private record RecipeLayoutUserInputHandler<R>(
		IRecipeLayoutDrawable<R> recipeLayout,
		@Nullable InputSlotSelectionState inputSlotSelectionState
	) implements IUserInputHandler {

		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			final double mouseX = input.getMouseX();
			final double mouseY = input.getMouseY();
			if (recipeLayout.isMouseOver(mouseX, mouseY)) {
				InputConstants.Key key = input.getKey();
				boolean simulate = input.isSimulate();

				if (recipeLayout.getInputHandler().handleInput(mouseX, mouseY, input)) {
					return Optional.of(this);
				}

				IInternalKeyMappings keyMappings = Internal.getKeyMappings();
				if (keyMappings.getCopyRecipeId().isActiveAndMatches(key)) {
					if (handleCopyRecipeId(recipeLayout, simulate)) {
						return Optional.of(this);
					}
				}
			}
			return Optional.empty();
		}

		private boolean handleCopyRecipeId(IRecipeLayoutDrawable<R> recipeLayout, boolean simulate) {
			if (simulate) {
				return true;
			}
			Minecraft minecraft = Minecraft.getInstance();
			LocalPlayer player = minecraft.player;
			IRecipeCategory<R> recipeCategory = recipeLayout.getRecipeCategory();
			R recipe = recipeLayout.getRecipe();
			ResourceLocation registryName = recipeCategory.getRegistryName(recipe);
			if (registryName == null) {
				MutableComponent message = Component.translatable("jei.message.copy.recipe.id.failure");
				if (player != null) {
					player.displayClientMessage(message, false);
				}
				return false;
			}

			String recipeId = registryName.toString();
			minecraft.keyboardHandler.setClipboard(recipeId);
			MutableComponent message = Component.translatable("jei.message.copy.recipe.id.success", Component.literal(recipeId));
			if (player != null) {
				player.displayClientMessage(message, false);
			}
			return true;
		}

		@Override
		public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
			boolean synchronizeFamily = Screen.hasShiftDown();
			if (inputSlotSelectionState != null &&
				(synchronizeFamily || Screen.hasControlDown()) &&
				inputSlotSelectionState.scroll(recipeLayout, mouseX, mouseY, scrollDeltaY, synchronizeFamily)
			) {
				return Optional.of(this);
			}
			if (recipeLayout.isMouseOver(mouseX, mouseY) &&
				recipeLayout.getInputHandler().handleMouseScrolled(mouseX, mouseY, scrollDeltaX, scrollDeltaY)
			) {
				return Optional.of(this);
			}

			return Optional.empty();
		}
	}
}
