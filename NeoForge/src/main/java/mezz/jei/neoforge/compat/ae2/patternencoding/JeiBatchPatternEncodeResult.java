package mezz.jei.neoforge.compat.ae2.patternencoding;

import java.util.List;

public record JeiBatchPatternEncodeResult(
	int encodedCount,
	int skippedExistingCount,
	int skippedInvalidCount,
	JeiPatternEncodeStopReason stopReason,
	List<JeiPatternEncodeEntryResult> entries
) {
	public JeiBatchPatternEncodeResult {
		entries = List.copyOf(entries);
	}

	static JeiBatchPatternEncodeResult fromEntries(JeiPatternEncodeStopReason reason, List<JeiPatternEncodeEntryResult> entries) {
		int encoded = 0;
		int existing = 0;
		int invalid = 0;
		for (var entry : entries) {
			switch (entry.status()) {
				case ENCODED -> encoded++;
				case SKIPPED_EXISTING_PRIMARY_OUTPUT -> existing++;
				case SKIPPED_INVALID_RECIPE, SKIPPED_INVALID_PATTERN, SKIPPED_INVALID_CATALYST -> invalid++;
			}
		}
		return new JeiBatchPatternEncodeResult(encoded, existing, invalid, reason, entries);
	}
}
