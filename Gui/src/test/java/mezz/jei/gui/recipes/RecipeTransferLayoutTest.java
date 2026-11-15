package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferListener;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
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
	void selectedSlotsReachTheHandlerAndLifecycleWithoutDuplicateTransfers() {
		List<String> calls = new ArrayList<>();
		List<IRecipeTransferContext<?, ?>> contexts = new ArrayList<>();
		IRecipeTransferListener listener = new IRecipeTransferListener() {
			@Override
			public void beforeRecipeTransfer(IRecipeTransferContext<?, ?> context) {
				calls.add("before");
				contexts.add(context);
			}

			@Override
			public void afterRecipeTransfer(IRecipeTransferContext<?, ?> context, RecipeTransferResult result) {
				assertEquals(RecipeTransferResult.SUCCESS, result);
				assertSame(contexts.getLast(), context);
				calls.add("after");
			}
		};
		IRecipeTransferHandler<?, ?> handler = (IRecipeTransferHandler<?, ?>) Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{IRecipeTransferHandler.class}, (proxy, method, args) -> {
				if (!method.getName().equals("transferRecipe") || args.length != 2) {
					throw new AssertionError(method.getName());
				}
				IRecipeTransferContext<?, ?> context = (IRecipeTransferContext<?, ?>) args[0];
				calls.add((boolean) args[1] ? "transfer" : "check");
				if ((boolean) args[1]) {
					assertSame(contexts.getLast(), context);
					assertTrue(context.isMaxTransfer());
					context.completeRecipeTransfer(RecipeTransferResult.SUCCESS);
				} else {
					contexts.add(context);
				}
				return null;
			});
		RecipeTransferService service = new RecipeTransferService(manager(Optional.of(handler), List.of(listener)));
		IRecipeLayoutDrawable<?> layout = layout();
		IRecipeSlotsView first = new TestSlots();
		IRecipeSlotsView second = new TestSlots();
		AtomicReference<IRecipeSlotsView> selection = new AtomicReference<>(first);
		RecipeTransferLayout<?> selectedLayout = new RecipeTransferLayout<>(layout, selection::get);
		TestScreen screen = new TestScreen();

		assertTrue(service.getTransferRecipeError(screen, selectedLayout, null).isEmpty());
		assertEquals(List.of("check"), calls);
		assertSame(first, contexts.getFirst().getRecipeSlots());
		selection.set(second);
		assertTrue(service.transferRecipe(screen, selectedLayout, null, true));
		assertEquals(List.of("check", "before", "transfer", "after"), calls);
		assertSame(second, contexts.getLast().getRecipeSlots());
		assertSame(layout.getRecipe(), contexts.getLast().getRecipe());
		assertSame(screen, contexts.getLast().getScreen());
	}

	@Test
	void missingHandlerDoesNotEvaluateSelectedSlots() {
		RecipeTransferService service = new RecipeTransferService(manager(Optional.empty(), List.of()));
		RecipeTransferLayout<?> selectedLayout = new RecipeTransferLayout<>(layout(), () -> {
			throw new AssertionError("Slots should only be read after finding a transfer handler");
		});
		assertTrue(service.getTransferRecipeError(new TestScreen(), selectedLayout, null).isPresent());
	}

	private IRecipeTransferManager manager(Optional<IRecipeTransferHandler<?, ?>> handler, List<IRecipeTransferListener> listeners) {
		return (IRecipeTransferManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IRecipeTransferManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getRecipeTransferHandler" -> handler;
				case "getRecipeTransferListeners" -> listeners;
				default -> throw new AssertionError(method.getName());
			});
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
