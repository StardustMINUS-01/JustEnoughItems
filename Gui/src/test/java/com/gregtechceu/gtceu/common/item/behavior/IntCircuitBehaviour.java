package com.gregtechceu.gtceu.common.item.behavior;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.WeakHashMap;

public final class IntCircuitBehaviour {
	private static final Map<ItemStack, Integer> CIRCUITS = new WeakHashMap<>();

	private IntCircuitBehaviour() {
	}

	public static ItemStack stack(int configuration) {
		ItemStack stack = new ItemStack(Items.REPEATER);
		stack.setCount(1);
		CIRCUITS.put(stack, configuration);
		return stack;
	}

	public static boolean isIntegratedCircuit(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.REPEATER) && CIRCUITS.containsKey(stack);
	}

	public static int getCircuitConfiguration(ItemStack stack) {
		return CIRCUITS.getOrDefault(stack, -1);
	}
}
