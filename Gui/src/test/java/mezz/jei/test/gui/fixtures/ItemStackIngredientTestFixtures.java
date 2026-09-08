package mezz.jei.test.gui.fixtures;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.lang.reflect.Proxy;
import java.util.Optional;

public final class ItemStackIngredientTestFixtures {
	private ItemStackIngredientTestFixtures() {
	}

	public static ITypedIngredient<ItemStack> item(ItemLike item) {
		return typed(new ItemStack(item));
	}

	public static ITypedIngredient<ItemStack> typed(ItemStack stack) {
		return new ITypedIngredient<>() {
			@Override
			public ITypedIngredient<ItemStack> normalize(mezz.jei.api.ingredients.IIngredientHelper<ItemStack> helper) {
				return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(getType(), helper.normalizeIngredient(getIngredient()));
			}

			@Override
			public IIngredientType<ItemStack> getType() {
				return VanillaTypes.ITEM_STACK;
			}

			@Override
			public ItemStack getIngredient() {
				return stack;
			}
		};
	}

	public static IIngredientManager ingredientManager() {
		return ingredientManager(null, null);
	}

	public static <T> IIngredientManager ingredientManager(
		IIngredientType<T> additionalType,
		IIngredientHelper<T> additionalHelper
	) {
		return (IIngredientManager) Proxy.newProxyInstance(
			ItemStackIngredientTestFixtures.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> {
				if ("getIngredientHelper".equals(method.getName()) && args[0] == VanillaTypes.ITEM_STACK) {
					return itemHelper();
				}
				if ("getIngredientHelper".equals(method.getName()) && args[0] == additionalType) {
					return additionalHelper;
				}
				if ("createTypedIngredient".equals(method.getName()) && args[0] == VanillaTypes.ITEM_STACK) {
					return Optional.of(typed((ItemStack) args[1]));
				}
				if ("normalizeTypedIngredient".equals(method.getName())) {
					return args[0];
				}
				throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	public static IIngredientHelper<ItemStack> itemHelper() {
		return new IIngredientHelper<>() {
			@Override
			public IIngredientType<ItemStack> getIngredientType() {
				return VanillaTypes.ITEM_STACK;
			}

			@Override
			public String getDisplayName(ItemStack ingredient) {
				return ingredient.getHoverName().getString();
			}

			@Override
			public String getUniqueId(ItemStack ingredient, UidContext context) {
				ResourceLocation key = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
				return key == null ? "minecraft:air" : key.toString();
			}

			@Override
			public ResourceLocation getResourceLocation(ItemStack ingredient) {
				return BuiltInRegistries.ITEM.getKey(ingredient.getItem());
			}

			@Override
			public long getAmount(ItemStack ingredient) {
				return ingredient.getCount();
			}

			@Override
			public ItemStack copyIngredient(ItemStack ingredient) {
				return ingredient.copy();
			}

			@Override
			public ItemStack copyWithAmount(ItemStack ingredient, long amount) {
				return ingredient.copyWithCount((int) Math.clamp(amount, 0, Integer.MAX_VALUE));
			}

			@Override
			public String getErrorInfo(ItemStack ingredient) {
				return ingredient.toString();
			}
		};
	}
}
