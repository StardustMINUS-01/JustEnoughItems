package mezz.jei.gui.config.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

final class ConfigScreenButton extends Button {
	private static final ResourceLocation HOVER = texture("button_hover");
	private static final ResourceLocation PRESSED = texture("button_pressed");
	private final String style;
	private final ResourceLocation background;
	private final @Nullable ResourceLocation icon;
	private boolean pressed;

	ConfigScreenButton(int x, int y, int width, Component label, String style, @Nullable String icon, OnPress action) {
		super(x, y, width, 26, label, action, DEFAULT_NARRATION);
		this.style = style;
		this.background = texture(style);
		this.icon = icon == null ? null : texture(icon);
	}

	private static ResourceLocation texture(String name) {
		return ResourceLocation.fromNamespaceAndPath("jei", "config/" + name);
	}

	@Override
	public void onClick(double x, double y) { pressed = true; }

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
		if (style.startsWith("switch")) {
			graphics.blitSprite(background, getX(), getY() + 3, width, 18);
			if (isHoveredOrFocused()) {
				graphics.renderOutline(getX() - 1, getY() + 2, width + 2, 20, 0xFFF0F0F0);
			}
			return;
		}
		boolean navigation = style.startsWith("tab");
		boolean depressed = pressed || !active;
		ResourceLocation sprite = depressed ? PRESSED :
			style.equals("button") && isHoveredOrFocused() ? HOVER : background;
		if (navigation) {
			if (style.equals("tab_selected")) {
				graphics.blitSprite(background, getX(), getY(), width, height);
			} else if (isHoveredOrFocused()) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF353535);
			}
		} else {
			graphics.blitSprite(sprite, getX(), getY(), width, height);
		}
		if (icon != null) {
			float tint = active ? 0.125F : 0.4F;
			graphics.setColor(tint, tint, tint, 1.0F);
			graphics.blitSprite(icon, getX() + (width - 16) / 2, getY() + 4 + (depressed ? 2 : 0), 16, 16);
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
			graphics.renderOutline(getX(), getY(), width, height, 0xFFF0F0F0);
		}
	}
}
