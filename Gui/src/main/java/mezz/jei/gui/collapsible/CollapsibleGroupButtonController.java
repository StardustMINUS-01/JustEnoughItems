package mezz.jei.gui.collapsible;

import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

public final class CollapsibleGroupButtonController implements IIconButtonController {
	private final CollapsibleManager manager;

	public CollapsibleGroupButtonController(CollapsibleManager manager) {
		this.manager = manager;
	}

	@Override
	public boolean onPress(IJeiUserInput input) {
		if (!input.isSimulate()) {
			manager.toggleAll(input.getKey().getValue() == 1 ? false : null);
		}
		return true;
	}

	@Override
	public void updateState(IButtonState state) {
		state.setActive(!manager.rules().groups().isEmpty());
	}

	@Override
	public void drawExtras(GuiGraphics graphics, Rect2i area, int mouseX, int mouseY, float partialTicks) {
		var font = Minecraft.getInstance().font;
		graphics.drawCenteredString(font, Component.translatable("jei.collapsible.button"), area.getX() + area.getWidth() / 2,
			area.getY() + (area.getHeight() - font.lineHeight) / 2, manager.rules().groups().isEmpty() ? 0xFFA0A0A0 : 0xFFFFFFFF);
	}

	@Override
	public void getTooltips(ITooltipBuilder tooltip) {
		tooltip.add(Component.translatable("jei.collapsible.button.tooltip"));
	}
}
