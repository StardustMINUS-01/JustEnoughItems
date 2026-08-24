package mezz.jei.test.gui.ingredients;

import mezz.jei.common.gui.CandidateTooltipWindow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CandidateTooltipWindowTest {
	@Test
	public void keepsEveryCandidateVisibleWhenTheyFitInTheTooltip() {
		int start = CandidateTooltipWindow.updateStart(30, 29, 0);

		Assertions.assertEquals(0, start);
	}

	@Test
	public void shiftsForwardWhenTheSelectedCandidateLeavesTheVisibleWindow() {
		int start = CandidateTooltipWindow.updateStart(31, 29, 0);

		Assertions.assertEquals(1, start);
	}

	@Test
	public void shiftsBackwardWhenTheSelectedCandidateLeavesTheVisibleWindow() {
		int start = CandidateTooltipWindow.updateStart(31, 0, 2);

		Assertions.assertEquals(0, start);
	}

	@Test
	public void clampsTheWindowToTheLastCandidate() {
		int start = CandidateTooltipWindow.updateStart(31, 30, 0);

		Assertions.assertEquals(2, start);
	}
}
