package mezz.jei.test.gui.ingredients;

import mezz.jei.common.gui.CandidateTooltipWindow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class CandidateTooltipWindowTest {
	@ParameterizedTest
	@CsvSource({
		"30, 29, 0, 0",
		"31, 29, 0, 1",
		"31, 0, 2, 0",
		"31, 30, 0, 2"
	})
	public void updatesWindowStart(int candidateCount, int selectedIndex, int currentStart, int expectedStart) {
		int start = CandidateTooltipWindow.updateStart(candidateCount, selectedIndex, currentStart);

		Assertions.assertEquals(expectedStart, start);
	}
}
