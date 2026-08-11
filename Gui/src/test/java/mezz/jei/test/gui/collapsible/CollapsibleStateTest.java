package mezz.jei.test.gui.collapsible;

import mezz.jei.gui.collapsible.CollapsibleGroup;
import mezz.jei.gui.collapsible.CollapsibleState;
import mezz.jei.gui.match.IngredientExpression;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CollapsibleStateTest {
	@Test
	public void toggleAndOverrideSemantics() {
		CollapsibleState state = new CollapsibleState();
		String id = "group-1";
		Assertions.assertFalse(state.isExpanded(id));
		state.toggleGroup(id);
		Assertions.assertTrue(state.isExpanded(id));
		Assertions.assertEquals(Map.of(id, true), state.toMap());
		state.toggleGroup(id);
		Assertions.assertFalse(state.isExpanded(id));
		Assertions.assertTrue(state.toMap().isEmpty());
	}

	@Test
	public void toggleAllExpandsOrCollapsesEveryGroup() {
		CollapsibleState state = new CollapsibleState();
		List<CollapsibleGroup> groups = List.of(group("minecraft:potion"), group("minecraft:splash_potion"));
		state.toggleAll(groups, null);
		Assertions.assertTrue(groups.stream().allMatch(g -> state.isExpanded(g.id())));
		state.toggleAll(groups, null);
		Assertions.assertTrue(groups.stream().noneMatch(g -> state.isExpanded(g.id())));
	}

	@Test
	public void pruneRemovesStaleIdsAndListenerFiresOnce() {
		CollapsibleState state = new CollapsibleState();
		CollapsibleGroup old = group("minecraft:old");
		state.toggleGroup(old.id());
		AtomicInteger fires = new AtomicInteger();
		state.addListener(fires::incrementAndGet);
		state.prune(List.of(group("minecraft:new")));
		Assertions.assertEquals(1, fires.get());
		Assertions.assertTrue(state.toMap().isEmpty());
	}

	private static CollapsibleGroup group(String expr) {
		return CollapsibleGroup.create(expr, IngredientExpression.parseIngredient(expr).orElseThrow());
	}
}
