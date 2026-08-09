package mezz.jei.neoforge.compat.ae2.patternencoding;

import java.util.List;

public record JeiBatchPatternEncodeResult(
	int encodedCount,
	int skippedExistingCount,
	int skippedInvalidCount,
	int notProcessedCount,
	JeiPatternEncodeStopReason stopReason,
	List<JeiPatternEncodeEntryResult> entries
) {
	public JeiBatchPatternEncodeResult {
		entries = List.copyOf(entries);
	}
}
