package mezz.jei.neoforge.compat.ae2.mixin;

import mezz.jei.neoforge.compat.CompatUtil;
import org.objectweb.asm.tree.ClassNode;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Only applies the AE2 mixin when AE2 is installed, so the game works without it.
 */
public class Ae2MixinConfigPlugin implements IMixinConfigPlugin {
	private static final boolean AE2_PRESENT = isAe2Present();

	public static boolean isAe2Present() {
		return CompatUtil.isClassPresent("appeng.menu.me.items.CraftingTermMenu");
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public @Nullable String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return AE2_PRESENT;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public @Nullable List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
