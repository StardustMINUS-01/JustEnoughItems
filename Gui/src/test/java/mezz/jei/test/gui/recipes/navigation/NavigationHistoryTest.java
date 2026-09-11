package mezz.jei.test.gui.recipes.navigation;

import mezz.jei.gui.recipes.navigation.RecipeNavigationHistory;
import mezz.jei.gui.recipes.navigation.RecipeNavigationDirection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NavigationHistoryTest {
	@Test
	public void navigatesEntries() {
		RecipeNavigationHistory<String> history = new RecipeNavigationHistory<>();
		history.push("one");
		history.push("two");
		history.push("three");

		Assertions.assertEquals("two", history.navigate(RecipeNavigationDirection.BACK, false).orElseThrow());
		Assertions.assertEquals("one", history.navigate(RecipeNavigationDirection.BACK, false).orElseThrow());
		Assertions.assertFalse(history.canNavigate(RecipeNavigationDirection.BACK));
		Assertions.assertEquals("two", history.navigate(RecipeNavigationDirection.FORWARD, false).orElseThrow());
		Assertions.assertEquals("three", history.navigate(RecipeNavigationDirection.FORWARD, false).orElseThrow());
		Assertions.assertFalse(history.canNavigate(RecipeNavigationDirection.FORWARD));
	}

	@Test
	public void jumpsToEndpoints() {
		RecipeNavigationHistory<String> history = new RecipeNavigationHistory<>();
		history.push("one");
		history.push("two");
		history.push("three");
		history.push("four");

		Assertions.assertEquals("one", history.navigate(RecipeNavigationDirection.BACK, true).orElseThrow());
		Assertions.assertEquals("four", history.navigate(RecipeNavigationDirection.FORWARD, true).orElseThrow());
	}

	@Test
	public void replacesForwardHistory() {
		RecipeNavigationHistory<String> history = new RecipeNavigationHistory<>();
		history.push("one");
		history.push("two");
		history.push("three");
		history.navigate(RecipeNavigationDirection.BACK, false);

		history.push("replacement");

		Assertions.assertFalse(history.canNavigate(RecipeNavigationDirection.FORWARD));
		Assertions.assertEquals("two", history.peek(RecipeNavigationDirection.BACK).orElseThrow());
		Assertions.assertEquals("replacement", history.current().orElseThrow());
	}

	@Test
	public void evictsOldestEntry() {
		RecipeNavigationHistory<Integer> history = new RecipeNavigationHistory<>();
		for (int entry = 0; entry <= RecipeNavigationHistory.DEFAULT_CAPACITY; entry++) {
			history.push(entry);
		}

		Assertions.assertEquals(1, history.navigate(RecipeNavigationDirection.BACK, true).orElseThrow());
		Assertions.assertFalse(history.canNavigate(RecipeNavigationDirection.BACK));
		Assertions.assertEquals(RecipeNavigationHistory.DEFAULT_CAPACITY, history.navigate(RecipeNavigationDirection.FORWARD, true).orElseThrow());
	}

	@Test
	public void clearsHistory() {
		RecipeNavigationHistory<String> history = new RecipeNavigationHistory<>();
		history.push("one");
		history.push("two");

		history.clear();

		Assertions.assertTrue(history.current().isEmpty());
		Assertions.assertTrue(history.peek(RecipeNavigationDirection.BACK).isEmpty());
		Assertions.assertTrue(history.peek(RecipeNavigationDirection.FORWARD).isEmpty());
		Assertions.assertFalse(history.canNavigate(RecipeNavigationDirection.BACK));
		Assertions.assertFalse(history.canNavigate(RecipeNavigationDirection.FORWARD));
	}
}
