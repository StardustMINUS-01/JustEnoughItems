package mezz.jei.gui.match;

import org.jetbrains.annotations.Nullable;
import net.minecraft.nbt.CompoundTag;
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
	Set<ResourceLocation> tagIds,
	@Nullable CompoundTag nbt
) {
	public IngredientMatchInfo(Kind kind, ResourceLocation id, Set<ResourceLocation> tagIds) {
		this(kind, id, tagIds, null);
	}

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
		return fromIngredient(ingredient, includeBlockTags, true);
	}

	public static Optional<IngredientMatchInfo> fromIngredient(ITypedIngredient<?> ingredient, boolean includeBlockTags, boolean includeNbt) {
		return ingredient.getItemStack()
			.filter(stack -> !stack.isEmpty())
			.map(stack -> fromItemStack(stack, includeBlockTags, includeNbt))
			.or(() -> fromFluidIngredient(ingredient, Services.PLATFORM.getFluidHelper(), includeNbt));
	}

	private static IngredientMatchInfo fromItemStack(ItemStack stack, boolean includeBlockTags, boolean includeNbt) {
		Stream<ResourceLocation> tagLocations = stack.getTags().map(TagKey::location);
		if (stack.getItem() instanceof BlockItem blockItem && includeBlockTags) {
			tagLocations = Stream.concat(
				tagLocations,
				blockItem.getBlock().defaultBlockState().getTags().map(TagKey::location)
			);
		}
		Set<ResourceLocation> tagIds = tagLocations
			.collect(Collectors.toUnmodifiableSet());
		return new IngredientMatchInfo(Kind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()), tagIds,
			includeNbt && stack.hasTag() ? stack.getTag().copy() : null);
	}

	private static boolean includeBlockTags() {
		return Internal.getJeiClientConfigs().getClientConfig().isLookupBlockTagsEnabled();
	}

	private static <T> Optional<IngredientMatchInfo> fromFluidIngredient(
		ITypedIngredient<?> ingredient,
		IPlatformFluidHelperInternal<T> fluidHelper, boolean includeNbt
	) {
		ITypedIngredient<T> fluidIngredient = ingredient.cast(fluidHelper.getFluidIngredientType());
		if (fluidIngredient == null || fluidHelper.getAmount(fluidIngredient.getIngredient()) <= 0) {
			return Optional.empty();
		}
		T fluid = fluidIngredient.getIngredient();
		return Optional.of(new IngredientMatchInfo(Kind.FLUID, fluidHelper.getFluidId(fluid), fluidHelper.getFluidTags(fluid),
			includeNbt ? fluidHelper.getTag(fluid).map(CompoundTag::copy).orElse(null) : null));
	}

	public enum Kind {
		ITEM,
		FLUID
	}
}
