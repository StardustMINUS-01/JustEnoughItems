package mezz.jei.test.bench;

/**
 * The search capability under test, mirroring the Thunderbolt
 * {@code ReferenceCapability} notion of a graph family / capability surface.
 */
public enum JeiSearchCapability {
	/** Plain display-name prefix search (no prefix token). */
	NAME_PREFIX,
	/** Plain display-name substring search. */
	NAME_SUBSTRING,
	/** Plain display-name suffix search. */
	NAME_SUFFIX,
	/** Multi-token query intersected across tokens. */
	NAME_MULTI_TOKEN,
	/** {@code @mod} prefixed search. */
	MOD_PREFIX,
	/** {@code &identifier} prefixed search. */
	ID_PREFIX,
	/** {@code ^color} prefixed search. */
	COLOR_PREFIX,
	/** Exact (full-string) display-name search. */
	EXACT_NAME,
	/** Exclusion ({@code -token}) search. */
	EXCLUSION,
	/** Large-scale performance probe. */
	LARGE_SCALE
}
