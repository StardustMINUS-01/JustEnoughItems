package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedRecipeLayoutResolverTest {
	@Test
	void legacySyntheticAliasNeverOverridesAnExactDisplayId() {
		ResourceLocation oldId = ResourceLocation.parse("test:legacy");
		ResourceLocation syntheticId = oldId.withPrefix("/");
		RecipeType<ResourceLocation> type = RecipeType.create("test", "category", ResourceLocation.class);
		AtomicReference<List<ResourceLocation>> candidates = new AtomicReference<>(List.of(syntheticId, oldId));
		IRecipeCategory<ResourceLocation> category = proxy(IRecipeCategory.class, (method, args) -> switch (method) {
			case "getRecipeType" -> type;
			case "getRegistryName" -> args[0];
			default -> throw new AssertionError(method);
		});
		IRecipeLookup<ResourceLocation> lookup = new IRecipeLookup<>() {
			@Override
			public IRecipeLookup<ResourceLocation> limitFocus(Collection<? extends IFocus<?>> focuses) { return this; }
			@Override
			public IRecipeLookup<ResourceLocation> includeHidden() { return this; }
			@Override
			public Stream<ResourceLocation> get() { return candidates.get().stream(); }
		};
		IRecipeManager manager = proxy(IRecipeManager.class, (method, args) -> switch (method) {
			case "getRecipeType" -> Optional.of(type);
			case "getRecipeCategory" -> category;
			case "createRecipeLookup" -> lookup;
			case "createRecipeLayoutDrawable" -> Optional.of(proxy(IRecipeLayoutDrawable.class, (layoutMethod, ignored) -> switch (layoutMethod) {
				case "getRecipe" -> args[1];
				default -> throw new AssertionError(layoutMethod);
			}));
			default -> throw new AssertionError(method);
		});
		IFocusGroup focuses = proxy(IFocusGroup.class, (method, args) -> List.of());
		FocusedRecipeLayoutResolver resolver = new FocusedRecipeLayoutResolver(manager);
		FocusedRecipe oldReference = new FocusedRecipe(type.getUid(), oldId);
		assertEquals(oldId, resolver.resolve(oldReference, focuses).orElseThrow().getRecipe());
		candidates.set(List.of(syntheticId));
		assertEquals(syntheticId, resolver.resolve(oldReference, focuses).orElseThrow().getRecipe());
		assertEquals(syntheticId, resolver.resolve(new FocusedRecipe(type.getUid(), syntheticId), focuses).orElseThrow().getRecipe());
		assertTrue(resolver.resolve(new FocusedRecipe(type.getUid(), ResourceLocation.parse("test:missing")), focuses).isEmpty());
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> handler.apply(method.getName(), args));
	}
}
