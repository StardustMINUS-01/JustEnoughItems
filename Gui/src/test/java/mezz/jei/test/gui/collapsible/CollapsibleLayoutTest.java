package mezz.jei.test.gui.collapsible;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.collapsible.CollapsibleGroup;
import mezz.jei.gui.collapsible.CollapsibleLayout;
import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleState;
import mezz.jei.gui.match.IngredientExpression;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import java.util.Set;

public class CollapsibleLayoutTest {
	@Test
	public void firstOccurrenceFoldsAndExpandedShowsAll() {
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

		Assertions.assertEquals(2, result.visibleElements().size());
		Assertions.assertEquals(2, result.slotInfo().get(result.visibleElements().get(0)).groupSize());
		Assertions.assertFalse(result.autoExpanded());

		state.toggleGroup(potion.id());
		Result expanded = render(layout, state);
		Assertions.assertEquals(3, expanded.visibleElements().size());
		Assertions.assertTrue(expanded.slotInfo().values().stream().allMatch(info -> info.hiddenMembers().isEmpty()));
		state.toggleGroup(potion.id());
		Assertions.assertEquals(result.visibleElements(), render(layout, state).visibleElements());
		Assertions.assertEquals(elements.size(), matches.get());
		var hidden = result.slotInfo().get(elements.getFirst()).hiddenMembers();
		Assertions.assertEquals(List.of(elements.get(1)), hidden);
		Assertions.assertThrows(UnsupportedOperationException.class, () -> hidden.clear());
	}

	@Test
	public void singleGroupAutoExpands() {
		CollapsibleRules rules = new CollapsibleRules(List.of(group("minecraft:potion")));
		CollapsibleState state = new CollapsibleState();
		List<IElement<?>> elements = List.of(
			element("minecraft:potion"),
			element("minecraft:potion")
		);
		Result result = render(
			elements, rules, state);
		Assertions.assertEquals(2, result.visibleElements().size());
		Assertions.assertTrue(result.autoExpanded());
		Assertions.assertTrue(result.slotInfo().values().stream().allMatch(CollapsibleLayout.SlotInfo::expanded));
	}

	@Test
	public void singleMemberGroupHasNoSlotInfo() {
		CollapsibleRules rules = new CollapsibleRules(List.of(group("minecraft:dirt")));
		CollapsibleState state = new CollapsibleState();
		List<IElement<?>> elements = List.of(element("minecraft:dirt"));
		Result result = render(
			elements, rules, state);
		Assertions.assertEquals(1, result.visibleElements().size());
		Assertions.assertTrue(result.slotInfo().isEmpty());
	}

	@Test
	public void scatteredMembersBecomeContiguousWhenExpanded() {
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
		Result result = render(
			elements, rules, state);
		Assertions.assertEquals(
			List.of("minecraft:potion", "minecraft:potion", "minecraft:potion", "minecraft:dirt", "minecraft:stone"),
			result.visibleElements().stream()
				.map(e -> (String) e.getTypedIngredient().getIngredient())
				.toList()
		);
	}

	@Test
	public void overlappingRulesKeepFirstMatchAndInterleavedGroupOrder() {
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
		Assertions.assertEquals(List.of(a, c, b, d, dirt, stone), result.visibleElements());
		Assertions.assertSame(broad, result.slotInfo().get(a).group());
		Assertions.assertEquals(List.of(dirt, stone), render(List.of(dirt, stone), CollapsibleRules.EMPTY, state).visibleElements());
		Assertions.assertTrue(render(List.of(), rules, state).visibleElements().isEmpty());
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

	private record Result(List<IElement<?>> visibleElements, Map<IElement<?>, CollapsibleLayout.SlotInfo> slotInfo, boolean autoExpanded) {}

	private static CollapsibleGroup group(String expr) {
		return CollapsibleGroup.create(expr, IngredientExpression.parseIngredient(expr).orElseThrow());
	}

	private static IElement<?> element(String itemId) {
		ITypedIngredient<Object> typed = new ITypedIngredient<>() {
			@Override
			public ITypedIngredient<Object> normalize(mezz.jei.api.ingredients.IIngredientHelper<Object> helper) {
				return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(getType(), helper.normalizeIngredient(getIngredient()));
			}

			@Override
			public IIngredientType<Object> getType() {
				return TEST_TYPE;
			}

			@Override
			public Object getIngredient() {
				return itemId;
			}

			@Override
			public <V> ITypedIngredient<V> cast(IIngredientType<V> ingredientType) {
				return null;
			}
		};
		return new IngredientElement<>(typed);
	}

	private static Optional<IngredientMatchInfo> toInfo(ITypedIngredient<?> typed) {
		return Optional.of(IngredientMatchInfo.item(
			ResourceLocation.parse((String) typed.getIngredient()), Set.of()));
	}

	private static final IIngredientType<Object> TEST_TYPE = new IIngredientType<>() {
		@Override
		public Class<? extends Object> getIngredientClass() {
			return Object.class;
		}

		@Override
		public String getUid() {
			return "test";
		}
	};
}
