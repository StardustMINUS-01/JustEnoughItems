package mezz.jei.test.gui.overlay.elements;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import mezz.jei.gui.bookmarks.chain.RecipeChainItemType;
import mezz.jei.gui.overlay.elements.ProjectedBookmarkElement;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

public class ProjectedBookmarkElementTest {
	private static final TestFluidType FLUID_TYPE = new TestFluidType();
	private static final TestItemType ITEM_TYPE = new TestItemType();
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("test", "crafting");
	private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("test", "recipe");

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
			false,
			false,
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
