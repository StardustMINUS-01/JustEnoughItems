package mezz.jei.gui.config.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class ConfigScreenEditBox extends EditBox {
	private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("jei", "config/input");

	ConfigScreenEditBox(Font font, int x, int y, int width, Component label) {
		super(font, x, y, width, 20, label);
	}

	@Override
	public boolean isBordered() {
		// Suppress only the native skin; EditBox's bordered field retains text and click padding.
		return false;
	}

	@Override
	public int getInnerWidth() {
		return getWidth() - 8;
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (isVisible()) {
			graphics.blitSprite(BACKGROUND, getX(), getY(), getWidth(), getHeight());
			if (isFocused()) {
				graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF8BC56A);
			}
			super.renderWidget(graphics, mouseX, mouseY, partialTick);
		}
	}
}
