package mezz.jei.gui.input;

import net.minecraft.resources.ResourceLocation;

public record FocusedRecipe(
	ResourceLocation recipeTypeUid,
	ResourceLocation recipeUid
) {
}
