package mezz.jei.test.bench;

/**
 * Recipe-chain families for the expansion-chain benchmark, mirroring the
 * recipe-tree families but focused on the quantity-propagation semantics of
 * {@code RecipeChainMath} + {@code AutoCraftingManager} (the "expansion
 * chain" that shift+C drives).
 */
public enum JeiChainFamily {
	/** A linear chain: raw -> ingot -> plate, each recipe with a single output. */
	CHAIN,
	/** A chain whose root result carries a multiplier > 1 (craft-all batch). */
	MULTIPLIER,
	/** A conversion ring: a -> b -> a, guarded by the cycle breaker. */
	CYCLE,
	/** A recipe with a catalyst ingredient that must not scale with multiplier. */
	CATALYST
}
