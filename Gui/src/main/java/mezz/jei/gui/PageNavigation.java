package mezz.jei.gui;

import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiGuiColors;
import mezz.jei.common.gui.JeiGuiColors.GuiColor;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.NullInputHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

public class PageNavigation {
	private final IPaged paged;
	private final IconButton nextButton;
	private final IconButton backButton;
	private final boolean hideOnSinglePage;
	private String pageNumDisplayString = "1/1";
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;
	private @Nullable IconButton extraButton;
	private IUserInputHandler extraInput = NullInputHandler.INSTANCE;
	private boolean leadingContent;
	private ImmutableRect2i leadingArea = ImmutableRect2i.EMPTY;

	public void setLeadingContent(boolean enabled) {
		leadingContent = enabled;
	}

	public ImmutableRect2i getLeadingArea() {
		return isVisible() ? leadingArea : ImmutableRect2i.EMPTY;
	}

	public void setExtraButton(IconButton button) {
		this.extraButton = button;
		this.extraInput = button.createInputHandler();
		updateBounds(area);
	}

	public PageNavigation(IPaged paged, boolean hideOnSinglePage) {
		this.paged = paged;
		this.nextButton = new IconButton(new IIconButtonController() {
			@Override
			public boolean onPress(IJeiUserInput b) {
				return b.isSimulate() || paged.nextPage();
			}

			@Override
			public void initState(IButtonState state) {
				state.setIcon(Internal.getTextures().getArrowNext());
				updateState(state);
			}

			@Override
			public void updateState(IButtonState state) {
				state.setActive(paged.getPageCount() > 1);
			}
		});
		this.backButton = new IconButton(new IIconButtonController() {
			@Override
			public boolean onPress(IJeiUserInput b) {
				return b.isSimulate() || paged.previousPage();
			}

			@Override
			public void initState(IButtonState state) {
				state.setIcon(Internal.getTextures().getArrowPrevious());
				updateState(state);
			}

			@Override
			public void updateState(IButtonState state) {
				state.setActive(paged.getPageCount() > 1);
			}
		});
		this.hideOnSinglePage = hideOnSinglePage;
	}

	private boolean isVisible() {
		if (area.isEmpty()) {
			return false;
		}
		return leadingContent || !hideOnSinglePage || this.paged.hasNext() || this.paged.hasPrevious();
	}

	public void updateBounds(ImmutableRect2i area) {
		this.area = area;
		int buttonSize = Math.min(area.getHeight(), leadingContent ? Math.max(0, (area.width() - 4) / (extraButton == null ? 4 : 5)) : area.width() / 2);
		leadingArea = leadingContent && buttonSize > 0 ? new ImmutableRect2i(area.x() + buttonSize + 2, area.y(), buttonSize * 2, area.height()) : ImmutableRect2i.EMPTY;

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

		this.nextButton.tick();
		this.backButton.tick();
		if (extraButton != null) {
			extraButton.tick();
		}
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

			int right = nextButton.getX();
			int left = backButton.getX() + backButton.getWidth();
			int availableWidth = Math.max(0, right - left);
			Font font = minecraft.font;
			ImmutableRect2i textArea = new ImmutableRect2i(left, area.y(), availableWidth, area.height());
			ImmutableRect2i centerArea = MathUtil.centerTextArea(textArea, font, this.pageNumDisplayString);
			if (centerArea.width() <= availableWidth && !centerArea.intersects(leadingArea) &&
				(extraButton == null || !centerArea.intersects(extraButton.getArea()))
			) {
				guiGraphics.drawString(font, pageNumDisplayString, centerArea.getX(), centerArea.getY(), JeiGuiColors.getColor(GuiColor.PAGE_NAVIGATION_TEXT));
			}
			nextButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			backButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			if (extraButton != null) {
				extraButton.tick();
				extraButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
			}
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

	public void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
		if (isVisible() && extraButton != null) {
			extraButton.drawTooltips(graphics, mouseX, mouseY);
		}
	}

}
