package mezz.jei.test.gui.collapsible;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkSlotBorder;
import mezz.jei.gui.collapsible.CollapsedGroupElement;
import mezz.jei.gui.collapsible.CollapsibleGroup;
import mezz.jei.gui.collapsible.CollapsibleSettings;
import mezz.jei.gui.collapsible.CollapsibleSlotVisualsProvider;
import mezz.jei.gui.match.IngredientExpression;
import mezz.jei.gui.overlay.IngredientListSlotContext;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisuals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CollapsibleSlotVisualsProviderTest {
	private static final int SLOT_SIZE = 18;
	private static final int COLUMNS = 9;
	private static final int ROWS = 3;
	private static final int GROUP_SIZE = COLUMNS * ROWS - 10;

	@Test
	public void lShapedBlockedGridKeepsBorderOnGroupOutline() {
		CollapsibleGroup group = group("minecraft:potion");
		List<IngredientListSlot> slots = new ArrayList<>();
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLUMNS; col++) {
				IngredientListSlot slot = new IngredientListSlot(
					col * SLOT_SIZE,
					row * SLOT_SIZE,
					SLOT_SIZE,
					SLOT_SIZE,
					1
				);
				boolean blocked = row < 2 && col >= 4;
				slot.setBlocked(blocked);
				if (!blocked) {
					slot.setElement(groupElement(group));
				}
				slots.add(slot);
			}
		}
		CollapsibleSlotVisualsProvider provider = new CollapsibleSlotVisualsProvider(
			() -> slots,
			() -> CollapsibleSettings.DEFAULT
		);

		assertBorder(provider, slots, 0, true, false, true, false);
		assertBorder(provider, slots, 3, false, true, true, false);
		assertBorder(provider, slots, 9, true, false, false, false);
		assertBorder(provider, slots, 12, false, true, false, false);
		assertBorder(provider, slots, 18, true, false, false, true);
		assertBorder(provider, slots, 23, false, false, true, true);
		assertBorder(provider, slots, 26, false, true, true, true);
	}

	private static void assertBorder(
		CollapsibleSlotVisualsProvider provider,
		List<IngredientListSlot> slots,
		int index,
		boolean left,
		boolean right,
		boolean top,
		boolean bottom
	) {
		IngredientListSlot slot = slots.get(index);
		IngredientListSlotContext context = new IngredientListSlotContext(
			slot.getElement(),
			Optional.empty(),
			index,
			-1,
			slot.getArea().getY(),
			-1,
			COLUMNS,
			slots.size(),
			1
		);
		BookmarkSlotBorder border = provider.apply(context)
			.flatMap(BookmarkSlotVisuals::border)
			.orElseThrow();
		Assertions.assertEquals(left, border.left(), "left of slot " + index);
		Assertions.assertEquals(right, border.right(), "right of slot " + index);
		Assertions.assertEquals(top, border.top(), "top of slot " + index);
		Assertions.assertEquals(bottom, border.bottom(), "bottom of slot " + index);
	}

	private static IElement<?> groupElement(CollapsibleGroup group) {
		return new CollapsedGroupElement<>(
			element("minecraft:potion"),
			group,
			null,
			true,
			true,
			GROUP_SIZE,
			List.of()
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
