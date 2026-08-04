package mezz.jei.gui.compat.gtm;

import mezz.jei.common.util.ReflectionCache;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.Services;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

public final class GtmVirtualCircuitCompat {
	private static final Access ACCESS = Access.create();

	private GtmVirtualCircuitCompat() {
	}

	private static Optional<ITypedIngredient<ItemStack>> createCircuitIngredient(
		int circuit,
		IIngredientManager ingredientManager
	) {
		ItemStack stack = ACCESS.createCircuitStack(circuit);
		if (stack == null || stack.isEmpty()) {
			return Optional.empty();
		}
		stack.setCount(1);
		return ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false);
	}

	public static boolean isProgrammedCircuit(ITypedIngredient<?> ingredient) {
		return ingredient.getItemStack()
			.filter(stack -> !stack.isEmpty())
			.map(ACCESS::isProgrammedCircuit)
			.orElse(false);
	}

	public static VirtualInputProjection projectVirtualInputs(
		@Nullable Object recipeLike,
		IIngredientManager ingredientManager
	) {
		return projectVirtualInputs(recipeLike, ingredientManager,
			fluid -> createFluidIngredient(fluid, ingredientManager));
	}

	static VirtualInputProjection projectVirtualInputs(
		@Nullable Object recipeLike,
		IIngredientManager ingredientManager,
		Function<Object, Optional<ITypedIngredient<?>>> fluidIngredientFactory
	) {
		List<VirtualInput> result = new ArrayList<>();
		for (RawVirtualInput input : ACCESS.readRawVirtualInputs(recipeLike, 0)) {
			Optional<? extends ITypedIngredient<?>> typed = switch (input.kind()) {
				case ITEM -> ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, (ItemStack) input.ingredient(), false);
				case FLUID -> fluidIngredientFactory.apply(input.ingredient());
			};
			typed.ifPresent(ingredient -> result.add(new VirtualInput(ingredient, isProgrammedCircuit(ingredient))));
		}

		OptionalInt circuit = ACCESS.getVirtualCircuit(recipeLike, 0);
		if (circuit.isPresent() && result.stream().noneMatch(VirtualInput::programmedCircuit)) {
			createCircuitIngredient(circuit.getAsInt(), ingredientManager)
				.ifPresent(ingredient -> result.add(new VirtualInput(ingredient, true)));
		}

		return new VirtualInputProjection(result);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Optional<ITypedIngredient<?>> createFluidIngredient(Object fluid, IIngredientManager ingredientManager) {
		IIngredientType fluidType = Services.PLATFORM.getFluidHelper().getFluidIngredientType();
		return ingredientManager.createTypedIngredient(fluidType, fluid, false)
			.map(ingredient -> (ITypedIngredient<?>) ingredient);
	}

	private static final class Access {
		private static final String GT_RECIPE_CLASS = "com.gregtechceu.gtceu.api.recipe.GTRecipe";
		private static final String RECIPE_METADATA_CLASS = "com.gregtechceu.gtceu.integration.ae2.pattern.GTRecipePatternMetadata";
		private static final String INT_CIRCUIT_CLASS = "com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour";
		private static final String ITEM_RECIPE_CAPABILITY_CLASS = "com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability";
		private static final String FLUID_RECIPE_CAPABILITY_CLASS = "com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability";

		@Nullable
		private final Class<?> gtRecipeClass;
		@Nullable
		private final Method getVirtualCircuit;
		@Nullable
		private final Method createCircuitStack;
		@Nullable
		private final Method isIntegratedCircuit;

		private Access(
			@Nullable Class<?> gtRecipeClass,
			@Nullable Method getVirtualCircuit,
			@Nullable Method createCircuitStack,
			@Nullable Method isIntegratedCircuit
		) {
			this.gtRecipeClass = gtRecipeClass;
			this.getVirtualCircuit = getVirtualCircuit;
			this.createCircuitStack = createCircuitStack;
			this.isIntegratedCircuit = isIntegratedCircuit;
		}

		private static Access create() {
			Class<?> gtRecipeClass = classOrNull(GT_RECIPE_CLASS);
			Method getVirtualCircuit = gtRecipeClass == null ? null :
				staticMethod(RECIPE_METADATA_CLASS, "getVirtualCircuit", gtRecipeClass);
			Method createCircuitStack = staticMethod(INT_CIRCUIT_CLASS, "stack", int.class);
			Method isIntegratedCircuit = staticMethod(INT_CIRCUIT_CLASS, "isIntegratedCircuit", ItemStack.class);
			return new Access(gtRecipeClass, getVirtualCircuit, createCircuitStack, isIntegratedCircuit);
		}

		private OptionalInt getVirtualCircuit(@Nullable Object recipeLike, int depth) {
			if (recipeLike == null || depth > 4 || gtRecipeClass == null || getVirtualCircuit == null) {
				return OptionalInt.empty();
			}
			if (gtRecipeClass.isInstance(recipeLike)) {
				Object result = invoke(getVirtualCircuit, recipeLike);
				return result instanceof OptionalInt optional ? optional : OptionalInt.empty();
			}
			for (String methodName : List.of("gtceu$getRecipe", "getRecipe", "recipe", "value")) {
				OptionalInt circuit = getVirtualCircuit(invokeNoArg(recipeLike, methodName), depth + 1);
				if (circuit.isPresent()) {
					return circuit;
				}
			}
			for (String fieldName : List.of("recipe", "gtRecipe")) {
				OptionalInt circuit = getVirtualCircuit(readField(recipeLike, fieldName), depth + 1);
				if (circuit.isPresent()) {
					return circuit;
				}
			}
			return OptionalInt.empty();
		}

		@Nullable
		private ItemStack createCircuitStack(int circuit) {
			Object result = createCircuitStack == null ? null : invoke(createCircuitStack, circuit);
			return result instanceof ItemStack stack ? stack : null;
		}

		private boolean isProgrammedCircuit(ItemStack stack) {
			Object result = isIntegratedCircuit == null ? null : invoke(isIntegratedCircuit, stack);
			return Boolean.TRUE.equals(result);
		}

		private List<RawVirtualInput> readRawVirtualInputs(@Nullable Object recipeLike, int depth) {
			if (recipeLike == null || depth > 4 || gtRecipeClass == null) {
				return List.of();
			}
			if (gtRecipeClass.isInstance(recipeLike)) {
				Object inputs = readField(recipeLike, "inputs");
				if (!(inputs instanceof java.util.Map<?, ?> inputCapabilities)) {
					return List.of();
				}
				List<RawVirtualInput> result = new ArrayList<>();
				for (var inputCapability : inputCapabilities.entrySet()) {
					boolean isItemCapability = isInstanceOf(inputCapability.getKey(), ITEM_RECIPE_CAPABILITY_CLASS);
					boolean isFluidCapability = isInstanceOf(inputCapability.getKey(), FLUID_RECIPE_CAPABILITY_CLASS);
					if (!isItemCapability && !isFluidCapability) {
						continue;
					}

					Object contents = inputCapability.getValue();
					if (!(contents instanceof Iterable<?> entries)) {
						continue;
					}
					for (Object entry : entries) {
						Object chance = readField(entry, "chance");
						if (!(chance instanceof Integer value) || value != 0) {
							continue;
						}
						Object ingredient = readField(entry, "content");
						if (isItemCapability) {
							getSingleItemInput(ingredient).ifPresent(stack -> result.add(new RawVirtualInput(stack, VirtualInputKind.ITEM)));
						} else {
							getSingleFluidInput(ingredient).ifPresent(fluid -> result.add(new RawVirtualInput(fluid, VirtualInputKind.FLUID)));
						}
					}
				}
				return List.copyOf(result);
			}
			for (String methodName : List.of("gtceu$getRecipe", "getRecipe", "recipe", "value")) {
				List<RawVirtualInput> result = readRawVirtualInputs(invokeNoArg(recipeLike, methodName), depth + 1);
				if (!result.isEmpty()) {
					return result;
				}
			}
			for (String fieldName : List.of("recipe", "gtRecipe")) {
				List<RawVirtualInput> result = readRawVirtualInputs(readField(recipeLike, fieldName), depth + 1);
				if (!result.isEmpty()) {
					return result;
				}
			}
			return List.of();
		}

		private static Optional<ItemStack> getSingleItemInput(@Nullable Object ingredient) {
			Object candidates = invokeNoArg(ingredient, "getItems");
			if (!(candidates instanceof ItemStack[])) {
				candidates = invokeNoArg(invokeNoArg(ingredient, "ingredient"), "getItems");
			}
			if (!(candidates instanceof ItemStack[] stacks) || stacks.length != 1 || stacks[0].isEmpty()) {
				return Optional.empty();
			}
			Object amount = invokeNoArg(ingredient, "amount");
			if (!(amount instanceof Integer)) {
				amount = invokeNoArg(ingredient, "count");
			}
			int count = amount instanceof Integer countValue ? Math.max(1, countValue) : Math.max(1, stacks[0].getCount());
			return Optional.of(stacks[0].copyWithCount(count));
		}

		private static Optional<Object> getSingleFluidInput(@Nullable Object ingredient) {
			Object candidates = invokeNoArg(ingredient, "getFluids");
			if (!(candidates instanceof Object[] stacks) || stacks.length != 1 || stacks[0] == null
				|| Boolean.TRUE.equals(invokeNoArg(stacks[0], "isEmpty"))) {
				return Optional.empty();
			}
			return Optional.of(stacks[0]);
		}

		@Nullable
		private static Class<?> classOrNull(String className) {
			try {
				return Class.forName(className);
			} catch (ClassNotFoundException | LinkageError e) {
				return null;
			}
		}

		@Nullable
		private static Method staticMethod(String className, String methodName, Class<?>... parameterTypes) {
			Class<?> clazz = classOrNull(className);
			if (clazz == null) {
				return null;
			}
			return ReflectionCache.findMethod(clazz, methodName, parameterTypes).orElse(null);
		}

		@Nullable
		private static Object invoke(Method method, Object... args) {
			try {
				return method.invoke(null, args);
			} catch (ReflectiveOperationException | IllegalArgumentException | LinkageError e) {
				return null;
			}
		}

		@Nullable
		private static Object invokeNoArg(Object target, String methodName) {
			if (target == null) {
				return null;
			}
			try {
				Method method = ReflectionCache.findMethod(target.getClass(), methodName).orElse(null);
				if (method == null) {
					return null;
				}
				return method.invoke(target);
			} catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError e) {
				return null;
			}
		}

		private static boolean isInstanceOf(@Nullable Object value, String className) {
			Class<?> clazz = classOrNull(className);
			return clazz != null && clazz.isInstance(value);
		}

		@Nullable
		private static Object readField(Object target, String fieldName) {
			Field field = ReflectionCache.findField(target.getClass(), fieldName).orElse(null);
			if (field == null) {
				return null;
			}
			try {
				return field.get(target);
			} catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError e) {
				return null;
			}
		}
	}

	public record VirtualInputProjection(List<VirtualInput> inputs) {
		public VirtualInputProjection {
			inputs = List.copyOf(inputs);
		}

		public List<VirtualInput> catalysts() {
			return inputs.stream()
				.filter(input -> !input.programmedCircuit())
				.toList();
		}

		public List<VirtualInput> programmedCircuits() {
			return inputs.stream()
				.filter(VirtualInput::programmedCircuit)
				.toList();
		}
	}

	public record VirtualInput(ITypedIngredient<?> ingredient, boolean programmedCircuit) {
	}

	private record RawVirtualInput(Object ingredient, VirtualInputKind kind) {
	}

	private enum VirtualInputKind {
		ITEM,
		FLUID
	}
}
