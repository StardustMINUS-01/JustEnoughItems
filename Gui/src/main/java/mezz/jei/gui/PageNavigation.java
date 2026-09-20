package mezz.jei.gui;

import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiGuiColors;
import mezz.jei.common.gui.JeiGuiColors.GuiColor;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.handlers.NullInputHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import org.jetbrains.annotations.Nullable;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

public class PageNavigation {
	private final IPaged paged;
	private final GuiIconButton nextButton;
	private final GuiIconButton backButton;
	private final boolean hideOnSinglePage;
	private String pageNumDisplayString = "1/1";
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;
	private @Nullable IconButton extraButton;
	private IUserInputHandler extraInput = NullInputHandler.INSTANCE;

	public void setExtraButton(IconButton button) {
		this.extraButton = button;
		this.extraInput = button.createInputHandler();
		updateBounds(area);
	}

	public PageNavigation(IPaged paged, boolean hideOnSinglePage) {
		this.paged = paged;
		Textures textures = Internal.getTextures();
		this.nextButton = new GuiIconButton(textures.getArrowNext(), b -> paged.nextPage());
		this.backButton = new GuiIconButton(textures.getArrowPrevious(), b -> paged.previousPage());
		this.hideOnSinglePage = hideOnSinglePage;
	}

	private boolean isVisible() {
		if (area.isEmpty()) {
			return false;
		}
		return !hideOnSinglePage || this.paged.hasNext() || this.paged.hasPrevious();
	}

	public void updateBounds(ImmutableRect2i area) {
		this.area = area;
		int buttonSize = Math.min(area.getHeight(), area.width() / 2);

		ImmutableRect2i backArea = area.keepLeft(buttonSize);
		this.backButton.updateBounds(backArea);

		ImmutableRect2i nextArea = area.keepRight(buttonSize);
		this.nextButton.updateBounds(nextArea);
		if (extraButton != null) {
			extraButton.updateBounds(area.width() >= buttonSize * 3 + 4 ? nextArea.addOffset(-buttonSize - 2, 0) : ImmutableRect2i.EMPTY);
		}
	}

	public void updatePageNumber() {
		int pageNum = this.paged.getPageNumber();
		int pageCount = this.paged.getPageCount();
		this.pageNumDisplayString = String.format("%d/%d", pageNum + 1, pageCount);
		this.nextButton.active = pageCount > 1;
		this.backButton.active = pageCount > 1;
	}

	public void draw(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		if (isVisible()) {
			guiGraphics.fill(
				RenderType.gui(),
				backButton.getX() + backButton.getWidth(),
				backButton.getY(),
				nextButton.getX(),
				nextButton.getY() + nextButton.getHeight(),
				JeiGuiColors.getColor(GuiColor.PAGE_NAVIGATION_BACKGROUND)
			);

			int right = extraButton != null && !extraButton.getArea().isEmpty() ? extraButton.getX() : nextButton.getX();
			int left = backButton.getX() + backButton.getWidth();
			int availableWidth = Math.max(0, right - left - (extraButton == null ? 0 : 2));
			Font font = minecraft.font;
			ImmutableRect2i textArea = extraButton == null ? this.area : new ImmutableRect2i(left + 1, area.y(), availableWidth, area.height());
			ImmutableRect2i centerArea = MathUtil.centerTextArea(textArea, font, this.pageNumDisplayString);
			if (centerArea.width() <= availableWidth) {
				guiGraphics.drawString(font, pageNumDisplayString, centerArea.getX(), centerArea.getY(), JeiGuiColors.getColor(GuiColor.PAGE_NAVIGATION_TEXT));
			}
			nextButton.render(guiGraphics, mouseX, mouseY, partialTicks);
			backButton.render(guiGraphics, mouseX, mouseY, partialTicks);
			if (extraButton != null) {
				extraButton.tick();
				extraButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			}
		}
	}

	public void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
		if (isVisible() && extraButton != null) {
			extraButton.drawTooltips(graphics, mouseX, mouseY);
		}
	}

	public ImmutableRect2i getNextButtonArea() {
		return nextButton.getArea();
	}

	public ImmutableRect2i getBackButtonArea() {
		return backButton.getArea();
	}

	public IUserInputHandler createInputHandler() {
		return new CombinedInputHandler(
			"PageNavigation",
			new ProxyInputHandler(() -> isVisible() ? extraInput : NullInputHandler.INSTANCE),
			this.nextButton.createInputHandler(),
			this.backButton.createInputHandler()
		);
	}

}
