package mezz.jei.gui.input.handlers;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.JeiClientSoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** 1.20.1 tag picker. 1.21 uses a pinned tooltip overlay; this screen is the Forge-safe equivalent. */
public final class IngredientTagSelectionScreen extends Screen {
	private static final int PADDING = 8;
	private static final int LINE_HEIGHT = 12;
	private final Screen parent;
	private final String titleText;
	private final List<String> tags;
	private int firstRow;
	private int visibleRows;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	public static <T> void open(ITypedIngredient<T> ingredient, IIngredientManager ingredientManager) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen parent = minecraft.screen;
		if (parent == null) {
			return;
		}
		String name = IngredientClipboardText.getIngredientName(ingredient, ingredientManager);
		List<String> tags = IngredientClipboardText.getIngredientTags(ingredient, ingredientManager);
		minecraft.setScreen(new IngredientTagSelectionScreen(parent, name, tags));
	}

	private IngredientTagSelectionScreen(Screen parent, String titleText, List<String> tags) {
		super(Component.translatable("key.jei.copyIngredientTags"));
		this.parent = parent;
		this.titleText = titleText;
		this.tags = tags;
	}

	@Override
	protected void init() {
		int maxWidth = Math.min(360, width - 2 * PADDING);
		int contentWidth = Math.max(font.width(titleText), tags.stream().mapToInt(font::width).max().orElse(font.width(Component.translatable("jei.tooltip.tags.empty"))));
		panelW = Math.min(maxWidth, contentWidth + 2 * PADDING + 8);
		visibleRows = Math.min(Math.max(tags.size(), 1), Math.max(1, (height - 80) / LINE_HEIGHT));
		panelH = 24 + visibleRows * LINE_HEIGHT + 2 * PADDING;
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		firstRow = 0;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101010);
		graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFFFFFFFF);
		graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFFFFFFFF);
		graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFFFFFFFF);
		graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFFFFFFFF);
		graphics.drawString(font, font.plainSubstrByWidth(titleText, panelW - 2 * PADDING), panelX + PADDING, panelY + PADDING, 0xFFFFFF);
		int y = panelY + PADDING + 16;
		int hovered = hoveredIndex(mouseX, mouseY);
		if (tags.isEmpty()) {
			graphics.drawString(font, Component.translatable("jei.tooltip.tags.empty"), panelX + PADDING, y, 0xAAAAAA);
		} else {
			int last = Math.min(tags.size(), firstRow + visibleRows);
			for (int i = firstRow; i < last; i++) {
				if (i == hovered) {
					graphics.fill(panelX + PADDING - 2, y - 1, panelX + panelW - PADDING + 2, y + LINE_HEIGHT - 1, 0x33FFFFFF);
				}
				graphics.drawString(font, font.plainSubstrByWidth(tags.get(i), panelW - 2 * PADDING - 6), panelX + PADDING, y, 0xFFFFFF);
				y += LINE_HEIGHT;
			}
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private int hoveredIndex(double mouseX, double mouseY) {
		if (tags.isEmpty()) {
			return -1;
		}
		int top = panelY + PADDING + 16;
		if (mouseX < panelX + PADDING || mouseX >= panelX + panelW - PADDING || mouseY < top || mouseY >= top + visibleRows * LINE_HEIGHT) {
			return -1;
		}
		int index = firstRow + (int) ((mouseY - top) / LINE_HEIGHT);
		return index >= 0 && index < tags.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int index = hoveredIndex(mouseX, mouseY);
		if (index >= 0) {
			Minecraft.getInstance().keyboardHandler.setClipboard(tags.get(index));
			JeiClientSoundUtil.playClickSound();
			onClose();
			return true;
		}
		if (mouseX < panelX || mouseX >= panelX + panelW || mouseY < panelY || mouseY >= panelY + panelH) {
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (tags.size() > visibleRows) {
			firstRow = Mth.clamp(firstRow - (int) Math.signum(delta), 0, tags.size() - visibleRows);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
