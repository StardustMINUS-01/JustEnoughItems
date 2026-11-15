package mezz.jei.common.recipes;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Resolves saved display identities without rewriting the caller's persisted key. */
public final class RecipeIdentifierLookup {
	private RecipeIdentifierLookup() {
	}

	public static <T> Optional<T> findRecipe(IRecipeCategory<T> category, IRecipeLookup<T> lookup, ResourceLocation savedId, IRecipeManager manager) {
		Optional<T> exact = lookup.get().filter(recipe -> savedId.equals(category.getRegistryName(recipe))).findFirst();
		if (exact.isPresent()) {
			return exact;
		}
		// Old grindstone IDs omitted the enchantment namespace. Reject ambiguous aliases instead of choosing one.
		List<T> aliases = lookup.get().filter(recipe -> matchesLegacyIdentifier(category, recipe, savedId, manager)).limit(2).toList();
		return aliases.size() == 1 ? Optional.of(aliases.getFirst()) : Optional.empty();
	}

	private static <T> boolean matchesLegacyIdentifier(IRecipeCategory<T> category, T recipe, ResourceLocation savedId, IRecipeManager manager) {
		ResourceLocation currentId = category.getRegistryName(recipe);
		if (currentId == null) {
			return false;
		}
		String savedPath = savedId.getPath();
		if (!savedPath.startsWith("/") && savedId.withPrefix("/").equals(currentId)) {
			return true;
		}
		if (!savedId.getNamespace().equals("minecraft") || !currentId.getNamespace().equals("minecraft")) {
			return false;
		}
		String legacyPath = savedPath.startsWith("/") ? savedPath.substring(1) : savedPath;
		if (RecipeTypes.CRAFTING.equals(category.getRecipeType()) && legacyPath.startsWith("jei.tipped.arrow.") && currentId.getPath().startsWith("/jei.tipped.arrow.")) {
			return manager.getRecipeIngredients(category, recipe, Set.of(RecipeIngredientRole.OUTPUT))
				.getIngredients(RecipeIngredientRole.OUTPUT).stream()
				.flatMap(ingredient -> ingredient.getItemStack().stream())
				.filter(stack -> stack.is(Items.TIPPED_ARROW))
				.anyMatch(stack -> legacyPath.equals("jei.tipped.arrow." + stack.getDescriptionId()));
		}
		if (RecipeTypes.GRINDSTONE.equals(category.getRecipeType()) && legacyPath.startsWith("grindstone.disenchantment.") &&
			currentId.getPath().startsWith("/grindstone.disenchantment.") && recipe instanceof IJeiGrindstoneRecipe grindstone
		) {
			for (ItemStack input : grindstone.getTopInputs()) {
				for (var enchantment : EnchantmentHelper.getEnchantmentsForCrafting(input).entrySet()) {
					var key = enchantment.getKey().unwrapKey();
					if (key.isEmpty()) {
						continue;
					}
					ResourceLocation enchantmentId = key.get().location();
					int level = enchantment.getIntValue();
					String currentPath = "/grindstone.disenchantment." + enchantmentId.getNamespace() + "." + enchantmentId.getPath() + "." + level;
					String oldPath = "grindstone.disenchantment." + input.getItem().getDescriptionId() + "." + enchantmentId.getPath() + "." + level;
					if (currentId.getPath().equals(sanitizePath(currentPath)) && legacyPath.equals(sanitizePath(oldPath))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static String sanitizePath(String path) {
		StringBuilder sanitized = new StringBuilder(path.length());
		for (int i = 0; i < path.length(); i++) {
			char character = path.charAt(i);
			sanitized.append(ResourceLocation.validPathChar(character) ? character : '.');
		}
		return sanitized.toString();
	}
}
