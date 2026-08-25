package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

public final class RecipeFilterModeButtonController implements IIconButtonController {
	private static final int ACTIVE_COLOR = 0xFFFFFFFF;
	private static final int INACTIVE_COLOR = 0xFF555555;

	private final RecipeFilterSettings settings;
	private final Runnable applyFilter;

	public RecipeFilterModeButtonController(RecipeFilterSettings settings, Runnable applyFilter) {
		this.settings = settings;
		this.applyFilter = applyFilter;
	}

	@Override
	public boolean onPress(IJeiUserInput input) {
		if (!input.isSimulate()) {
			settings.cycleMode();
			applyFilter.run();
		}
		return true;
	}

	@Override
	public void updateState(IButtonState state) {
		state.setActive(true);
		state.setForcePressed(settings.getMode() != RecipeFilterMode.ALL);
	}

	@Override
	public void getTooltips(ITooltipBuilder tooltip) {
		String modeKey = switch (settings.getMode()) {
			case ALL -> "gui.jei.recipe_filter.mode.all";
			case PREFERRED -> "gui.jei.recipe_filter.mode.preferred";
			case NOT_PREFERRED -> "gui.jei.recipe_filter.mode.not_preferred";
		};
		tooltip.add(Component.translatable("gui.jei.recipe_filter.mode", Component.translatable(modeKey)));
	}

	@Override
	public void drawExtras(GuiGraphics guiGraphics, Rect2i buttonArea, int mouseX, int mouseY, float partialTicks) {
		int left = buttonArea.getX() + 5;
		int right = buttonArea.getX() + buttonArea.getWidth() - 5;
		int top = buttonArea.getY() + 6;
		for (int line = 0; line < 3; line++) {
			int y = top + (line * 3);
			guiGraphics.fill(RenderType.gui(), left, y, right, y + 1, getLineColor(line));
		}
	}

	private int getLineColor(int line) {
		return switch (settings.getMode()) {
			case ALL -> ACTIVE_COLOR;
			case PREFERRED -> line == 0 ? ACTIVE_COLOR : INACTIVE_COLOR;
			case NOT_PREFERRED -> line == 0 ? INACTIVE_COLOR : ACTIVE_COLOR;
		};
	}
}
