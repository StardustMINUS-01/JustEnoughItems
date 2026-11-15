package mezz.jei.gui.recipes;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.handlers.InputGroup;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.ReleaseInsideBounds;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.FocusedRecipeCandidate;
import mezz.jei.gui.input.IClickableIngredientInternal;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
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

public class RecipeGuiLayouts implements IInputTarget {
	private static final Logger LOGGER = LogManager.getLogger();

	private final RecipeSlotClickTargetFactory clickTargetFactory;
	private final List<IRecipeLayoutWithButtons<?>> recipeLayoutsWithButtons = new ArrayList<>();
	@Nullable
	private IInputTarget cachedInputHandler;
	private @Nullable ImmutableRect2i viewport;

	RecipeGuiLayouts(RecipeSlotClickTargetFactory clickTargetFactory) {
		this.clickTargetFactory = clickTargetFactory;
	}

	public void updateLayout(ImmutableRect2i recipeLayoutsArea, RecipeGuiGrid grid, @Nullable RecipeGuiScrollState scrollState) {
		this.viewport = null;
		if (scrollState != null) {
			this.viewport = recipeLayoutsArea;
		}
		if (this.recipeLayoutsWithButtons.isEmpty()) {
			return;
		}
		IRecipeLayoutWithButtons<?> firstLayout = this.recipeLayoutsWithButtons.getFirst();
		ImmutableRect2i layoutAreaWithBorder = new ImmutableRect2i(firstLayout.getRecipeLayout().getRectWithBorder());
		for (int i = 0; i < recipeLayoutsWithButtons.size(); i++) {
			IRecipeLayoutWithButtons<?> recipeLayoutWithButtons = recipeLayoutsWithButtons.get(i);
			ImmutableRect2i recipeArea = grid.getRecipeArea(i, recipeLayoutsArea, layoutAreaWithBorder.getSize(), recipeLayoutWithButtons.totalWidth());
			int y = recipeArea.y();
			if (scrollState != null) {
				y = recipeLayoutsArea.y() + scrollState.getRecipeY(i);
			}
			recipeLayoutWithButtons.updateBounds(recipeArea.x(), y);
		}
	}

	private IInputTarget getRecipeInputHandler() {
		if (cachedInputHandler == null) {
			List<IInputTarget> handlers = this.recipeLayoutsWithButtons.stream()
				.map(IRecipeLayoutWithButtons::createUserInputHandler)
				.toList();
			cachedInputHandler = new InputGroup("RecipeGuiLayouts", handlers);
		}
		return cachedInputHandler;
	}

