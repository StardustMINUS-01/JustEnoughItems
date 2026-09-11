package mezz.jei.test.gui.collapsible;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.collapsible.CollapsibleGroup;
import mezz.jei.gui.collapsible.CollapsibleLayout;
import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleState;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import java.util.Set;

import static mezz.jei.test.gui.collapsible.CollapsibleTestFixtures.group;
import static mezz.jei.test.gui.collapsible.CollapsibleTestFixtures.element;

public class CollapsibleLayoutTest {
	@Test
	public void projectsGroupState() {
		CollapsibleGroup potion = group("minecraft:potion");
		CollapsibleGroup splash = group("minecraft:splash_potion");
		CollapsibleRules rules = new CollapsibleRules(List.of(potion, splash));
		CollapsibleState state = new CollapsibleState();

		List<IElement<?>> elements = List.of(
			element("minecraft:potion"),
			element("minecraft:potion"),
			element("minecraft:splash_potion")
		);
		AtomicInteger matches = new AtomicInteger();
		CollapsibleLayout layout = CollapsibleLayout.prepare(elements, rules, ingredient -> {
			matches.incrementAndGet();
			return toInfo(ingredient);
		});
		Result result = render(layout, state);

		Assertions.assertEquals(2, result.elements().size());
		Assertions.assertEquals(2, result.slots().get(result.elements().get(0)).groupSize());
		Assertions.assertFalse(result.autoExpanded());

		state.toggleGroup(potion.id());
		Result expanded = render(layout, state);
		Assertions.assertEquals(3, expanded.elements().size());
		Assertions.assertTrue(expanded.slots().values().stream().allMatch(info -> info.hiddenMembers().isEmpty()));
		state.toggleGroup(potion.id());
		Assertions.assertEquals(result.elements(), render(layout, state).elements());
		Assertions.assertEquals(elements.size(), matches.get());
		var hidden = result.slots().get(elements.getFirst()).hiddenMembers();
		Assertions.assertEquals(List.of(elements.get(1)), hidden);
		Assertions.assertThrows(UnsupportedOperationException.class, () -> hidden.clear());
	}

	@Test
	public void autoExpandsSingleGroup() {
		CollapsibleRules rules = new CollapsibleRules(List.of(group("minecraft:potion")));
		CollapsibleState state = new CollapsibleState();
		List<IElement<?>> elements = List.of(
			element("minecraft:potion"),
			element("minecraft:potion")
		);
		Result result = render(elements, rules, state);
		Assertions.assertEquals(2, result.elements().size());
		Assertions.assertTrue(result.autoExpanded());
		Assertions.assertTrue(result.slots().values().stream().allMatch(CollapsibleLayout.SlotInfo::expanded));
	}

	@Test
	public void omitsSingletonInfo() {
		CollapsibleRules rules = new CollapsibleRules(List.of(group("minecraft:dirt")));
		CollapsibleState state = new CollapsibleState();
		List<IElement<?>> elements = List.of(element("minecraft:dirt"));
		Result result = render(elements, rules, state);
		Assertions.assertEquals(1, result.elements().size());
		Assertions.assertTrue(result.slots().isEmpty());
	}

	@Test
	public void gathersMembers() {
		CollapsibleRules rules = new CollapsibleRules(List.of(group("minecraft:potion")));
		CollapsibleState state = new CollapsibleState();
		List<IElement<?>> elements = List.of(
			element("minecraft:potion"),
			element("minecraft:dirt"),
			element("minecraft:potion"),
			element("minecraft:stone"),
			element("minecraft:potion")
		);
		state.toggleGroup(rules.groups().getFirst().id());
		Result result = render(elements, rules, state);
		Assertions.assertEquals(
			List.of("minecraft:potion", "minecraft:potion", "minecraft:potion", "minecraft:dirt", "minecraft:stone"),
			result.elements().stream()
				.map(e -> (String) e.getTypedIngredient().getIngredient())
				.toList()
		);
	}

	@Test
	public void preservesRuleOrder() {
		var broad = group("minecraft:potion");
		var duplicate = group("minecraft:potion");
		var splash = group("minecraft:splash_potion");
		var state = new CollapsibleState();
		state.toggleGroup(broad.id());
		state.toggleGroup(splash.id());
		var a = element("minecraft:potion");
		var b = element("minecraft:splash_potion");
		var c = element("minecraft:potion");
		var dirt = element("minecraft:dirt");
		var d = element("minecraft:splash_potion");
		var stone = element("minecraft:stone");
		var rules = new CollapsibleRules(List.of(broad, duplicate, splash));
		var result = render(List.of(a, b, c, dirt, d, stone), rules, state);
		Assertions.assertEquals(List.of(a, c, b, d, dirt, stone), result.elements());
		Assertions.assertSame(broad, result.slots().get(a).group());
		Assertions.assertEquals(List.of(dirt, stone), render(List.of(dirt, stone), CollapsibleRules.EMPTY, state).elements());
		Assertions.assertTrue(render(List.of(), rules, state).elements().isEmpty());
	}

	private static Result render(List<IElement<?>> elements, CollapsibleRules rules, CollapsibleState state) {
		return render(CollapsibleLayout.prepare(elements, rules, CollapsibleLayoutTest::toInfo), state);
	}

	private static Result render(CollapsibleLayout layout, CollapsibleState state) {
		Map<IElement<?>, CollapsibleLayout.SlotInfo> info = new IdentityHashMap<>();
		var visible = layout.project(state, (element, slot) -> {
			info.put(element, slot);
			return element;
		});
		return new Result(visible, info, info.values().stream().anyMatch(CollapsibleLayout.SlotInfo::autoExpanded));
	}

	private record Result(List<IElement<?>> elements, Map<IElement<?>, CollapsibleLayout.SlotInfo> slots, boolean autoExpanded) {}

	private static Optional<IngredientMatchInfo> toInfo(ITypedIngredient<?> typed) {
		return Optional.of(IngredientMatchInfo.item(
			ResourceLocation.parse((String) typed.getIngredient()), Set.of()));
	}

}
