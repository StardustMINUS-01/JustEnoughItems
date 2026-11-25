package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.common.transfer.RecipeTransferErrorInternal;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeTransferLayoutTest {
	@BeforeAll
	static void bootStrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void dryRunAndExecutionUseTheirCurrentSelection() {
		var selection = new AtomicReference<IRecipeSlotsView>(new TestSlots());
		var layout = layout();
		var selected = new RecipeTransferLayout<>(layout, selection::get);
		var screen = new TestScreen();
		List<IRecipeSlotsView> slots = new ArrayList<>();
		List<List<Boolean>> modes = new ArrayList<>();
		var service = service(args -> {
			assertSame(screen, args[0]);
			var received = (IRecipeLayoutDrawable<?>) args[1];
			assertSame(layout.getRecipe(), received.getRecipe());
			slots.add(received.getRecipeSlotsView());
			modes.add(List.of((boolean) args[3], (boolean) args[4]));
			return Optional.empty();
		});
		var first = selection.get();
		assertTrue(service.getTransferRecipeError(screen, selected, null).isEmpty());
		selection.set(new TestSlots());
		assertTrue(service.transferRecipe(screen, selected, null, true));
		assertEquals(List.of(first, selection.get()), slots);
		assertEquals(List.of(List.of(false, false), List.of(true, true)), modes);
	}

	@Test
	void rejectedTransferDoesNotRequireTheServiceToEvaluateSelectedSlots() {
		var service = service(args -> Optional.of(RecipeTransferErrorInternal.INSTANCE));
		var selected = new RecipeTransferLayout<>(layout(), () -> {
			throw new AssertionError("Unexpected slot lookup");
		});
		assertSame(RecipeTransferErrorInternal.INSTANCE, service.getTransferRecipeError(new TestScreen(), selected, null).orElseThrow());
	}

	private RecipeTransferService service(Function<Object[], Optional<IRecipeTransferError>> transfer) {
		var manager = (IRecipeTransferManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IRecipeTransferManager.class},
			(proxy, method, args) -> {
				if (!method.getName().equals("transferRecipe")) {
					throw new AssertionError(method.getName());
				}
				return transfer.apply(args);
			});
		return new RecipeTransferService(manager);
	}

	private static IRecipeLayoutDrawable<?> layout() {
		return RecipeLayoutTestFixtures.layout(RecipeType.create("test", "transfer", String.class), "recipe",
			ResourceLocation.fromNamespaceAndPath("test", "recipe"), List.of(), List.of());
	}

	private static final class TestSlots implements IRecipeSlotsView {
		@Override
		public List<mezz.jei.api.gui.ingredient.IRecipeSlotView> getSlotViews() {
			return List.of();
		}
	}

	private static final class TestScreen extends AbstractContainerScreen<AbstractContainerMenu> {
		private TestScreen() {
			super(null, new Inventory(null), Component.empty());
		}

		@Override
		protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		}
	}
}
