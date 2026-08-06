package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.ScalableDrawable;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IUserInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class ScrollStepTextField extends EditBox {
	private static final int MAX_LENGTH = 10;
	private final ScrollStep scrollStep;
	private final ScalableDrawable background;
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;
	private ImmutableRect2i backgroundBounds = ImmutableRect2i.EMPTY;

	public ScrollStepTextField(ScrollStep scrollStep) {
		super(Minecraft.getInstance().font, 0, 0, 0, 0, Component.translatable("jei.bookmark.scrollStep"));
		this.scrollStep = scrollStep;
		setMaxLength(MAX_LENGTH);
		setBordered(false);
		setFilter(text -> text.chars().allMatch(Character::isDigit));
		setResponder(this::onTextChanged);
		Textures textures = Internal.getTextures();
		this.background = textures.getSearchBackground();
		syncFromScrollStep();
	}

	public void updateBounds(ImmutableRect2i area) {
		this.backgroundBounds = area;
		setX(area.getX() + 4);
		setY(area.getY() + (area.getHeight() - 8) / 2);
		this.width = Math.max(0, area.getWidth() - 12);
		this.height = area.getHeight();
		this.area = area;
	}

	public void syncFromScrollStep() {
		if (scrollStep.getValue() == 0) {
			if (!getValue().isEmpty()) {
				setValue("");
			}
		} else {
			String text = Long.toString(scrollStep.getValue());
			if (!text.equals(getValue())) {
				setValue(text);
			}
		}
		updateColor();
	}

	public IUserInputHandler createInputHandler() {
		return new ScrollStepFieldInputHandler(this);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return area.contains(mouseX, mouseY);
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		if (isVisible()) {
			RenderSystem.setShaderColor(1, 1, 1, 1);
			background.draw(guiGraphics, backgroundBounds);
		}
		if (getValue().isEmpty() && !isFocused()) {
			Component placeholder = Component.translatable("jei.bookmark.scrollStep.oneStack");
			guiGraphics.drawString(Minecraft.getInstance().font, placeholder, getX(), getY(), getPlaceholderColor(), true);
			return;
		}
		super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
	}

	static long parse(String text) {
		if (text.isEmpty()) {
			return 0;
		}
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void onTextChanged(String text) {
		scrollStep.setValue(parse(text));
		updateColor();
	}

	private void updateColor() {
		if (Internal.getClientToggleState().isFastPickupEnabled()) {
			setTextColor(0xFFB22222);
		} else if (scrollStep.getValue() == 0) {
			setTextColor(0xFF666666);
		} else {
			setTextColor(0xFFFFFFFF);
		}
	}

	private int getPlaceholderColor() {
		return Internal.getClientToggleState().isFastPickupEnabled() ? 0xFFB22222 : 0xFF666666;
	}
}