	@Override
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (!isInsideViewport(input.getMouseX(), input.getMouseY())) {
			return Optional.empty();
		}
		return getRecipeInputHandler().beginInput(screen, input, keyBindings)
			.map(interaction -> new ReleaseInsideBounds(interaction, this::isInsideViewport));
	}

	/**
	 * Routes otherwise unhandled input to the hovered recipe's deprecated category callback.
	 * Mouse callbacks run only when released inside the original recipe and the visible viewport.
	 */
	public Optional<IInputInteraction> beginLegacyCategoryInput(UserInput input) {
		return getRecipeLayoutUnderMouse(input.getMouseX(), input.getMouseY())
			.flatMap(layout -> layout.beginLegacyCategoryInput(input))
			.map(interaction -> new ReleaseInsideBounds(interaction, this::isInsideViewport));
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isInsideViewport(mouseX, mouseY)) {
			return false;
		}
		return getRecipeInputHandler().scroll(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void resetInput() {
		getRecipeInputHandler().resetInput();
	}

	public boolean isInsideViewport(double mouseX, double mouseY) {
		return viewport == null || viewport.contains(mouseX, mouseY);
	}

	public void tick() {
		safeCallOnRecipeLayouts(IRecipeLayoutWithButtons::tick);
	}

	public void setRecipeLayoutsWithButtons(List<IRecipeLayoutWithButtons<?>> recipeLayoutsWithButtons) {
		this.recipeLayoutsWithButtons.clear();
		this.recipeLayoutsWithButtons.addAll(recipeLayoutsWithButtons);
		this.cachedInputHandler = null;
	}

	public Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> captureInputSelections() {
		Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> selections = new LinkedHashMap<>();
		for (IRecipeLayoutWithButtons<?> layout : recipeLayoutsWithButtons) {
			if (layout instanceof RecipeLayoutWithExtras<?> layoutWithButtons) {
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
			if (layout instanceof RecipeLayoutWithExtras<?> layoutWithButtons) {
				getFocusedRecipe(layout.getRecipeLayout())
					.map(selections::get)
					.ifPresent(layoutWithButtons::restoreInputSelections);
			}
		}
	}

	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		if (!isInsideViewport(mouseX, mouseY)) {
			return Stream.empty();
		}
		return this.recipeLayoutsWithButtons.stream()
			.map(IRecipeLayoutWithButtons::getRecipeLayout)
			.flatMap(recipeLayout -> clickTargetFactory.create(recipeLayout, mouseX, mouseY).stream());
	}

	public Optional<IRecipeLayoutWithButtons<?>> getRecipeLayoutUnderMouse(double mouseX, double mouseY) {
		if (!isInsideViewport(mouseX, mouseY)) {
			return Optional.empty();
		}
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
			if (recipeLayout.isMouseOver(mouseX, mouseY)) {
				return Optional.of(recipeLayoutWithButtons);
			}
		}
		return Optional.empty();
	}

	public Optional<RecipeLayoutUnderMouse> getRecipeLayoutWithSlotUnderMouse(double mouseX, double mouseY) {
		if (!isInsideViewport(mouseX, mouseY)) {
			return Optional.empty();
		}
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
		if (!isInsideViewport(mouseX, mouseY)) {
			return Optional.empty();
		}
		return this.recipeLayoutsWithButtons.stream()
			.map(IRecipeLayoutWithButtons::getRecipeLayout)
			.filter(recipeLayout -> recipeLayout.isMouseOver(mouseX, mouseY))
			.filter(recipeLayout -> isOutputSlotUnderMouse(recipeLayout, mouseX, mouseY))
			.findFirst()
			.flatMap(RecipeGuiLayouts::getFocusedRecipeCandidate);
	}

	public Optional<FocusedRecipeCandidate> getRecipeTooltipCandidateUnderMouse(double mouseX, double mouseY) {
		if (!isInsideViewport(mouseX, mouseY)) {
			return Optional.empty();
		}
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
		if (!isInsideViewport(mouseX, mouseY)) {
			return false;
		}
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
			if (recipeLayout.isMouseOver(mouseX, mouseY) && isOutputSlotUnderMouse(recipeLayout, mouseX, mouseY)) {
				if (recipeLayoutWithButtons instanceof RecipeLayoutWithExtras<?> recipeLayoutWithForkExtras) {
					return recipeLayout.getSlotUnderMouse(mouseX, mouseY)
						.flatMap(slot -> slot.slot().getDisplayedIngredient())
						.map(output -> recipeLayoutWithForkExtras.addRecipeBookmarkGroup(input, preserveAmount, output))
						.orElse(false);
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

	/** Offers movement to public recipe handlers under the pointer, in layout order. */
	public boolean handleApiMouseDragged(double mouseX, double mouseY, InputConstants.Key button, double dragX, double dragY) {
		if (!isInsideViewport(mouseX, mouseY)) {
			return false;
		}
		for (IRecipeLayoutWithButtons<?> layout : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipe = layout.getRecipeLayout();
			if (recipe.isMouseOver(mouseX, mouseY) && recipe.getInputHandler().handleMouseDragged(mouseX, mouseY, button, dragX, dragY)) {
				return true;
			}
		}
		return false;
	}

	public void mouseMoved(double mouseX, double mouseY) {
		if (!isInsideViewport(mouseX, mouseY)) {
			return;
		}
		for (IRecipeLayoutWithButtons<?> recipeLayoutWithButtons : recipeLayoutsWithButtons) {
			IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutWithButtons.getRecipeLayout();
			if (recipeLayout.isMouseOver(mouseX, mouseY)) {
				IJeiInputHandler inputHandler = recipeLayout.getInputHandler();
				inputHandler.handleMouseMoved(mouseX, mouseY);
			}
		}
	}

	public Optional<IRecipeLayoutDrawable<?>> draw(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (viewport == null) {
			return drawContents(guiGraphics, mouseX, mouseY);
		}
		guiGraphics.enableScissor(viewport.x(), viewport.y(), viewport.x() + viewport.width(), viewport.y() + viewport.height());
		try {
			if (!isInsideViewport(mouseX, mouseY)) {
				return drawContents(guiGraphics, Integer.MIN_VALUE, Integer.MIN_VALUE);
			}
			return drawContents(guiGraphics, mouseX, mouseY);
		} finally {
			guiGraphics.disableScissor();
		}
	}

	private Optional<IRecipeLayoutDrawable<?>> drawContents(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
		if (isInsideViewport(mouseX, mouseY)) {
			safeCallOnRecipeLayouts(r -> r.drawTooltips(guiGraphics, mouseX, mouseY));
		}
	}

	public int getWidth() {
		return recipeLayoutsWithButtons.stream()
			.mapToInt(IRecipeLayoutWithButtons::totalWidth)
			.max()
			.orElse(0);
	}
}
