package mezz.jei.test.gui.bookmarks.tree;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.gui.bookmarks.tree.RecipeTreePreview;
import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TreePreviewTest {
	@Test
	void normalizesPreview() {
		var fixture = new Layout();
		var preview = new RecipeTreePreview(fixture.drawable(), List.of(), ingredientManager(), Map.of());
		assertEquals(4, fixture.x);
		assertEquals(4, fixture.y);
		assertEquals(208, preview.width());
		var area = preview.area(300, 60, 400);
		assertEquals(1, area.scale());
		assertEquals(208, area.width());
		assertEquals(108, area.height());
		preview.tick();
		assertEquals(1, fixture.ticks);
	}

	@Test
	void mapsMouseCoordinates() {
		var fixture = new Layout();
		var preview = new RecipeTreePreview(fixture.drawable(), List.of(), ingredientManager(), Map.of());
		var area = preview.area(300, -10, 104);
		assertEquals(0.5, area.scale());
		assertEquals(54, area.height());
		preview.slotAt(area, 325, 10);
		assertEquals(50, fixture.mouseX);
		assertEquals(40, fixture.mouseY);
		assertEquals(1, fixture.hitTests);
		preview.slotAt(area, 299, 10);
		preview.slotAt(area, 404, 10);
		preview.slotAt(area, 325, 44);
		assertEquals(1, fixture.hitTests, "Outside the recipe must not query slots");
	}

	private static class Layout {
		int x, y, ticks, hitTests;
		double mouseX, mouseY;

		IRecipeLayoutDrawable<?> drawable() {
			return (IRecipeLayoutDrawable<?>) Proxy.newProxyInstance(IRecipeLayoutDrawable.class.getClassLoader(), new Class<?>[]{IRecipeLayoutDrawable.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getRecipeSlotsView" -> (IRecipeSlotsView) List::of;
					case "getRectWithBorder" -> new Rect2i(x - 4, y - 4, 208, 108);
					case "setPosition" -> {
						x = (int) args[0];
						y = (int) args[1];
						yield null;
					}
					case "tick" -> {
						ticks++;
						yield null;
					}
					case "getSlotUnderMouse" -> {
						mouseX = (double) args[0];
						mouseY = (double) args[1];
						hitTests++;
						yield Optional.empty();
					}
					default -> throw new AssertionError("Unexpected layout call: " + method.getName());
				});
		}
	}
}
