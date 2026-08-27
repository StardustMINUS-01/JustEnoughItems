package mezz.jei.test.gui.overlay.elements;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import mezz.jei.gui.bookmarks.chain.RecipeChainItemType;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.ProjectedBookmarkElement;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class ProjectedBookmarkElementTest {
	private static final TestFluidType FLUID_TYPE = new TestFluidType();
	private static final TestItemType ITEM_TYPE = new TestItemType();
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("test", "crafting");
	private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("test", "recipe");
	private static final InputConstants.Key LEFT_MOUSE = InputConstants.Type.MOUSE.getOrCreate(0);
	private static final InputConstants.Key R_KEY = InputConstants.Type.KEYSYM.getOrCreate(82);

	@Test
	public void showUsesTheProjectedIngredientForNormalQueries() {
		AtomicBoolean delegateShowCalled = new AtomicBoolean();
		AtomicBoolean recipesGuiShowCalled = new AtomicBoolean();
		ProjectedBookmarkElement<String> projected = projected(recordingElement(delegateShowCalled), BookmarkItemType.RESULT);
		IRecipesGui recipesGui = proxy(IRecipesGui.class, (proxy, method, args) -> {
			if (method.getName().equals("show")) {
				recipesGuiShowCalled.set(true);
			}
			return null;
		});
		FocusUtil focusUtil = new FocusUtil(null, null, null) {
			@Override
			public List<IFocus<?>> createFocuses(ITypedIngredient<?> ingredient, List<RecipeIngredientRole> roles) {
				return List.of();
			}
		};

		projected.show(recipesGui, focusUtil, List.of(RecipeIngredientRole.OUTPUT));

		Assertions.assertTrue(recipesGuiShowCalled.get());
		Assertions.assertFalse(delegateShowCalled.get());
	}

	@ParameterizedTest
	@MethodSource("exactRecipeActions")
	public void exactRecipeNavigationOnlyHandlesResultLeftClicks(
		BookmarkItemType type,
		InputConstants.Key key,
		boolean expected
	) {
		AtomicBoolean delegateShowCalled = new AtomicBoolean();
		ProjectedBookmarkElement<String> projected = projected(recordingElement(delegateShowCalled), type);

		boolean handled = projected.handleShowRecipeClick(
			new UserInput(key, 0, 0, 0, InputType.EXECUTE),
			keyMappings(),
			null,
			null
		);

		Assertions.assertEquals(expected, handled);
		Assertions.assertEquals(expected, delegateShowCalled.get());
	}

	private static Stream<Arguments> exactRecipeActions() {
		return Stream.of(
			Arguments.of(BookmarkItemType.RESULT, LEFT_MOUSE, true),
			Arguments.of(BookmarkItemType.RESULT, R_KEY, false),
			Arguments.of(BookmarkItemType.INGREDIENT, LEFT_MOUSE, false)
		);
	}

	@Test
	public void fluidTooltipIngredientUsesRecipeMetadataAmount() throws ReflectiveOperationException {
		ITypedIngredient<TestFluid> normalizedFluid = typedFluid(new TestFluid("water", 1000));
		BookmarkDisplayEntry<Object> entry = entry(metadata("water", 144, 3), Optional.empty());

		ITypedIngredient<TestFluid> tooltipIngredient = createTooltipIngredient(
			normalizedFluid,
			new TestFluidHelper(),
			entry
		);

		Assertions.assertEquals(new TestFluid("water", 432), tooltipIngredient.getIngredient());
	}

	@Test
	public void fluidTooltipIngredientUsesCalculatedChainAmount() throws ReflectiveOperationException {
		ITypedIngredient<TestFluid> normalizedFluid = typedFluid(new TestFluid("water", 1000));
		BookmarkItemMetadata metadata = metadata("water", 144, 3);
		RecipeChainItem chainItem = new RecipeChainItem(
			0,
			metadata,
			RecipeChainItemType.INGREDIENT,
			144,
			288,
			576,
			1,
			4
		);
		BookmarkDisplayEntry<Object> entry = entry(metadata, Optional.of(chainItem));

		ITypedIngredient<TestFluid> tooltipIngredient = createTooltipIngredient(
			normalizedFluid,
			new TestFluidHelper(),
			entry
		);

		Assertions.assertEquals(new TestFluid("water", 576), tooltipIngredient.getIngredient());
	}

	@Test
	public void nonFluidTooltipIngredientKeepsNormalizedIngredient() throws ReflectiveOperationException {
		ITypedIngredient<String> normalizedItem = new TestTypedIngredient<>(ITEM_TYPE, "iron_ingot");
		BookmarkDisplayEntry<Object> entry = entry(metadata("iron_ingot", 2, 5), Optional.empty());

		ITypedIngredient<String> tooltipIngredient = createTooltipIngredient(
			normalizedItem,
			new TestItemHelper(),
			entry
		);

		Assertions.assertSame(normalizedItem, tooltipIngredient);
	}

	@SuppressWarnings("unchecked")
	private static <T> ITypedIngredient<T> createTooltipIngredient(
		ITypedIngredient<T> typedIngredient,
		IIngredientHelper<T> ingredientHelper,
		BookmarkDisplayEntry<?> entry
	) throws ReflectiveOperationException {
		Method method = ProjectedBookmarkElement.class.getDeclaredMethod(
			"createTooltipIngredient",
			ITypedIngredient.class,
			IIngredientHelper.class,
			BookmarkDisplayEntry.class
		);
		method.setAccessible(true);
		return (ITypedIngredient<T>) method.invoke(null, typedIngredient, ingredientHelper, entry);
	}

	private static BookmarkDisplayEntry<Object> entry(BookmarkItemMetadata metadata, Optional<RecipeChainItem> chainItem) {
		return new BookmarkDisplayEntry<>(
			new Object(),
			0,
			metadata,
			BookmarkViewMode.DEFAULT,
			Optional.of(RECIPE),
			chainItem,
			false,
			false
		);
	}

	private static BookmarkItemMetadata metadata(String uid, long factor, long multiplier) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.RESULT,
			multiplier,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			RECIPE,
			Set.of(BookmarkIngredientKey.of(FLUID_TYPE.getUid(), uid))
		);
	}

	private static ITypedIngredient<TestFluid> typedFluid(TestFluid fluid) {
		return new TestTypedIngredient<>(FLUID_TYPE, fluid);
	}

	private static ProjectedBookmarkElement<String> projected(IElement<String> delegate, BookmarkItemType type) {
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			RECIPE,
			Set.of()
		);
		BookmarkDisplayEntry<String> entry = new BookmarkDisplayEntry<>(
			"item",
			0,
			metadata,
			BookmarkViewMode.DEFAULT,
			Optional.of(RECIPE),
			Optional.empty(),
			false,
			false
		);
		return new ProjectedBookmarkElement<>(delegate, entry);
	}

	@SuppressWarnings("unchecked")
	private static IElement<String> recordingElement(AtomicBoolean showCalled) {
		return proxy(IElement.class, (proxy, method, args) -> switch (method.getName()) {
			case "getTypedIngredient" -> new TestTypedIngredient<>(ITEM_TYPE, "item");
			case "show" -> {
				showCalled.set(true);
				yield null;
			}
			default -> null;
		});
	}

	private static IInternalKeyMappings keyMappings() {
		IJeiKeyMapping leftClick = proxy(IJeiKeyMapping.class, (proxy, method, args) ->
			method.getName().equals("isActiveAndMatches") && LEFT_MOUSE.equals(args[0])
		);
		IJeiKeyMapping noMatch = proxy(IJeiKeyMapping.class, (proxy, method, args) -> false);
		return proxy(IInternalKeyMappings.class, (proxy, method, args) ->
			method.getName().equals("getLeftClick") ? leftClick : noMatch
		);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
	}

	private record TestTypedIngredient<T>(IIngredientType<T> type, T ingredient) implements ITypedIngredient<T> {
		@Override
		public IIngredientType<T> getType() {
			return type;
		}

		@Override
		public T getIngredient() {
			return ingredient;
		}
	}

	private record TestFluid(String name, long amount) {
	}

	private static class TestFluidType implements IIngredientType<TestFluid> {
		@Override
		public Class<? extends TestFluid> getIngredientClass() {
			return TestFluid.class;
		}

		@Override
		public String getUid() {
			return "fluid_stack";
		}
	}

	private static class TestItemType implements IIngredientType<String> {
		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}

		@Override
		public String getUid() {
			return "minecraft:item_stack";
		}
	}

	private static class TestFluidHelper implements IIngredientHelper<TestFluid> {
		@Override
		public IIngredientType<TestFluid> getIngredientType() {
			return FLUID_TYPE;
		}

		@Override
		public String getDisplayName(TestFluid ingredient) {
			return ingredient.name();
		}

		@Override
		public String getUniqueId(TestFluid ingredient, UidContext context) {
			return ingredient.name();
		}

		@Override
		public ResourceLocation getResourceLocation(TestFluid ingredient) {
			return ResourceLocation.fromNamespaceAndPath("test", ingredient.name());
		}

		@Override
		public TestFluid copyIngredient(TestFluid ingredient) {
			return ingredient;
		}

		@Override
		public TestFluid copyWithAmount(TestFluid ingredient, long amount) {
			return new TestFluid(ingredient.name(), amount);
		}

		@Override
		public String getErrorInfo(@Nullable TestFluid ingredient) {
			return String.valueOf(ingredient);
		}
	}

	private static class TestItemHelper implements IIngredientHelper<String> {
		@Override
		public IIngredientType<String> getIngredientType() {
			return ITEM_TYPE;
		}

		@Override
		public String getDisplayName(String ingredient) {
			return ingredient;
		}

		@Override
		public String getUniqueId(String ingredient, UidContext context) {
			return ingredient;
		}

		@Override
		public ResourceLocation getResourceLocation(String ingredient) {
			return ResourceLocation.fromNamespaceAndPath("test", ingredient);
		}

		@Override
		public String copyIngredient(String ingredient) {
			return ingredient;
		}

		@Override
		public String getErrorInfo(@Nullable String ingredient) {
			return String.valueOf(ingredient);
		}
	}
}
