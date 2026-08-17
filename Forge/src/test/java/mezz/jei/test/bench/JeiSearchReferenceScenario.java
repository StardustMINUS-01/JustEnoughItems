package mezz.jei.test.bench;

import java.util.Set;

/**
 * A single reference search scenario, mirroring the AE2VM
 * {@code ReferenceScenario} record adapted for JEI search.
 *
 * @param id          stable identifier, e.g. {@code name/prefix}
 * @param capability  the search capability under test
 * @param dataMode    MISSING / MINIMUM / UNBOUNDED
 * @param scale       number of indexed ingredients (>= 2, base plugin ingredients included)
 * @param query       the search filter text
 * @param expected    the expected matching ingredient numbers
 */
public record JeiSearchReferenceScenario(
	String id,
	JeiSearchCapability capability,
	JeiDataMode dataMode,
	int scale,
	String query,
	Set<Integer> expected
) {
}
