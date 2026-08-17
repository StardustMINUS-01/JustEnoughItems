package com.gregtechceu.gtceu.integration.ae2.pattern;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.OptionalInt;

public final class GTRecipePatternMetadata {
	private GTRecipePatternMetadata() {
	}

	public static OptionalInt getVirtualCircuit(GTRecipe recipe) {
		return recipe.getTestVirtualCircuit();
	}
}
