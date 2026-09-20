package mezz.jei.gui.config.screen;

import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

final class ConfigScreenButton extends Button {
	private final String style;
	private final @Nullable IDrawableStatic icon;
	private boolean pressed;

	ConfigScreenButton(int x, int y, int width, Component label, String style, @Nullable String icon, OnPress action) {
		super(x, y, width, 26, label, action, DEFAULT_NARRATION);
		this.style = style;
		this.icon = icon == null ? null : Internal.getTextures().getConfigScreenIcon(icon);
	}

	@Override
	public void onClick(double x, double y) {
		pressed = true;
	}

	@Override
	public void onRelease(double x, double y) {
		// Navigate only after release, so the parent's overlay cannot receive this click's release.
		if (pressed) {
			pressed = false;
			if (clicked(x, y)) {
				onPress();
			}
		}
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Textures textures = Internal.getTextures();
		boolean hovered = isHoveredOrFocused();
		if (style.startsWith("switch")) {
			IDrawableStatic background = textures.getConfigScreenSprite(style);
			background.draw(graphics, getX(), getY() + 3);
			if (hovered) {
				drawOutline(graphics, getX() - 1, getY() + 2, width + 2, 20, 0xFFF0F0F0);
			}
			return;
		}
		boolean navigation = style.startsWith("tab");
		boolean depressed = pressed || !active;
		if (navigation) {
			if (style.equals("tab_selected")) {
				textures.getConfigScreenNineSlice("tab_selected").draw(graphics, getX(), getY(), width, height);
			} else if (hovered) {
				graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF353535);
			}
		} else {
			DrawableNineSliceTexture sprite = textures.getConfigScreenNineSlice(
				depressed ? "button_pressed" : style.equals("button") && hovered ? "button_hover" : style
			);
			sprite.draw(graphics, getX(), getY(), width, height);
		}
		if (icon != null) {
			float tint = active ? 0.125F : 0.4F;
			graphics.setColor(tint, tint, tint, 1.0F);
			icon.draw(graphics, getX() + (width - 16) / 2, getY() + 4 + (depressed ? 2 : 0));
			graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		} else {
			var font = Minecraft.getInstance().font;
			String label = font.plainSubstrByWidth(getMessage().getString(), width - 12);
			int textX = navigation ? getX() + 8 : getX() + (width - font.width(label)) / 2;
			var textColor = getMessage().getStyle().getColor();
			graphics.drawString(font, label, textX, getY() + 6 + (depressed && !navigation ? 2 : 0),
				!active ? 0xFF666666 : textColor != null ? 0xFF000000 | textColor.getValue() : navigation ? 0xFFF0F0F0 : 0xFF202020, false);
		}
		if (isFocused()) {
			drawOutline(graphics, getX(), getY(), width, height, 0xFFF0F0F0);
		}
	}

	static void drawOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}
}
