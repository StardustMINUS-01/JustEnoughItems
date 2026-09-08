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
		CollapsibleLayout.LayoutResult result = CollapsibleLayout.compute(
			elements, rules, state, (members, group) -> members.getFirst(),
			CollapsibleLayoutTest::toInfo);

		Assertions.assertEquals(2, result.visibleElements().size());
		Assertions.assertEquals(2, result.slotInfo().get(result.visibleElements().get(0)).groupSize());
		Assertions.assertFalse(result.autoExpanded());

		state.toggleGroup(potion.id());
		CollapsibleLayout.LayoutResult expanded = CollapsibleLayout.compute(
			elements, rules, state, (members, group) -> members.getFirst(),
			CollapsibleLayoutTest::toInfo);
		Assertions.assertEquals(3, expanded.visibleElements().size());
	}

	@Test
	public void singleGroupAutoExpands() {
		CollapsibleRules rules = new CollapsibleRules(List.of(group("minecraft:potion")));
		CollapsibleState state = new CollapsibleState();
		List<IElement<?>> elements = List.of(
			element("minecraft:potion"),
			element("minecraft:potion")
		);
		CollapsibleLayout.LayoutResult result = CollapsibleLayout.compute(
			elements, rules, state, (members, group) -> members.getFirst(),
			CollapsibleLayoutTest::toInfo);
		Assertions.assertEquals(2, result.visibleElements().size());
		Assertions.assertTrue(result.autoExpanded());
		Assertions.assertTrue(result.slotInfo().values().stream().allMatch(CollapsibleLayout.SlotInfo::expanded));
	}

	@Test
	public void singleMemberGroupHasNoSlotInfo() {
		CollapsibleRules rules = new CollapsibleRules(List.of(group("minecraft:dirt")));
		CollapsibleState state = new CollapsibleState();
		List<IElement<?>> elements = List.of(element("minecraft:dirt"));
		CollapsibleLayout.LayoutResult result = CollapsibleLayout.compute(
			elements, rules, state, (members, group) -> members.getFirst(),
			CollapsibleLayoutTest::toInfo);
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
		CollapsibleLayout.LayoutResult result = CollapsibleLayout.compute(
			elements, rules, state, (members, group) -> members.getFirst(),
			CollapsibleLayoutTest::toInfo);
		Assertions.assertEquals(
			List.of("minecraft:potion", "minecraft:potion", "minecraft:potion", "minecraft:dirt", "minecraft:stone"),
			result.visibleElements().stream()
				.map(e -> (String) e.getTypedIngredient().getIngredient())
				.toList()
		);
	}

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
