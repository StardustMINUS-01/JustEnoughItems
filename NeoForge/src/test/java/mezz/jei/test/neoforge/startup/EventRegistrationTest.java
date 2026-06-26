package mezz.jei.test.neoforge.startup;

import mezz.jei.neoforge.startup.EventRegistration;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventRegistrationTest {
	@Test
	@SuppressWarnings("removal")
	public void guiRenderEventsIncludeBackgroundLayerForOverlayRenderingBeforeTooltips() {
		assertTrue(EventRegistration.getGuiRenderEventTypes().contains(ScreenEvent.BackgroundRendered.class));
	}
}
