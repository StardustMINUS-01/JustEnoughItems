package mezz.jei.neoforge.mixin;

import mezz.jei.common.gui.ITooltipStackAccessInternal;
import mezz.jei.gui.input.InteractiveTooltipController;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphics.class)
public abstract class InteractiveTooltipMixin implements ITooltipStackAccessInternal {
	@Override
	public void jei$renderPreparedTooltip(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner) {
		((GuiGraphics) (Object) this).renderTooltipInternal(font, components, x, y, positioner);
	}
	@Unique
	private final Deque<ScreenRectangle> jei$clips = new ArrayDeque<>();
	@Shadow(remap = false)
	private ItemStack tooltipStack;
	@Override
	public ItemStack jei$getTooltipStack() { return tooltipStack; }
	@Override
	public void jei$setTooltipStack(ItemStack stack) { tooltipStack = stack; }
	@Override
	public @Nullable ScreenRectangle jei$getTooltipClip() { return jei$clips.peekLast(); }

	@Inject(
		method = "enableScissor",
		at = @At("RETURN")
	)
	private void trackScissor(int left, int top, int right, int bottom, CallbackInfo callback) {
		ScreenRectangle next = new ScreenRectangle(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
		ScreenRectangle previous = jei$clips.peekLast();
		if (previous != null) {
			ScreenRectangle intersection = previous.intersection(next);
			next = intersection == null ? new ScreenRectangle(0, 0, 0, 0) : intersection;
		}
		jei$clips.addLast(next);
	}

	@Inject(
		method = "disableScissor",
		at = @At("RETURN")
	)
	private void trackScissorEnd(CallbackInfo callback) {
		jei$clips.removeLast();
	}

	@Inject(
		method = "renderTooltipInternal",
		at = @At("HEAD"),
		cancellable = true
	)
	private void prepareInteractiveTooltip(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, CallbackInfo callback) {
		if (InteractiveTooltipController.INSTANCE.intercept((GuiGraphics) (Object) this, font, components, x, y, positioner))
			callback.cancel();
	}
}
