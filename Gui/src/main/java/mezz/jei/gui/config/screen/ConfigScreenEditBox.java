package mezz.jei.gui.config.screen;

import mezz.jei.common.Internal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class ConfigScreenEditBox extends EditBox {
	// 1.21.1's ConfigScreenEditBox suppresses the MC 1.21.x sprite border by overriding
	// isBordered() (which is public there). 1.20.1 vanilla EditBox.isBordered() is private,
	// so the port cannot override it; we mirror 1.21.1's structure (draw nine-slice first,
	// then call super.renderWidget) and accept that super will draw the MC 1.20.1 native
	// fill rectangle (which overwrites the nine-slice center) on top of the nine-slice.
	// The 1.20.1 vanilla native fill rectangle + bordered=true text padding + caret is
	// exactly what 1.21.1's sprite border + bordered=true text + caret renders visually.
	private static final int FOCUS_OUTLINE_COLOR = 0xFF8BC56A;

	ConfigScreenEditBox(Font font, int x, int y, int width, Component label) {
		super(font, x, y, width, 20, label);
		// No setBordered(false): keep bordered=true so super.renderWidget uses the
		// bordered=true text padding (X+4, Y+(height-8)/2) and the correct caret position.
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
				ConfigScreenButton.drawOutline(graphics, getX(), getY(), getWidth(), getHeight(), FOCUS_OUTLINE_COLOR);
			}
			super.renderWidget(graphics, mouseX, mouseY, partialTick);
		}
	}
}
