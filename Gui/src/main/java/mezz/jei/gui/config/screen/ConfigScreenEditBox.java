package mezz.jei.gui.config.screen;

import mezz.jei.common.Internal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class ConfigScreenEditBox extends EditBox {
	ConfigScreenEditBox(Font font, int x, int y, int width, Component label) {
		super(font, x, y, width, 20, label);
		setBordered(false);
	}

	@Override
	public int getInnerWidth() {
		return getWidth() - 8;
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (isVisible()) {
			Internal.getTextures().getConfigScreenNineSlice("input").draw(graphics, getX(), getY(), getWidth(), getHeight());
			if (isFocused()) {
				ConfigScreenButton.drawOutline(graphics, getX(), getY(), getWidth(), getHeight(), 0xFF8BC56A);
			}
			super.renderWidget(graphics, mouseX, mouseY, partialTick);
		}
	}
}
