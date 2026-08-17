package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public class GTRecipe {
	private final OptionalInt virtualCircuit;
	public final Map<Object, List<Content>> inputs;

	public GTRecipe(int virtualCircuit) {
		this(virtualCircuit, (ItemStack) null);
	}

	public GTRecipe(int virtualCircuit, ItemStack nonConsumableInput) {
		this(virtualCircuit, nonConsumableInput, null);
	}

	public GTRecipe(int virtualCircuit, ItemStack nonConsumableInput, Object nonConsumableFluid) {
		this(virtualCircuit, nonConsumableInput == null ? List.of() : List.of(nonConsumableInput), nonConsumableFluid);
	}

	public GTRecipe(int virtualCircuit, List<ItemStack> nonConsumableInputs) {
		this(virtualCircuit, nonConsumableInputs, null);
	}

	private GTRecipe(int virtualCircuit, List<ItemStack> nonConsumableInputs, Object nonConsumableFluid) {
		this.virtualCircuit = OptionalInt.of(virtualCircuit);
		Map<Object, List<Content>> inputs = new LinkedHashMap<>();
		if (!nonConsumableInputs.isEmpty()) {
			inputs.put(new ItemRecipeCapability(), nonConsumableInputs.stream()
				.map(stack -> new Content(0, new TestSizedIngredient(stack.copy())))
				.toList());
		}
		if (nonConsumableFluid != null) {
			inputs.put(new FluidRecipeCapability(), List.of(new Content(0, new TestSizedFluidIngredient(nonConsumableFluid))));
		}
		this.inputs = Map.copyOf(inputs);
	}

	public OptionalInt getTestVirtualCircuit() {
		return virtualCircuit;
	}

	public static final class Content {
		public final int chance;
		public final Object content;

		public Content(int chance, Object content) {
			this.chance = chance;
			this.content = content;
		}
	}

	public static final class TestSizedIngredient {
		private final ItemStack stack;

		public TestSizedIngredient(ItemStack stack) {
			this.stack = stack;
		}

		public ItemStack[] getItems() {
			return new ItemStack[]{stack.copy()};
		}

		public int amount() {
			return stack.getCount();
		}
	}

	public static final class TestSizedFluidIngredient {
		private final Object stack;

		public TestSizedFluidIngredient(Object stack) {
			this.stack = stack;
		}

		public Object[] getFluids() {
			return new Object[]{stack};
		}

		public ItemStack[] getItems() {
			return new ItemStack[0];
		}
	}
}
