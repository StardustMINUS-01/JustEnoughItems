package mezz.jei.gui.bookmarks;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BookmarkItemMetadataFactory {
	private BookmarkItemMetadataFactory() {
	}

	public static <R, T> BookmarkItemMetadata createForRecipeSlot(
		String groupId,
		IRecipeCategory<R> recipeCategory,
		ResourceLocation recipeUid,
		RecipeIngredientRole role,
		IRecipeSlotView sourceSlot,
		List<IRecipeSlotView> roleSlots,
		ITypedIngredient<T> selectedIngredient,
		IIngredientManager ingredientManager
	) {
		BookmarkItemType type = toBookmarkItemType(role);
		Set<BookmarkIngredientKey> permutations = createPermutations(type, sourceSlot, selectedIngredient, ingredientManager);
		long factor = getMatchedFactor(selectedIngredient, roleSlots, ingredientManager);
		ContainerItemInfo containerItem = createContainerItemInfo(type, selectedIngredient, ingredientManager);
		return new BookmarkItemMetadata(
			groupId,
			type,
			1,
			Math.max(1, factor),
			BookmarkItemMetadata.CHANCE_FULL,
			recipeCategory.getRecipeType().getUid(),
			recipeUid,
			permutations,
			containerItem.key(),
			containerItem.craftingUses(),
			containerItem.brokenKey()
		);
	}

	public static <T> BookmarkIngredientKey createPermutationKey(ITypedIngredient<T> ingredient, IIngredientManager ingredientManager) {
		try {
			String typeUid = ingredient.getType().getUid();
			return BookmarkIngredientKey.of(typeUid, getUniqueId(ingredient, ingredientManager));
		} catch (RuntimeException e) {
			return BookmarkIngredientKey.fallback("fallback:" + getFallbackIngredientId(ingredient));
		}
	}

	private static BookmarkItemType toBookmarkItemType(RecipeIngredientRole role) {
		return switch (role) {
			case INPUT -> BookmarkItemType.INGREDIENT;
			case OUTPUT -> BookmarkItemType.RESULT;
			default -> BookmarkItemType.ITEM;
		};
	}

	private static <T> Set<BookmarkIngredientKey> createPermutations(
		BookmarkItemType type,
		IRecipeSlotView sourceSlot,
		ITypedIngredient<T> selectedIngredient,
		IIngredientManager ingredientManager
	) {
		if (type != BookmarkItemType.INGREDIENT) {
			return Set.of(createPermutationKey(selectedIngredient, ingredientManager));
		}

		Set<BookmarkIngredientKey> permutations = new LinkedHashSet<>();
		sourceSlot.getAllIngredients()
			.map(ingredient -> createPermutationKey(ingredient, ingredientManager))
			.forEach(permutations::add);
		if (permutations.isEmpty()) {
			permutations.add(createPermutationKey(selectedIngredient, ingredientManager));
		}
		return permutations;
	}

	private static <T> long getMatchedFactor(
		ITypedIngredient<T> selectedIngredient,
		List<IRecipeSlotView> roleSlots,
		IIngredientManager ingredientManager
	) {
		BookmarkIngredientKey selectedKey = createPermutationKey(selectedIngredient, ingredientManager);
		long amount = 0;
		for (IRecipeSlotView slot : roleSlots) {
			List<ITypedIngredient<?>> matchingIngredients = slot.getAllIngredients()
				.filter(ingredient -> selectedKey.equals(createPermutationKey(ingredient, ingredientManager)))
				.toList();
			if (!matchingIngredients.isEmpty()) {
				amount = saturatedAdd(amount, BookmarkIngredientAmountResolver.getAmount(matchingIngredients.get(0), ingredientManager));
			}
		}
		return amount;
	}

	private static <T> ContainerItemInfo createContainerItemInfo(
		BookmarkItemType type,
		ITypedIngredient<T> ingredient,
		IIngredientManager ingredientManager
	) {
		if (type != BookmarkItemType.INGREDIENT) {
			return ContainerItemInfo.EMPTY;
		}
		T containerItem = getCraftingRemainingItem(ingredient.getIngredient());
		if (containerItem == null) {
			return ContainerItemInfo.EMPTY;
		}
		BookmarkIngredientKey containerKey = createPermutationKey(typedIngredient(ingredient, containerItem), ingredientManager);
		long craftingUses = createContainerItemCraftingUses(type, ingredient.getIngredient(), containerKey);
		BookmarkIngredientKey brokenKey = createBrokenContainerItemKey(ingredient, ingredientManager);
		return new ContainerItemInfo(containerKey, craftingUses, brokenKey);
	}

	private static <T> ITypedIngredient<T> typedIngredient(ITypedIngredient<T> source, T ingredient) {
		return new ITypedIngredient<>() {
			@Override
			public mezz.jei.api.ingredients.IIngredientType<T> getType() {
				return source.getType();
			}

			@Override
			public T getIngredient() {
				return ingredient;
			}
		};
	}

	private static long createContainerItemCraftingUses(
		BookmarkItemType type,
		Object ingredient,
		BookmarkIngredientKey containerItem
	) {
		if (type != BookmarkItemType.INGREDIENT || containerItem == null) {
			return 1;
		}
		int damagePerCraft = getToolDamagePerCraft(ingredient);
		long maxDamage = getLongValue(ingredient, "getMaxDamage");
		long damage = getLongValue(ingredient, "getDamageValue");
		if (maxDamage <= 0) {
			maxDamage = getLegacyGtToolStatsLong(ingredient, "MaxDamage");
			damage = getLegacyGtToolStatsLong(ingredient, "Damage");
		}
		if (damagePerCraft <= 0) {
			return 1;
		}
		long electricUses = getElectricCraftingUses(ingredient, damagePerCraft);
		if (electricUses >= 0) {
			if (maxDamage <= 0) {
				return electricUses;
			}
			long durabilityUses = saturatedDivideRoundUp(Math.max(0, maxDamage - damage), damagePerCraft);
			return Math.min(durabilityUses, electricUses);
		}
		if (maxDamage <= 0) {
			return 1;
		}
		long durabilityUses = saturatedDivideRoundUp(Math.max(0, maxDamage - damage), damagePerCraft);
		return Math.max(1, durabilityUses);
	}

	private static long getElectricCraftingUses(Object ingredient, int damagePerCraft) {
		try {
			Object item = invokeNoArg(ingredient, "getItem");
			if (item == null || !Boolean.TRUE.equals(invokeNoArg(item, "isElectric"))) {
				return -1;
			}
			long charge = getLongValue(item, "getCharge", ingredient);
			if (charge <= 0) {
				return 0;
			}
			long energyPerCraft = saturatedMultiply(damagePerCraft, getGtmEnergyUsageMultiplier());
			if (energyPerCraft <= 0) {
				return -1;
			}
			return charge / energyPerCraft;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return -1;
		}
	}

	private static long getGtmEnergyUsageMultiplier() {
		try {
			Class<?> configHolder = Class.forName("com.gregtechceu.gtceu.config.ConfigHolder");
			Object instance = configHolder.getField("INSTANCE").get(null);
			Object machines = instance.getClass().getField("machines").get(instance);
			Object value = machines.getClass().getField("energyUsageMultiplier").get(machines);
			if (value instanceof Number number) {
				return Math.max(1, number.longValue());
			}
		} catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
		}
		return 100;
	}

	private static <T> BookmarkIngredientKey createBrokenContainerItemKey(
		ITypedIngredient<T> ingredient,
		IIngredientManager ingredientManager
	) {
		try {
			Object item = invokeNoArg(ingredient.getIngredient(), "getItem");
			if (item == null) {
				return null;
			}
			Object toolStats = getToolStats(item, ingredient.getIngredient());
			if (toolStats == null) {
				return null;
			}
			Object brokenStack = invokeNoArg(toolStats, "getBrokenStack");
			if (brokenStack == null) {
				return null;
			}
			@SuppressWarnings("unchecked")
			T typedBrokenStack = (T) brokenStack;
			return createPermutationKey(typedIngredient(ingredient, typedBrokenStack), ingredientManager);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	private static int getToolDamagePerCraft(Object ingredient) {
		try {
			Object item = invokeNoArg(ingredient, "getItem");
			if (item == null) {
				return 0;
			}
			Object toolStats = getToolStats(item, ingredient);
			if (toolStats == null) {
				return 0;
			}
			for (String methodName : List.of("getToolDamagePerCraft", "getDamagePerCraftingAction")) {
				Integer damage = invokeIntMethod(toolStats, methodName, ingredient);
				if (damage != null) {
					return damage;
				}
			}
			Integer legacyDamage = invokeIntNoArgMethod(toolStats, "getToolDamagePerContainerCraft");
			if (legacyDamage != null) {
				return legacyDamage;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return 0;
		}
		return 0;
	}

	private static Object getToolStats(Object item, Object ingredient) throws ReflectiveOperationException {
		try {
			return invokeNoArg(item, "getToolStats");
		} catch (NoSuchMethodException ignored) {
			for (Method method : item.getClass().getMethods()) {
				if (!"getToolStats".equals(method.getName()) || method.getParameterCount() != 1) {
					continue;
				}
				Class<?> parameterType = method.getParameterTypes()[0];
				if (!parameterType.isAssignableFrom(ingredient.getClass())) {
					continue;
				}
				method.trySetAccessible();
				return method.invoke(item, ingredient);
			}
			return null;
		}
	}

	private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
		Method method = target.getClass().getMethod(methodName);
		method.trySetAccessible();
		return method.invoke(target);
	}

	private static Integer invokeIntMethod(Object target, String methodName, Object argument) throws ReflectiveOperationException {
		for (Method method : target.getClass().getMethods()) {
			if (!methodName.equals(method.getName()) || method.getParameterCount() != 1) {
				continue;
			}
			Class<?> parameterType = method.getParameterTypes()[0];
			if (!parameterType.isAssignableFrom(argument.getClass())) {
				continue;
			}
			method.trySetAccessible();
			Object result = method.invoke(target, argument);
			if (result instanceof Number number) {
				return number.intValue();
			}
		}
		return null;
	}

	private static Integer invokeIntNoArgMethod(Object target, String methodName) throws ReflectiveOperationException {
		Object result = invokeNoArg(target, methodName);
		if (result instanceof Number number) {
			return number.intValue();
		}
		return null;
	}

	private static long getLongValue(Object target, String methodName) {
		try {
			Object value = invokeNoArg(target, methodName);
			if (value instanceof Number number) {
				return number.longValue();
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return 0;
	}

	private static long getLongValue(Object target, String methodName, Object argument) {
		try {
			for (Method method : target.getClass().getMethods()) {
				if (!methodName.equals(method.getName()) || method.getParameterCount() != 1) {
					continue;
				}
				Class<?> parameterType = method.getParameterTypes()[0];
				if (!parameterType.isAssignableFrom(argument.getClass())) {
					continue;
				}
				method.trySetAccessible();
				Object value = method.invoke(target, argument);
				if (value instanceof Number number) {
					return number.longValue();
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return 0;
	}

	private static long getLegacyGtToolStatsLong(Object ingredient, String key) {
		try {
			Object tag = getTag(ingredient);
			if (tag == null || !hasStringKey(tag, "GT.ToolStats")) {
				return 0;
			}
			Object toolStats = getCompound(tag, "GT.ToolStats");
			if (toolStats == null) {
				return 0;
			}
			Method getLong = toolStats.getClass().getMethod("getLong", String.class);
			getLong.trySetAccessible();
			Object value = getLong.invoke(toolStats, key);
			if (value instanceof Number number) {
				return number.longValue();
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return 0;
	}

	private static Object getTag(Object ingredient) throws ReflectiveOperationException {
		try {
			return invokeNoArg(ingredient, "getTagCompound");
		} catch (NoSuchMethodException ignored) {
			try {
				return invokeNoArg(ingredient, "getTag");
			} catch (NoSuchMethodException ignoredAgain) {
				return null;
			}
		}
	}

	private static boolean hasStringKey(Object tag, String key) throws ReflectiveOperationException {
		for (String methodName : List.of("hasKey", "contains")) {
			try {
				Method method = tag.getClass().getMethod(methodName, String.class);
				method.trySetAccessible();
				Object result = method.invoke(tag, key);
				if (result instanceof Boolean bool) {
					return bool;
				}
			} catch (NoSuchMethodException ignored) {
			}
		}
		return false;
	}

	private static Object getCompound(Object tag, String key) throws ReflectiveOperationException {
		for (String methodName : List.of("getCompoundTag", "getCompound")) {
			try {
				Method method = tag.getClass().getMethod(methodName, String.class);
				method.trySetAccessible();
				return method.invoke(tag, key);
			} catch (NoSuchMethodException ignored) {
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T> T getCraftingRemainingItem(T ingredient) {
		try {
			Method hasCraftingRemainingItem = ingredient.getClass().getMethod("hasCraftingRemainingItem");
			hasCraftingRemainingItem.trySetAccessible();
			if (!Boolean.TRUE.equals(hasCraftingRemainingItem.invoke(ingredient))) {
				return null;
			}
			Method getCraftingRemainingItem = ingredient.getClass().getMethod("getCraftingRemainingItem");
			getCraftingRemainingItem.trySetAccessible();
			Object remainingItem = getCraftingRemainingItem.invoke(ingredient);
			return remainingItem == ingredient ? null : (T) remainingItem;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> String getUniqueId(ITypedIngredient<T> ingredient, IIngredientManager ingredientManager) {
		try {
			IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredient.getType());
			return ingredientHelper.getUniqueId(ingredient.getIngredient(), UidContext.Ingredient);
		} catch (RuntimeException e) {
			return "fallback:" + getFallbackIngredientId(ingredient);
		}
	}

	private static long saturatedAdd(long first, long second) {
		try {
			return Math.addExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	private static long saturatedMultiply(long first, long second) {
		try {
			return Math.multiplyExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	private static long saturatedDivideRoundUp(long numerator, long denominator) {
		if (denominator <= 0 || numerator <= 0) {
			return 0;
		}
		return 1 + (numerator - 1) / denominator;
	}

	private static String getFallbackIngredientId(ITypedIngredient<?> ingredient) {
		try {
			return Objects.toString(ingredient.getIngredient());
		} catch (RuntimeException e) {
			return ingredient.getClass().getName();
		}
	}

	private record ContainerItemInfo(
		BookmarkIngredientKey key,
		long craftingUses,
		BookmarkIngredientKey brokenKey
	) {
		private static final ContainerItemInfo EMPTY = new ContainerItemInfo(null, 1, null);
	}
}
