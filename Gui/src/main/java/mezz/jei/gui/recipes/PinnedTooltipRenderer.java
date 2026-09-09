package mezz.jei.gui.recipes;

import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.GuiRenderLayers;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.gui.GuiGraphics;

public final class PinnedTooltipRenderer {
	public static final int TOOLTIP_FOREGROUND_Z = GuiRenderLayers.TOOLTIP_Z;
	public static final int NESTED_TOOLTIP_FOREGROUND_Z = TOOLTIP_FOREGROUND_Z + GuiRenderLayers.OVERLAY_DECORATION_Z + 100;
	private static final int BACKGROUND_PADDING = 2;
	private static final int SCREEN_DIM = 0x40000000;

	private final RecipeSlotTooltipPositioner positioner = new RecipeSlotTooltipPositioner();
	private final IScalableDrawable background = Internal.getTextures().getRecipePreviewBackground();
	private final int anchorX;
	private final int anchorY;

	public PinnedTooltipRenderer(int anchorX, int anchorY) {
		this.anchorX = anchorX;
		this.anchorY = anchorY;
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return getArea().contains(mouseX, mouseY);
	}

	public void draw(GuiGraphics guiGraphics, JeiTooltip tooltip) {
		ImmutableRect2i area = getArea();
		var poseStack = guiGraphics.pose();
		poseStack.pushPose();
		try {
			poseStack.translate(0, 0, TOOLTIP_FOREGROUND_Z);
			guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), SCREEN_DIM);
			if (!area.isEmpty()) {
				this.background.draw(guiGraphics, area.x(), area.y(), area.width(), area.height());
			}
			tooltip.draw(guiGraphics, this.anchorX, this.anchorY, this.positioner);
		} finally {
			poseStack.popPose();
		}
	}

	private ImmutableRect2i getArea() {
		ImmutableRect2i tooltipArea = this.positioner.getTooltipArea();
		if (tooltipArea.isEmpty()) {
			return ImmutableRect2i.EMPTY;
		}
		return tooltipArea.expandBy(BACKGROUND_PADDING);
	}
}
