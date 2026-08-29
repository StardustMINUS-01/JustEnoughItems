package mezz.jei.gui.recipes.navigation;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.gui.input.InputModifiers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class RecipeNavigationButtonController implements IIconButtonController {
	private static final int ACTIVE_ICON_COLOR = 0xFFFFFFFF;
	private static final int INACTIVE_ICON_COLOR = 0xFF555555;

	@FunctionalInterface
	public interface NavigationAction {
		boolean navigate(RecipeNavigationDirection direction, boolean jumpToEnd);
	}

	private final RecipeNavigationDirection direction;
	private final Predicate<RecipeNavigationDirection> canNavigate;
	private final Function<RecipeNavigationDirection, Optional<Component>> targetTitle;
	private final NavigationAction navigationAction;
	private final IDrawable activeIcon;
	private final IDrawable inactiveIcon;

	public RecipeNavigationButtonController(
		RecipeNavigationDirection direction,
		Predicate<RecipeNavigationDirection> canNavigate,
		Function<RecipeNavigationDirection, Optional<Component>> targetTitle,
		NavigationAction navigationAction
	) {
		this.direction = direction;
		this.canNavigate = canNavigate;
		this.targetTitle = targetTitle;
		this.navigationAction = navigationAction;
		this.activeIcon = new CurvedArrowDrawable(direction, ACTIVE_ICON_COLOR);
		this.inactiveIcon = new CurvedArrowDrawable(direction, INACTIVE_ICON_COLOR);
	}

	@Override
	public void updateState(IButtonState state) {
		boolean active = canNavigate.test(direction);
		state.setActive(active);
		state.setIcon(active ? activeIcon : inactiveIcon);
	}

	@Override
	public boolean onPress(IJeiUserInput input) {
		if (!input.isSimulate()) {
			boolean jumpToEnd = Screen.hasShiftDown() || InputModifiers.hasShift(input.getModifiers());
			navigationAction.navigate(direction, jumpToEnd);
		}
		return true;
	}

	@Override
	public void getTooltips(ITooltipBuilder tooltip) {
		String directionKey = direction.getTranslationKey();
		Optional<Component> target = targetTitle.apply(direction);
		if (target.isPresent()) {
			tooltip.add(Component.translatable("gui.jei.recipe_navigation." + directionKey, target.get()));
			tooltip.add(Component.translatable("gui.jei.recipe_navigation." + directionKey + ".shift"));
		} else {
			tooltip.add(Component.translatable("gui.jei.recipe_navigation." + directionKey + ".unavailable"));
		}
	}

	private record CurvedArrowDrawable(RecipeNavigationDirection direction, int color) implements IDrawable {
		private static final int SIZE = 9;

		@Override
		public int getWidth() {
			return SIZE;
		}

		@Override
		public int getHeight() {
			return SIZE;
		}

		@Override
		public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
			drawPixel(guiGraphics, xOffset, yOffset, 2, 1);
			drawHorizontal(guiGraphics, xOffset, yOffset, 3, 6, 1);
			drawPixel(guiGraphics, xOffset, yOffset, 1, 2);
			drawPixel(guiGraphics, xOffset, yOffset, 7, 2);
			drawHorizontal(guiGraphics, xOffset, yOffset, 0, 4, 3);
			drawPixel(guiGraphics, xOffset, yOffset, 7, 3);
			drawPixel(guiGraphics, xOffset, yOffset, 1, 4);
			drawPixel(guiGraphics, xOffset, yOffset, 7, 4);
			drawPixel(guiGraphics, xOffset, yOffset, 2, 5);
			drawPixel(guiGraphics, xOffset, yOffset, 6, 5);
			drawHorizontal(guiGraphics, xOffset, yOffset, 3, 5, 6);
		}

		private void drawHorizontal(GuiGraphics guiGraphics, int xOffset, int yOffset, int minX, int maxX, int y) {
			for (int x = minX; x <= maxX; x++) {
				drawPixel(guiGraphics, xOffset, yOffset, x, y);
			}
		}

		private void drawPixel(GuiGraphics guiGraphics, int xOffset, int yOffset, int x, int y) {
			int mirroredX = direction == RecipeNavigationDirection.BACK ? x : SIZE - 1 - x;
			guiGraphics.fill(xOffset + mirroredX, yOffset + y, xOffset + mirroredX + 1, yOffset + y + 1, color);
		}
	}
}
