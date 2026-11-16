package mezz.jei.fabric.test.mixin;

import mezz.jei.fabric.test.FabricClientTestInput;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardClipboardMixin {
	@Inject(
		method = "setClipboard",
		at = @At("HEAD"),
		cancellable = true
	)
	private void jei$captureClipboard(String value, CallbackInfo callback) {
		if (FabricClientTestInput.captureClipboard(value)) {
			callback.cancel();
		}
	}
}
