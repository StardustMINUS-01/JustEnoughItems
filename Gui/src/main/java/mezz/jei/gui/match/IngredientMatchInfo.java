package mezz.jei.gui.match;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.Internal;
import mezz.jei.common.platform.IPlatformFluidHelperInternal;
import mezz.jei.common.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record IngredientMatchInfo(
	Kind kind,
	ResourceLocation id,
	Set<ResourceLocation> tagIds
) {
	public IngredientMatchInfo {
		tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
	}

	public static IngredientMatchInfo item(ResourceLocation id, Set<ResourceLocation> tagIds) {
		return new IngredientMatchInfo(Kind.ITEM, id, tagIds);
	}

	public static IngredientMatchInfo fluid(ResourceLocation id, Set<ResourceLocation> tagIds) {
		return new IngredientMatchInfo(Kind.FLUID, id, tagIds);
	}

	public static Optional<IngredientMatchInfo> fromIngredient(ITypedIngredient<?> ingredient) {
		return fromIngredient(ingredient, includeBlockTags());
	}

	public static Optional<IngredientMatchInfo> fromIngredient(
		ITypedIngredient<?> ingredient,
		boolean includeBlockTags
	) {
		return ingredient.getItemStack()
			.filter(stack -> !stack.isEmpty())
			.map(stack -> fromItemStack(stack, includeBlockTags))
			.or(() -> fromFluidIngredient(ingredient, Services.PLATFORM.getFluidHelper()));
	}

	private static IngredientMatchInfo fromItemStack(ItemStack stack, boolean includeBlockTags) {
		Stream<ResourceLocation> tagLocations = stack.getTags().map(TagKey::location);
		if (stack.getItem() instanceof BlockItem blockItem && includeBlockTags) {
			tagLocations = Stream.concat(
				tagLocations,
				blockItem.getBlock().defaultBlockState().getTags().map(TagKey::location)
			);
		}
		Set<ResourceLocation> tagIds = tagLocations
			.collect(Collectors.toUnmodifiableSet());
		return item(BuiltInRegistries.ITEM.getKey(stack.getItem()), tagIds);
	}

	private static boolean includeBlockTags() {
		return Internal.getJeiClientConfigs().getClientConfig().isLookupBlockTagsEnabled();
	}

	private static <T> Optional<IngredientMatchInfo> fromFluidIngredient(
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
