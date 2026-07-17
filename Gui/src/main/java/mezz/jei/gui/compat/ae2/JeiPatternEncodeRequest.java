package mezz.jei.gui.compat.ae2;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record JeiPatternEncodeRequest(
	ResourceLocation recipeTypeUid,
	ResourceLocation recipeUid,
	JeiPatternEncodeMode mode,
	List<@Nullable JeiPatternStack> sparseInputs,
	List<@Nullable JeiPatternStack> sparseOutputs,
	List<JeiPatternCatalyst> catalysts,
	List<@Nullable JeiPatternStack> canonicalInputGuides,
	@Nullable ResourceLocation canonicalRecipeId,
	boolean allowSubstitution,
	boolean allowFluidSubstitution
) {
	public JeiPatternEncodeRequest {
		sparseInputs = Collections.unmodifiableList(new ArrayList<>(sparseInputs));
		sparseOutputs = Collections.unmodifiableList(new ArrayList<>(sparseOutputs));
		catalysts = Collections.unmodifiableList(new ArrayList<>(catalysts));
		canonicalInputGuides = Collections.unmodifiableList(new ArrayList<>(canonicalInputGuides));
	}
}
