package mezz.jei.test.bench;

/**
 * The support status of a reference scenario, mirroring the AE2VM
 * {@code ReferenceSupportStatus} taxonomy adapted for JEI search.
 */
public enum JeiSupportStatus {
	/** The engine returned exactly the expected result set. */
	SUPPORTED,
	/** The engine returned the expected results plus unexpected extras. */
	FALSE_POSITIVE,
	/** The engine omitted expected results (or returned a disjoint set). */
	FALSE_NEGATIVE,
	/** The engine threw an exception while evaluating the scenario. */
	ENGINE_ERROR,
	/** The engine exceeded the deadline and could not be interrupted in time. */
	ENGINE_TIMEOUT
}
