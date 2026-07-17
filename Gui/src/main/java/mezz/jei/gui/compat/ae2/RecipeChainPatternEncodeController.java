package mezz.jei.gui.compat.ae2;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.input.UserInput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RecipeChainPatternEncodeController {
	private RecipeChainPatternEncodeController() {
	}

	public static Optional<HandleResult> handle(
		UserInput input,
		IJeiKeyMapping keyMapping,
		AbstractContainerMenu menu,
		Optional<String> groupId,
		boolean craftingMode,
		Ae2RecipeChainPatternEncodingBridge bridge,
		Supplier<List<RecipeChainInput>> chainInputs,
		Supplier<Set<ResourceLocation>> collapsedRecipeIds,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> layoutResolver,
		IIngredientManager ingredientManager,
		Consumer<Component> messageSink
	) {
		return handle(
			input,
			keyMapping,
			menu,
			groupId,
			craftingMode,
			false,
			bridge,
			chainInputs,
			collapsedRecipeIds,
			layoutResolver,
			ingredientManager,
			messageSink
		);
	}

	public static Optional<HandleResult> handle(
		UserInput input,
		IJeiKeyMapping keyMapping,
		AbstractContainerMenu menu,
		Optional<String> groupId,
		boolean craftingMode,
		boolean allowAnyGroupMode,
		Ae2RecipeChainPatternEncodingBridge bridge,
		Supplier<List<RecipeChainInput>> chainInputs,
		Supplier<Set<ResourceLocation>> collapsedRecipeIds,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> layoutResolver,
		IIngredientManager ingredientManager,
		Consumer<Component> messageSink
	) {
		if (!input.is(keyMapping) || !bridge.isAvailable() || !bridge.isPatternEncodingTerminal(menu) || groupId.isEmpty() || (!craftingMode && !allowAnyGroupMode)) {
			return Optional.empty();
		}
		if (input.isSimulate()) {
			return Optional.of(new HandleResult(true, false));
		}
		RecipeChainPatternEncodeRequestFactory.Result result = new RecipeChainPatternEncodeRequestFactory(ingredientManager)
			.createRequests(chainInputs.get(), collapsedRecipeIds.get(), layoutResolver);
		if (result.status() == RecipeChainPatternEncodeRequestFactory.Status.TOO_MANY_REQUESTS) {
			messageSink.accept(Component.translatable("jei.message.ae2.pattern_encoding.too_many_requests", RecipeChainPatternEncodeRequestFactory.MAX_REQUESTS));
			return Optional.of(new HandleResult(true, false));
		}
		if (result.requests().isEmpty()) {
			messageSink.accept(Component.translatable("jei.message.ae2.pattern_encoding.no_requests"));
			return Optional.of(new HandleResult(true, false));
		}
		boolean sent = bridge.sendRequests(menu, result.requests());
		return Optional.of(new HandleResult(true, sent));
	}

	public static Optional<HandleResult> handleSingleRecipe(
		UserInput input,
		IJeiKeyMapping keyMapping,
		AbstractContainerMenu menu,
		Ae2RecipeChainPatternEncodingBridge bridge,
		Optional<IRecipeLayoutDrawable<?>> layout,
		Optional<IRecipeSlotView> hoveredSlot,
		IIngredientManager ingredientManager,
		Consumer<Component> messageSink
	) {
		return handleSingleRecipe(
			input,
			keyMapping,
			menu,
			bridge,
			layout,
			hoveredSlot,
			List::of,
			ingredientManager,
			messageSink
		);
	}

	public static Optional<HandleResult> handleSingleRecipe(
		UserInput input,
		IJeiKeyMapping keyMapping,
		AbstractContainerMenu menu,
		Ae2RecipeChainPatternEncodingBridge bridge,
		Optional<IRecipeLayoutDrawable<?>> layout,
		Optional<IRecipeSlotView> hoveredSlot,
		Supplier<List<RecipeChainInput>> bookmarkInputs,
		IIngredientManager ingredientManager,
		Consumer<Component> messageSink
	) {
		if (!input.is(keyMapping) || !bridge.isAvailable() || !bridge.isPatternEncodingTerminal(menu) || layout.isEmpty()) {
			return Optional.empty();
		}
		if (input.isSimulate()) {
			return Optional.of(new HandleResult(true, false));
		}
		RecipeChainPatternEncodeRequestFactory.Result result = new RecipeChainPatternEncodeRequestFactory(ingredientManager)
			.createSingleRequest(layout.get(), hoveredSlot, bookmarkInputs.get());
		if (result.requests().isEmpty()) {
			messageSink.accept(Component.translatable("jei.message.ae2.pattern_encoding.no_recipe_request"));
			return Optional.of(new HandleResult(true, false));
		}
		boolean sent = bridge.sendRequests(menu, result.requests());
		return Optional.of(new HandleResult(true, sent));
	}

	public record HandleResult(boolean handled, boolean sent) {
	}
}
