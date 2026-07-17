package mezz.jei.test.gui.bookmarks;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class BookmarkListVirtualCircuitTest {
	private static final ResourceLocation RECIPE_UID = ResourceLocation.fromNamespaceAndPath("test", "assembler");
	private static final IIngredientType<IdentityIngredient> IDENTITY_INGREDIENT_TYPE = () -> IdentityIngredient.class;
	private static final IIngredientManager INGREDIENT_MANAGER = ingredientManager();

	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void recipeBookmarksIncludeZeroCostGtmVirtualCircuitInput() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		TestRecipeLayout layout = layout(
			new GTRecipe(7),
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, false));

		IBookmark circuitBookmark = bookmarks.getBookmarks()
			.stream()
			.filter(bookmark -> bookmark.getElement()
				.getTypedIngredient()
				.getItemStack()
				.map(stack -> stack.is(Items.REPEATER))
				.orElse(false))
			.findFirst()
			.orElseThrow();
		BookmarkItemMetadata metadata = bookmarks.getBookmarkMetadata(circuitBookmark);
		Assertions.assertEquals(0, metadata.factor());
		Assertions.assertEquals(0, metadata.amount());
		Assertions.assertEquals(RECIPE_UID, metadata.recipeUid());
		Assertions.assertEquals(mezz.jei.gui.bookmarks.BookmarkItemType.CATALYST, metadata.type());
	}

	@Test
	public void recipeBookmarksMarkGtmNonConsumableInputsAsCatalysts() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		ItemStack mold = new ItemStack(Items.SHEARS, 3);
		TestRecipeLayout layout = layout(
			new GTRecipe(7, mold),
			List.of(item(Items.IRON_INGOT), typed(mold)),
			List.of(item(Items.GOLD_INGOT))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, true));

		BookmarkItemMetadata metadata = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.SHEARS))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(mezz.jei.gui.bookmarks.BookmarkItemType.CATALYST, metadata.type());
	}

	@Test
	public void syntheticGtmNonConsumableCatalystKeepsItsRawAmount() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		ItemStack mold = new ItemStack(Items.SHEARS, 3);
		TestRecipeLayout layout = layout(
			new GTRecipe(7, mold),
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, true));

		BookmarkItemMetadata metadata = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.SHEARS))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(mezz.jei.gui.bookmarks.BookmarkItemType.CATALYST, metadata.type());
		Assertions.assertEquals(3, metadata.factor());
		Assertions.assertEquals(3, metadata.amount());
	}

	@Test
	public void recipeBookmarksKeepDistinctGtmCatalystsVisible() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		ItemStack firstCatalyst = new ItemStack(Items.SHEARS, 3);
		ItemStack secondCatalyst = new ItemStack(Items.FLINT_AND_STEEL, 2);
		TestRecipeLayout layout = layout(
			new GTRecipe(7, List.of(firstCatalyst, secondCatalyst)),
			List.of(item(Items.IRON_INGOT), typed(firstCatalyst), typed(secondCatalyst)),
			List.of(item(Items.GOLD_INGOT))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, true));

		List<BookmarkItemMetadata> catalysts = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.SHEARS) || stack.is(Items.FLINT_AND_STEEL))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.toList();
		Assertions.assertEquals(2, catalysts.size());
		Assertions.assertTrue(catalysts.stream().allMatch(metadata -> metadata.type() == mezz.jei.gui.bookmarks.BookmarkItemType.CATALYST));
	}

	@Test
	public void repeatedRecipeBookmarkingDeduplicatesIdentityOnlyIngredients() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		TestRecipeLayout layout = layout(
			new Object(),
			List.of(identity("styrene_butadiene_rubber")),
			List.of(item(Items.GOLD_INGOT))
		);

		bookmarks.addRecipeBookmarks(layout, false);
		bookmarks.addRecipeBookmarks(layout, false);

		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
	}

	private static TestRecipeLayout layout(
		Object recipe,
		List<@Nullable ITypedIngredient<?>> inputs,
		List<@Nullable ITypedIngredient<?>> outputs
	) {
		return new TestRecipeLayout(new TestRecipeCategory(), recipe, inputs, outputs);
	}

	private static ITypedIngredient<ItemStack> item(net.minecraft.world.level.ItemLike item) {
		return typed(new ItemStack(item));
	}

	private static ITypedIngredient<ItemStack> typed(ItemStack stack) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<ItemStack> getType() {
				return VanillaTypes.ITEM_STACK;
			}

			@Override
			public ItemStack getIngredient() {
				return stack;
			}
		};
	}

	private static ITypedIngredient<IdentityIngredient> identity(String id) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<IdentityIngredient> getType() {
				return IDENTITY_INGREDIENT_TYPE;
			}

			@Override
			public IdentityIngredient getIngredient() {
				return new IdentityIngredient(id);
			}
		};
	}

	private static IIngredientManager ingredientManager() {
		return (IIngredientManager) Proxy.newProxyInstance(
			BookmarkListVirtualCircuitTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> {
				if ("getIngredientHelper".equals(method.getName()) && args[0] == VanillaTypes.ITEM_STACK) {
					return itemHelper();
				}
				if ("getIngredientHelper".equals(method.getName()) && args[0] == IDENTITY_INGREDIENT_TYPE) {
					return identityHelper();
				}
				if ("createTypedIngredient".equals(method.getName()) && args[0] == VanillaTypes.ITEM_STACK) {
					return Optional.of(typed((ItemStack) args[1]));
				}
				if ("normalizeTypedIngredient".equals(method.getName())) {
					return args[0];
				}
				throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static IIngredientHelper<IdentityIngredient> identityHelper() {
		return new IIngredientHelper<>() {
			@Override
			public IIngredientType<IdentityIngredient> getIngredientType() {
				return IDENTITY_INGREDIENT_TYPE;
			}

			@Override
			public String getDisplayName(IdentityIngredient ingredient) {
				return ingredient.id;
			}

			@Override
			public String getUniqueId(IdentityIngredient ingredient, UidContext context) {
				return ingredient.id;
			}

			@Override
			public ResourceLocation getResourceLocation(IdentityIngredient ingredient) {
				return ResourceLocation.fromNamespaceAndPath("test", ingredient.id);
			}

			@Override
			public IdentityIngredient copyIngredient(IdentityIngredient ingredient) {
				return new IdentityIngredient(ingredient.id);
			}

			@Override
			public String getErrorInfo(IdentityIngredient ingredient) {
				return ingredient.id;
			}
		};
	}

	private static final class IdentityIngredient {
		private final String id;

		private IdentityIngredient(String id) {
			this.id = id;
		}
	}

	private static IIngredientHelper<ItemStack> itemHelper() {
		return new IIngredientHelper<>() {
			@Override
			public IIngredientType<ItemStack> getIngredientType() {
				return VanillaTypes.ITEM_STACK;
			}

			@Override
			public String getDisplayName(ItemStack ingredient) {
				return ingredient.getHoverName().getString();
			}

			@Override
			public String getUniqueId(ItemStack ingredient, UidContext context) {
				ResourceLocation key = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
				return key == null ? "minecraft:air" : key.toString();
			}

			@Override
			public ResourceLocation getResourceLocation(ItemStack ingredient) {
				return BuiltInRegistries.ITEM.getKey(ingredient.getItem());
			}

			@Override
			public long getAmount(ItemStack ingredient) {
				return ingredient.getCount();
			}

			@Override
			public ItemStack copyIngredient(ItemStack ingredient) {
				return ingredient.copy();
			}

			@Override
			public String getErrorInfo(ItemStack ingredient) {
				return ingredient.toString();
			}
		};
	}

	private record TestRecipeCategory() implements IRecipeCategory<Object> {
		@Override
		public RecipeType<Object> getRecipeType() {
			return RecipeType.create("gtceu", "assembler", Object.class);
		}

		@Override
		public Component getTitle() {
			return Component.literal("assembler");
		}

		@Override
		public @Nullable mezz.jei.api.gui.drawable.IDrawable getIcon() {
			return null;
		}

		@Override
		public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder, Object recipe, IFocusGroup focuses) {
		}

		@Override
		public @Nullable ResourceLocation getRegistryName(Object recipe) {
			return RECIPE_UID;
		}
	}

	private record TestRecipeLayout(
		TestRecipeCategory category,
		Object recipe,
		List<@Nullable ITypedIngredient<?>> inputs,
		List<@Nullable ITypedIngredient<?>> outputs
	) implements IRecipeLayoutDrawable<Object> {
		@Override
		public void setPosition(int posX, int posY) {
		}

		@Override
		public void drawRecipe(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
		}

		@Override
		public void drawOverlays(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return true;
		}

		@Override
		public <T> Optional<T> getIngredientUnderMouse(int mouseX, int mouseY, IIngredientType<T> ingredientType) {
			return Optional.empty();
		}

		@Override
		public Optional<IRecipeSlotDrawable> getRecipeSlotUnderMouse(double mouseX, double mouseY) {
			return Optional.empty();
		}

		@Override
		public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
			return Optional.empty();
		}

		@Override
		public Rect2i getRect() {
			return new Rect2i(0, 0, 1, 1);
		}

		@Override
		public Rect2i getRectWithBorder() {
			return getRect();
		}

		@Override
		public Rect2i getSideButtonArea(int buttonIndex) {
			return getRect();
		}

		@Override
		public IRecipeSlotsView getRecipeSlotsView() {
			return () -> Stream.concat(
					inputs.stream().map(i -> new TestRecipeSlotView(RecipeIngredientRole.INPUT, i)),
					outputs.stream().map(i -> new TestRecipeSlotView(RecipeIngredientRole.OUTPUT, i))
				)
				.map(IRecipeSlotView.class::cast)
				.toList();
		}

		@Override
		public IRecipeCategory<Object> getRecipeCategory() {
			return category;
		}

		@Override
		public Object getRecipe() {
			return recipe;
		}

		@Override
		public IJeiInputHandler getInputHandler() {
			return () -> ScreenRectangle.empty();
		}

		@Override
		public void tick() {
		}
	}

	private record TestRecipeSlotView(RecipeIngredientRole role, @Nullable ITypedIngredient<?> ingredient) implements IRecipeSlotView {
		@Override
		public Stream<ITypedIngredient<?>> getAllIngredients() {
			return ingredient == null ? Stream.empty() : Stream.of(ingredient);
		}

		@Override
		public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
			return Collections.singletonList(ingredient);
		}

		@Override
		public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
			return Optional.ofNullable(ingredient);
		}

		@Override
		public RecipeIngredientRole getRole() {
			return role;
		}

		@Override
		public void drawHighlight(net.minecraft.client.gui.GuiGraphics guiGraphics, int color) {
		}

		@Override
		public Optional<String> getSlotName() {
			return Optional.empty();
		}
	}
}
