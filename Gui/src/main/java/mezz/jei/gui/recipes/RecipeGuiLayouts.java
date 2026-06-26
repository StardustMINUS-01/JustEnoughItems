package mezz.jei.gui.recipes;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.input.ClickableIngredientInternal;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.FocusedRecipeCandidate;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.NullInputHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class RecipeGuiLayouts {
	private final List<RecipeLayoutWithButtons<?>> recipeLayoutsWithButtons = new ArrayList<>();
	@Nullable
	private IUserInputHandler cachedInputHandler;

	public RecipeGuiLayouts() {
		this.cachedInputHandler = NullInputHandler.INSTANCE;
	}

	public void updateLayout(ImmutableRect2i recipeLayoutsArea, final int recipesPerPage) {
		if (this.recipeLayoutsWithButtons.isEmpty()) {
			return;
		}
		RecipeLayoutWithButtons<?> firstLayout = this.recipeLayoutsWithButtons.get(0);
		ImmutableRect2i layoutAreaWithBorder = new ImmutableRect2i(firstLayout.recipeLayout().getRectWithBorder());
		final int recipeXOffset = getRecipeXOffset(layoutAreaWithBorder, recipeLayoutsArea);

		final int recipeHeight = layoutAreaWithBorder.getHeight();
		final int availableHeight = Math.max(recipeLayoutsArea.getHeight(), recipeHeight);
		final int remainingHeight = availableHeight - (recipesPerPage * recipeHeight);
		final int recipeSpacing = remainingHeight / (recipesPerPage + 1);

		final int spacingY = recipeHeight + recipeSpacing;
		int recipeYOffset = recipeLayoutsArea.getY() + recipeSpacing;
		for (RecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.recipeLayout();
			Rect2i rectWithBorder = recipeLayout.getRectWithBorder();
			Rect2i rect = recipeLayout.getRect();
			recipeLayout.setPosition(
				recipeXOffset - rectWithBorder.getX() + rect.getX(),
				recipeYOffset - rectWithBorder.getY() + rect.getY()
			);
			recipeYOffset += spacingY;
		}

		updateRecipeButtonPositions();
	}

	private void updateRecipeButtonPositions() {
		for (RecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.recipeLayout();
			Rect2i layoutArea = recipeLayout.getRect();
			Rect2i transferButtonArea = offset(recipeLayout.getRecipeTransferButtonArea(), layoutArea);
			Rect2i bookmarkButtonArea = offset(recipeLayout.getRecipeBookmarkButtonArea(), layoutArea);

			{
				RecipeTransferButton button = recipeLayoutWithButtons.transferButton();
				button.updateBounds(transferButtonArea);
			}
			{
				RecipeBookmarkButton button = recipeLayoutWithButtons.bookmarkButton();
				button.updateBounds(bookmarkButtonArea);
			}
			{
				RecipeFavoriteButton button = recipeLayoutWithButtons.favoriteButton();
				button.updateBounds(calculateRecipeFavoriteButtonArea(transferButtonArea, bookmarkButtonArea));
			}
		}
	}

	public static Rect2i calculateRecipeFavoriteButtonArea(Rect2i transferButtonArea, Rect2i bookmarkButtonArea) {
		int bookmarkToTransferGap = Math.max(
			0,
			transferButtonArea.getY() - (bookmarkButtonArea.getY() + bookmarkButtonArea.getHeight())
		);
		return new Rect2i(
			bookmarkButtonArea.getX(),
			bookmarkButtonArea.getY() - bookmarkButtonArea.getHeight() - bookmarkToTransferGap,
			bookmarkButtonArea.getWidth(),
			bookmarkButtonArea.getHeight()
		);
	}

	private static Rect2i offset(Rect2i area, Rect2i offset) {
		return new Rect2i(
			area.getX() + offset.getX(),
			area.getY() + offset.getY(),
			area.getWidth(),
			area.getHeight()
		);
	}

	private int getRecipeXOffset(ImmutableRect2i layoutRect, ImmutableRect2i layoutsArea) {
		if (recipeLayoutsWithButtons.isEmpty()) {
			return layoutsArea.getX();
		}

		final int recipeWidth = layoutRect.getWidth();
		final int recipeWidthWithButtons = recipeLayoutsWithButtons.get(0).totalWidth();
		final int buttonSpace = recipeWidthWithButtons - recipeWidth;

		final int availableArea = layoutsArea.getWidth();
		if (availableArea > recipeWidth + (2 * buttonSpace)) {
			// we have enough room to nicely draw the recipe centered with the buttons off to the side
			return layoutsArea.getX() + (layoutsArea.getWidth() - recipeWidth) / 2;
		} else {
			// we can just barely fit, center the recipe and buttons all together in the available area
			return layoutsArea.getX() + (layoutsArea.getWidth() - recipeWidthWithButtons) / 2;
		}
	}

	public IUserInputHandler createInputHandler() {
		return new ProxyInputHandler(() -> {
			if (cachedInputHandler == null) {
				List<IUserInputHandler> handlers = this.recipeLayoutsWithButtons.stream()
					.map(RecipeLayoutWithButtons::createUserInputHandler)
					.toList();
				cachedInputHandler = new CombinedInputHandler("RecipeGuiLayouts", handlers);
			}
			return cachedInputHandler;
		});
	}

	public void tick(@Nullable AbstractContainerMenu parentContainer) {
		Player player = Minecraft.getInstance().player;
		for (RecipeLayoutWithButtons<?> recipeLayoutWithButtons : this.recipeLayoutsWithButtons) {
			recipeLayoutWithButtons.tick(parentContainer, player);
		}
	}

	public void setRecipeLayoutsWithButtons(List<RecipeLayoutWithButtons<?>> recipeLayoutsWithButtons) {
		this.recipeLayoutsWithButtons.clear();
		this.recipeLayoutsWithButtons.addAll(recipeLayoutsWithButtons);
		this.cachedInputHandler = null;
	}

	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		return this.recipeLayoutsWithButtons.stream()
			.map(RecipeLayoutWithButtons::recipeLayout)
			.map(recipeLayout -> recipeLayout.getSlotUnderMouse(mouseX, mouseY))
			.flatMap(Optional::stream)
			.map(RecipeGuiLayouts::getClickedIngredient)
			.flatMap(Optional::stream);
	}

	public Optional<FocusedRecipeCandidate> getFocusedRecipeCandidateUnderMouse(double mouseX, double mouseY) {
		return this.recipeLayoutsWithButtons.stream()
			.map(RecipeLayoutWithButtons::recipeLayout)
			.filter(recipeLayout -> recipeLayout.isMouseOver(mouseX, mouseY))
			.filter(recipeLayout -> isOutputSlotUnderMouse(recipeLayout, mouseX, mouseY))
			.findFirst()
			.flatMap(RecipeGuiLayouts::getFocusedRecipeCandidate);
	}

	public Optional<FocusedRecipeCandidate> getRecipeTooltipCandidateUnderMouse(double mouseX, double mouseY) {
		return this.recipeLayoutsWithButtons.stream()
			.map(RecipeLayoutWithButtons::recipeLayout)
			.filter(recipeLayout -> recipeLayout.isMouseOver(mouseX, mouseY))
			.filter(recipeLayout -> !isOutputSlotUnderMouse(recipeLayout, mouseX, mouseY))
			.filter(recipeLayout -> hasDisplayedSlotUnderMouse(recipeLayout, mouseX, mouseY))
			.findFirst()
			.flatMap(RecipeGuiLayouts::getFocusedRecipeCandidate);
	}

	private static boolean isOutputSlotUnderMouse(IRecipeLayoutDrawable<?> recipeLayout, double mouseX, double mouseY) {
		return recipeLayout.getSlotUnderMouse(mouseX, mouseY)
			.map(RecipeSlotUnderMouse::slot)
			.map(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT)
			.orElse(false);
	}

	private static boolean hasDisplayedSlotUnderMouse(IRecipeLayoutDrawable<?> recipeLayout, double mouseX, double mouseY) {
		return recipeLayout.getSlotUnderMouse(mouseX, mouseY)
			.map(RecipeSlotUnderMouse::slot)
			.flatMap(slot -> slot.getDisplayedIngredient())
			.isPresent();
	}

	public boolean bookmarkRecipeUnderMouse(UserInput input, boolean preserveAmount) {
		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		for (RecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.recipeLayout();
			if (recipeLayout.isMouseOver(mouseX, mouseY) && isOutputSlotUnderMouse(recipeLayout, mouseX, mouseY)) {
				return recipeLayoutWithButtons.bookmarkButton().addRecipeBookmarkGroup(input, preserveAmount);
			}
		}
		return false;
	}

	private static Optional<IClickableIngredientInternal<?>> getClickedIngredient(RecipeSlotUnderMouse slotUnderMouse) {
		return slotUnderMouse.slot().getDisplayedIngredient()
			.map(displayedIngredient -> {
				IElement<?> element = new IngredientElement<>(displayedIngredient);
				return new ClickableIngredientInternal<>(element, slotUnderMouse::isMouseOver, false, true);
			});
	}

	private static <R> Optional<FocusedRecipeCandidate> getFocusedRecipeCandidate(IRecipeLayoutDrawable<R> recipeLayout) {
		R recipe = recipeLayout.getRecipe();
		ResourceLocation recipeUid = recipeLayout.getRecipeCategory().getRegistryName(recipe);
		if (recipeUid == null) {
			return Optional.empty();
		}
		ResourceLocation recipeTypeUid = recipeLayout.getRecipeCategory().getRecipeType().getUid();
		return Optional.of(FocusedRecipeCandidate.recipe(new FocusedRecipe(recipeTypeUid, recipeUid)));
	}

	public boolean mouseDragged(double mouseX, double mouseY, InputConstants.Key input, double dragX, double dragY) {
		for (RecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.recipeLayout();
			if (mouseDragged(recipeLayout, mouseX, mouseY, input, dragX, dragY)) {
				return true;
			}
		}
		return false;
	}

	private <R> boolean mouseDragged(IRecipeLayoutDrawable<R> recipeLayout, double mouseX, double mouseY, InputConstants.Key input, double dragX, double dragY) {
		if (recipeLayout.isMouseOver(mouseX, mouseY)) {
			IJeiInputHandler inputHandler = recipeLayout.getInputHandler();
			return inputHandler.handleMouseDragged(mouseX, mouseY, input, dragX, dragY);
		}
		return false;
	}

	public void mouseMoved(double mouseX, double mouseY) {
		for (RecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.recipeLayout();
			if (recipeLayout.isMouseOver(mouseX, mouseY)) {
				IJeiInputHandler inputHandler = recipeLayout.getInputHandler();
				inputHandler.handleMouseMoved(mouseX, mouseY);
			}
		}
	}

	public Optional<IRecipeLayoutDrawable<?>> draw(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		IRecipeLayoutDrawable<?> hoveredLayout = null;

		Minecraft minecraft = Minecraft.getInstance();
		DeltaTracker deltaTracker = minecraft.getTimer();
		float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);

		for (RecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.recipeLayout();
			if (recipeLayout.isMouseOver(mouseX, mouseY)) {
				hoveredLayout = recipeLayout;
			}
			recipeLayout.drawRecipe(guiGraphics, mouseX, mouseY);

			RecipeTransferButton transferButton = recipeLayoutWithButtons.transferButton();
			transferButton.draw(guiGraphics, mouseX, mouseY, partialTicks);

			RecipeFavoriteButton favoriteButton = recipeLayoutWithButtons.favoriteButton();
			favoriteButton.draw(guiGraphics, mouseX, mouseY, partialTicks);

			RecipeBookmarkButton bookmarkButton = recipeLayoutWithButtons.bookmarkButton();
			bookmarkButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
		}
		RenderSystem.disableBlend();
		return Optional.ofNullable(hoveredLayout);
	}

	public void drawTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		for (RecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			recipeLayoutWithButtons.transferButton().drawTooltips(guiGraphics, mouseX, mouseY);
			recipeLayoutWithButtons.favoriteButton().drawTooltips(guiGraphics, mouseX, mouseY);
			recipeLayoutWithButtons.bookmarkButton().drawTooltips(guiGraphics, mouseX, mouseY);
		}
	}

	public int getWidth() {
		if (recipeLayoutsWithButtons.isEmpty()) {
			return 0;
		}
		RecipeLayoutWithButtons<?> first = this.recipeLayoutsWithButtons.get(0);
		return first.totalWidth();
	}
}
