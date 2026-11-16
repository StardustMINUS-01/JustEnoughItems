package mezz.jei.fabric.mixin;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import mezz.jei.common.gui.ITooltipClipAccessInternal;
import mezz.jei.gui.input.InteractiveTooltipController;
import mezz.jei.fabric.events.JeiScreenEvents;
import mezz.jei.gui.chat.ChatIngredientTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin implements ITooltipClipAccessInternal {
	@Unique
	private Deque<ScreenRectangle> jei$clips;

	@Override
	public @Nullable ScreenRectangle jei$getTooltipClip() {
		return jei$clips.peekLast();
	}

	@Inject(
		method = "<init>",
		at = @At("RETURN")
	)
	private void initializeTooltipClipStack(CallbackInfo callback) {
		jei$clips = new ArrayDeque<>();
	}

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
	private void jei$renderTooltipInternal(
		Font font,
		List<ClientTooltipComponent> lines,
		int x,
		int y,
		ClientTooltipPositioner positioner,
		CallbackInfo ci
	) {
		@SuppressWarnings("DataFlowIssue")
		GuiGraphics guiGraphics = (GuiGraphics) (Object) this;
		if (!JeiScreenEvents.ALLOW_TOOLTIP.invoker().allow(guiGraphics) ||
			InteractiveTooltipController.INSTANCE.intercept(guiGraphics, font, lines, x, y, positioner, ItemStack.EMPTY)
		) {
			ci.cancel();
		}
	}

	@Inject(
		method = "renderComponentHoverEffect",
		at = @At("HEAD"),
		cancellable = true
	)
	private void jei$componentHoverEffect(Font font, Style hoveredStyle, int xMouse, int yMouse, CallbackInfo ci) {
		@SuppressWarnings("DataFlowIssue")
		GuiGraphics guiGraphics = (GuiGraphics) (Object) this;
		if (ChatIngredientTooltip.setTooltipForHoveredText(guiGraphics, hoveredStyle, xMouse, yMouse)) {
			ci.cancel();
		}
	}
}
