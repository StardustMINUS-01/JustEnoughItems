package com.gregtechceu.gtceu.common.item.behavior;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class IntCircuitBehaviour {
	private static final String CONFIGURATION_TAG = "circuit_configuration";

	private IntCircuitBehaviour() {
	}

	public static ItemStack stack(int configuration) {
		ItemStack stack = new ItemStack(Items.REPEATER);
		stack.setCount(1);
		CompoundTag tag = new CompoundTag();
		tag.putInt(CONFIGURATION_TAG, configuration);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	public static boolean isIntegratedCircuit(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.REPEATER) && stack.get(DataComponents.CUSTOM_DATA) != null;
	}

	public static int getCircuitConfiguration(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return -1;
		}
		return data.copyTag().getInt(CONFIGURATION_TAG);
	}
}
