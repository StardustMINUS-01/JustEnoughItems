package mezz.jei.gui.recipes;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.FocusedRecipeCandidate;
import mezz.jei.gui.input.ClickableIngredientInternal;
import mezz.jei.gui.input.IClickableIngredientInternal;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RecipeGuiLayouts {
	private static final Logger LOGGER = LogManager.getLogger();

	private final List<IRecipeLayoutWithButtons<?>> recipeLayoutsWithButtons = new ArrayList<>();
	@Nullable
	private IUserInputHandler cachedInputHandler;

	public RecipeGuiLayouts() {
		this.cachedInputHandler = NullInputHandler.INSTANCE;
	}

	public void updateLayout(ImmutableRect2i recipeLayoutsArea, final int recipesPerPage) {
		if (this.recipeLayoutsWithButtons.isEmpty()) {
			return;
		}
		IRecipeLayoutWithButtons<?> firstLayout = this.recipeLayoutsWithButtons.getFirst();
		ImmutableRect2i layoutAreaWithBorder = new ImmutableRect2i(firstLayout.getRecipeLayout().getRectWithBorder());
		final int recipeXOffset = getRecipeXOffset(layoutAreaWithBorder, recipeLayoutsArea);

		final int recipeHeight = layoutAreaWithBorder.getHeight();
		final int availableHeight = Math.max(recipeLayoutsArea.getHeight(), recipeHeight);
		final int remainingHeight = availableHeight - (recipesPerPage * recipeHeight);
		final int recipeSpacing = remainingHeight / (recipesPerPage + 1);

		final int spacingY = recipeHeight + recipeSpacing;
		int recipeYOffset = recipeLayoutsArea.getY() + recipeSpacing;
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			recipeLayoutWithButtons.updateBounds(recipeXOffset, recipeYOffset);
			recipeYOffset += spacingY;
		}
	}

	private int getRecipeXOffset(ImmutableRect2i layoutRect, ImmutableRect2i layoutsArea) {
		if (recipeLayoutsWithButtons.isEmpty()) {
			return layoutsArea.getX();
		}

		final int recipeWidth = layoutRect.getWidth();
		final int recipeWidthWithButtons = recipeLayoutsWithButtons.getFirst().totalWidth();
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
					.map(IRecipeLayoutWithButtons::createUserInputHandler)
					.toList();
				cachedInputHandler = new CombinedInputHandler("RecipeGuiLayouts", handlers);
			}
			return cachedInputHandler;
		});
	}

	public void tick() {
		safeCallOnRecipeLayouts(IRecipeLayoutWithButtons::tick);
	}

	public void tick(@Nullable AbstractContainerMenu parentContainer) {
		Player player = Minecraft.getInstance().player;
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			if (recipeLayoutWithButtons instanceof RecipeLayoutWithButtons<?> recipeLayout) {
				recipeLayout.tick(parentContainer, player);
			} else {
				recipeLayoutWithButtons.tick();
			}
		}
	}

	public void setRecipeLayoutsWithButtons(List<IRecipeLayoutWithButtons<?>> recipeLayoutsWithButtons) {
		this.recipeLayoutsWithButtons.clear();
		this.recipeLayoutsWithButtons.addAll(recipeLayoutsWithButtons);
		this.cachedInputHandler = null;
	}

	public Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> captureInputSelections() {
		Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> selections = new LinkedHashMap<>();
		for (IRecipeLayoutWithButtons<?> layout : recipeLayoutsWithButtons) {
			if (layout instanceof RecipeLayoutWithButtons<?> layoutWithButtons) {
				Map<Integer, BookmarkIngredientKey> selectedInputs = layoutWithButtons.getInputSelections();
				if (!selectedInputs.isEmpty()) {
					getFocusedRecipe(layout.getRecipeLayout())
						.ifPresent(recipe -> selections.put(recipe, selectedInputs));
				}
			}
		}
		return Map.copyOf(selections);
	}

	public void restoreInputSelections(Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> selections) {
		for (IRecipeLayoutWithButtons<?> layout : recipeLayoutsWithButtons) {
			if (layout instanceof RecipeLayoutWithButtons<?> layoutWithButtons) {
				getFocusedRecipe(layout.getRecipeLayout())
					.map(selections::get)
					.ifPresent(layoutWithButtons::restoreInputSelections);
			}
		}
	}

	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		return this.recipeLayoutsWithButtons.stream()
			.map(IRecipeLayoutWithButtons::getRecipeLayout)
			.map(recipeLayout -> recipeLayout.getSlotUnderMouse(mouseX, mouseY))
			.flatMap(Optional::stream)
			.map(RecipeGuiLayouts::getClickedIngredient)
			.flatMap(Optional::stream);
	}

	public Optional<IRecipeLayoutWithButtons<?>> getRecipeLayoutUnderMouse(double mouseX, double mouseY) {
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
			if (recipeLayout.isMouseOver(mouseX, mouseY)) {
				return Optional.of(recipeLayoutWithButtons);
			}
		}
		return Optional.empty();
	}

	public Optional<RecipeLayoutUnderMouse> getRecipeLayoutWithSlotUnderMouse(double mouseX, double mouseY) {
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
			if (!recipeLayout.isMouseOver(mouseX, mouseY)) {
				continue;
			}
			Optional<RecipeSlotUnderMouse> slotUnderMouse = recipeLayout.getSlotUnderMouse(mouseX, mouseY)
				.filter(slot -> slot.slot().getDisplayedIngredient().isPresent());
			if (slotUnderMouse.isPresent()) {
				return Optional.of(new RecipeLayoutUnderMouse(recipeLayout, slotUnderMouse.get()));
			}
		}
		return Optional.empty();
	}

	public Optional<FocusedRecipeCandidate> getFocusedRecipeCandidateUnderMouse(double mouseX, double mouseY) {
		return this.recipeLayoutsWithButtons.stream()
			.map(IRecipeLayoutWithButtons::getRecipeLayout)
			.filter(recipeLayout -> recipeLayout.isMouseOver(mouseX, mouseY))
			.filter(recipeLayout -> isOutputSlotUnderMouse(recipeLayout, mouseX, mouseY))
			.findFirst()
			.flatMap(RecipeGuiLayouts::getFocusedRecipeCandidate);
	}

	public Optional<FocusedRecipeCandidate> getRecipeTooltipCandidateUnderMouse(double mouseX, double mouseY) {
		return this.recipeLayoutsWithButtons.stream()
			.map(IRecipeLayoutWithButtons::getRecipeLayout)
			.filter(recipeLayout -> recipeLayout.isMouseOver(mouseX, mouseY))
			.filter(recipeLayout -> !isOutputSlotUnderMouse(recipeLayout, mouseX, mouseY))
			.filter(recipeLayout -> hasDisplayedSlotUnderMouse(recipeLayout, mouseX, mouseY))
			.findFirst()
			.flatMap(RecipeGuiLayouts::getFocusedRecipeCandidate);
	}

	public boolean bookmarkRecipeUnderMouse(UserInput input, boolean preserveAmount) {
		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
			if (recipeLayout.isMouseOver(mouseX, mouseY) && isOutputSlotUnderMouse(recipeLayout, mouseX, mouseY)) {
				if (recipeLayoutWithButtons instanceof RecipeLayoutWithButtons<?> recipeLayoutWithForkExtras) {
					return recipeLayoutWithForkExtras.addRecipeBookmarkGroup(input, preserveAmount);
				}
				return false;
			}
		}
		return false;
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

	private static <R> Optional<FocusedRecipeCandidate> getFocusedRecipeCandidate(IRecipeLayoutDrawable<R> recipeLayout) {
		return getFocusedRecipe(recipeLayout)
			.map(FocusedRecipeCandidate::recipe);
	}

	private static <R> Optional<FocusedRecipe> getFocusedRecipe(IRecipeLayoutDrawable<R> recipeLayout) {
		R recipe = recipeLayout.getRecipe();
		ResourceLocation recipeUid = recipeLayout.getRecipeCategory().getRegistryName(recipe);
		if (recipeUid == null) {
			return Optional.empty();
		}
		ResourceLocation recipeTypeUid = recipeLayout.getRecipeCategory().getRecipeType().getUid();
		return Optional.of(new FocusedRecipe(recipeTypeUid, recipeUid));
	}

	public record RecipeLayoutUnderMouse(IRecipeLayoutDrawable<?> layout, RecipeSlotUnderMouse slotUnderMouse) {
	}

	private static Optional<IClickableIngredientInternal<?>> getClickedIngredient(RecipeSlotUnderMouse slotUnderMouse) {
		return slotUnderMouse.slot().getDisplayedIngredient()
			.map(displayedIngredient -> {
				IElement<?> element = new IngredientElement<>(displayedIngredient);
				return new ClickableIngredientInternal<>(element, slotUnderMouse::isMouseOver, false, true);
			});
	}

	public boolean mouseDragged(double mouseX, double mouseY, InputConstants.Key input, double dragX, double dragY) {
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
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
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
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

		safeCallOnRecipeLayouts(r -> r.draw(guiGraphics, mouseX, mouseY, partialTicks));

		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
			if (recipeLayout.isMouseOver(mouseX, mouseY)) {
				hoveredLayout = recipeLayout;
				break;
			}
		}
		RenderSystem.disableBlend();
		return Optional.ofNullable(hoveredLayout);
	}

	private void safeCallOnRecipeLayouts(Consumer<IRecipeLayoutWithButtons<?>> consumer) {
		for (int i = 0; i < recipeLayoutsWithButtons.size(); i++) {
			IRecipeLayoutWithButtons<?> recipeLayoutWithButtons = recipeLayoutsWithButtons.get(i);
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
			try {
				consumer.accept(recipeLayoutWithButtons);
			} catch (RuntimeException e) {
				String recipeInfo = ErrorUtil.getRecipeInfo(recipeLayout);
				LOGGER.error("Recipe crashed:\n{}", recipeInfo, e);
				recipeLayoutsWithButtons.set(i, new RecipeLayoutWithButtonsErrored<>(recipeLayout));
			}
		}
	}

	public void drawTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		safeCallOnRecipeLayouts(r -> r.drawTooltips(guiGraphics, mouseX, mouseY));
	}

	public int getWidth() {
		if (recipeLayoutsWithButtons.isEmpty()) {
			return 0;
		}
		IRecipeLayoutWithButtons<?> first = this.recipeLayoutsWithButtons.getFirst();
		return first.totalWidth();
	}
}
