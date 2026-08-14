package mezz.jei.test.bench;

/**
 * The "material mode" of a scenario, mirroring the AE2VM/Thunderbolt
 * MISSING / MINIMUM / UNBOUNDED taxonomy adapted for JEI search data.
 */
public enum JeiDataMode {
	/** The searched data is missing entirely (e.g. no indexed ingredient matches). */
	MISSING,
	/** The minimum viable dataset (the two base plugin ingredients). */
	MINIMUM,
	/** An unbounded / large dataset used to exercise scale and performance. */
	UNBOUNDED
}
