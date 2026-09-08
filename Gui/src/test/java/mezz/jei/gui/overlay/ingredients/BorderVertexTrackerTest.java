package mezz.jei.gui.overlay.ingredients;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorderVertexTrackerTest {
	@Test
	void sharedCornersArePaintedOncePerFrameAndResetEachRender() {
		BorderVertexTracker vertices = new BorderVertexTracker();
		assertTrue(vertices.claim("first", 18, 18));
		assertFalse(vertices.claim("first", 18, 18));
		assertTrue(vertices.claim("second", 18, 18));
		assertFalse(vertices.claim("second", 18, 18));
		assertTrue(vertices.claim("first", 36, 18));
		assertTrue(vertices.claim("first", 18, 36));
		vertices.clear();
		assertTrue(vertices.claim("first", 18, 18));
		assertTrue(vertices.claim("second", 18, 18));
	}
}
