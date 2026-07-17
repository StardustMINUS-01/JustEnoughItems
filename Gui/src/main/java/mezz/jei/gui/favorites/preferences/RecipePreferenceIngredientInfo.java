package mezz.jei.gui.favorites.preferences;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.platform.IPlatformFluidHelperInternal;
import mezz.jei.common.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

public record RecipePreferenceIngredientInfo(
	Kind kind,
	ResourceLocation id,
	Set<ResourceLocation> tagIds
) {
	public RecipePreferenceIngredientInfo {
		tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
	}

	public static RecipePreferenceIngredientInfo item(ResourceLocation id, Set<ResourceLocation> tagIds) {
		return new RecipePreferenceIngredientInfo(Kind.ITEM, id, tagIds);
	}

	public static RecipePreferenceIngredientInfo fluid(ResourceLocation id, Set<ResourceLocation> tagIds) {
		return new RecipePreferenceIngredientInfo(Kind.FLUID, id, tagIds);
	}

	public static Optional<RecipePreferenceIngredientInfo> fromIngredient(ITypedIngredient<?> ingredient) {
		return ingredient.getItemStack()
			.filter(stack -> !stack.isEmpty())
			.map(RecipePreferenceIngredientInfo::fromItemStack)
			.or(() -> fromFluidIngredient(ingredient, Services.PLATFORM.getFluidHelper()));
	}

	private static RecipePreferenceIngredientInfo fromItemStack(ItemStack stack) {
		Set<ResourceLocation> tagIds = stack.getTags()
			.map(TagKey::location)
			.collect(Collectors.toUnmodifiableSet());
		return item(BuiltInRegistries.ITEM.getKey(stack.getItem()), tagIds);
	}

	private static <T> Optional<RecipePreferenceIngredientInfo> fromFluidIngredient(
		ITypedIngredient<?> ingredient,
		IPlatformFluidHelperInternal<T> fluidHelper
	) {
		ITypedIngredient<T> fluidIngredient = ingredient.cast(fluidHelper.getFluidIngredientType());
		if (fluidIngredient == null || fluidHelper.getAmount(fluidIngredient.getIngredient()) <= 0) {
			return Optional.empty();
		}
		T fluid = fluidIngredient.getIngredient();
		return Optional.of(fluid(fluidHelper.getFluidId(fluid), fluidHelper.getFluidTags(fluid)));
	}

	public enum Kind {
		ITEM,
		FLUID
	}
}
