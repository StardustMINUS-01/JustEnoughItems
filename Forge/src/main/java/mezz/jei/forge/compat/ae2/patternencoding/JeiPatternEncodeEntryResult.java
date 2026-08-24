package mezz.jei.forge.compat.ae2.patternencoding;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record JeiPatternEncodeEntryResult(
	ResourceLocation recipeUid,
	JeiPatternEncodeEntryStatus status,
	@Nullable Component message
) {
}
