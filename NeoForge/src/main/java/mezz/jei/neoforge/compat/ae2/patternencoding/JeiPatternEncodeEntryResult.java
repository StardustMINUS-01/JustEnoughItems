package mezz.jei.neoforge.compat.ae2.patternencoding;

import net.minecraft.resources.ResourceLocation;

public record JeiPatternEncodeEntryResult(
	ResourceLocation recipeTypeUid,
	ResourceLocation recipeUid,
	JeiPatternEncodeEntryStatus status
) {
}
