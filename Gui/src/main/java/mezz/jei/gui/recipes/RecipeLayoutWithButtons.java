package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator.ClientFallbackStarter;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record RecipeLayoutWithButtons<R>(
	IRecipeLayoutDrawable<R> recipeLayout,
	RecipeTransferButton transferButton,
	RecipeFavoriteButton favoriteButton,
	RecipeBookmarkButton bookmarkButton,
	ClientFallbackStarter clientFallbackStarter,
	InputSlotSelectionState inputSlotSelectionState
) {
	public int totalWidth() {
		Rect2i area = recipeLayout.getRect();
		Rect2i areaWithBorder = recipeLayout.getRectWithBorder();
		int leftBorderWidth = area.getX() - areaWithBorder.getX();
		int rightAreaWidth = areaWithBorder.getWidth() - leftBorderWidth;

		if (transferButton.isVisible()) {
			Rect2i buttonArea = recipeLayout.getRecipeTransferButtonArea();
			int buttonRight = buttonArea.getX() + buttonArea.getWidth();
			rightAreaWidth = Math.max(buttonRight, rightAreaWidth);
		}

		if (bookmarkButton.isVisible()) {
			Rect2i buttonArea = recipeLayout.getRecipeBookmarkButtonArea();
			int buttonRight = buttonArea.getX() + buttonArea.getWidth();
			rightAreaWidth = Math.max(buttonRight, rightAreaWidth);
		}

		if (favoriteButton.isVisible()) {
			Rect2i buttonArea = recipeLayout.getRecipeBookmarkButtonArea();
			int buttonRight = buttonArea.getX() + buttonArea.getWidth();
			rightAreaWidth = Math.max(buttonRight, rightAreaWidth);
		}

		return leftBorderWidth + rightAreaWidth;
	}

	public IUserInputHandler createUserInputHandler() {
		return new CombinedInputHandler(
			"RecipeLayoutWithButtons",
			favoriteButton.createInputHandler(),
			bookmarkButton.createInputHandler(),
			transferButton.createInputHandler(),
			new RecipeBookmarkButtonHotkeyInputHandler(
				bookmarkButton::isMouseOver,
				bookmarkButton::addRecipeBookmarkGroup,
				JeiClientSoundUtil::playClickSound
			),
			new RecipeBookmarkButtonHotkeyInputHandler(
				transferButton::isMouseOver,
				bookmarkButton::addRecipeBookmarkGroup,
				JeiClientSoundUtil::playClickSound
			),
			new RecipeGhostOverlayInputHandler(recipeLayout, recipeLayout::isMouseOver),
			new RecipeGhostOverlayInputHandler(recipeLayout, transferButton::isMouseOver),
			new RecipeAutoCraftingInputHandler(recipeLayout, clientFallbackStarter),
			new RecipeLayoutUserInputHandler<>(recipeLayout, inputSlotSelectionState)
		);
	}

	public void tick(@Nullable AbstractContainerMenu parentContainer, @Nullable Player player) {
		recipeLayout.tick();
		inputSlotSelectionState.apply(recipeLayout);
		transferButton.update(parentContainer, player);
		favoriteButton.tick();
		bookmarkButton.tick();
	}

	private record RecipeLayoutUserInputHandler<R>(
		IRecipeLayoutDrawable<R> recipeLayout,
		InputSlotSelectionState inputSlotSelectionState
	) implements IUserInputHandler {

		@Override
		public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
			final double mouseX = input.getMouseX();
			final double mouseY = input.getMouseY();
			if (recipeLayout.isMouseOver(mouseX, mouseY)) {
				if (recipeLayout.getInputHandler().handleInput(mouseX, mouseY, input)) {
					return Optional.of(this);
				}
			}
			return Optional.empty();
		}

		@Override
		public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
			if (inputSlotSelectionState.scroll(recipeLayout, mouseX, mouseY, scrollDeltaY, Screen.hasControlDown())) {
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
