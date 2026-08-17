package com.gregtechceu.gtceu.common.item.behavior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class IntCircuitBehaviour {
	private static final String CONFIGURATION_TAG = "circuit_configuration";

	private IntCircuitBehaviour() {
	}

	public static ItemStack stack(int configuration) {
		ItemStack stack = new ItemStack(Items.REPEATER);
		stack.setCount(1);
		stack.getOrCreateTag().putInt(CONFIGURATION_TAG, configuration);
		return stack;
	}

	public static boolean isIntegratedCircuit(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.REPEATER) && stack.hasTag() && stack.getTag().contains(CONFIGURATION_TAG);
	}

	public static int getCircuitConfiguration(ItemStack stack) {
		if (!stack.hasTag() || !stack.getTag().contains(CONFIGURATION_TAG)) {
			return -1;
		}
		return stack.getTag().getInt(CONFIGURATION_TAG);
	}
}
