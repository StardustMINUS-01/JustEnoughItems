package mezz.jei.test.bench;

/**
 * Recipe-graph families for the recipe-tree expansion benchmark, mirroring the
 * Thunderbolt graph families (single-dag, multi-dag, cycle, catalyst, recursion).
 */
public enum JeiTreeFamily {
	/** A linear chain of recipes, each with a single input. */
	CHAIN,
	/** A complete binary tree: each recipe has two inputs that recurse. */
	BINARY_TREE,
	/** A single root recipe with many independent inputs (fan-out). */
	FAN_OUT,
	/** A conversion ring: root -> a -> root, guarded by the visited set. */
	CYCLE,
	/** A recipe whose inputs are never expanded (catalyst / un-favorited inputs). */
	CATALYST,
	/** A chain with a missing middle input (branch pruning). */
	PRUNED,
	/** An unusually deep chain used to exercise the iterative expansion loop. */
	DEEP_RECURSION
}
