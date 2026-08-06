package mezz.jei.gui.overlay.bookmarks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScrollStepTextFieldTest {
	@Test
	public void digitsParseToValue() {
		assertEquals(33, ScrollStepTextField.parse("33"));
	}
}
