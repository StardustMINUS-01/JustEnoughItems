package mezz.jei.common.gui;

import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import java.util.List;

/** NeoForge-only access for replaying prepared tooltip components with the original event stack. */
public interface ITooltipStackAccessInternal {
	ItemStack jei$getTooltipStack();
	void jei$setTooltipStack(ItemStack stack);
	@Nullable
	ScreenRectangle jei$getTooltipClip();
	void jei$renderPreparedTooltip(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner);
}
