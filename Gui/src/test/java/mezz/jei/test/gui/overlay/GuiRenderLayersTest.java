package mezz.jei.test.gui.overlay;

import mezz.jei.common.gui.GuiRenderLayers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GuiRenderLayersTest {
	private static final int ITEM_BATCH_RENDER_Z = 150;

	@Test
	public void overlayDecorationsRenderBelowTooltips() {
		assertTrue(GuiRenderLayers.OVERLAY_DECORATION_Z < GuiRenderLayers.TOOLTIP_Z);
	}

	@Test
	public void overlayDecorationsRenderAboveItemBatchModels() {
		assertTrue(GuiRenderLayers.OVERLAY_DECORATION_Z > ITEM_BATCH_RENDER_Z);
	}
}
