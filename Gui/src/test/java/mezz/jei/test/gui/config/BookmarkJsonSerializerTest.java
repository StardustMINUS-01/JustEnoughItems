package mezz.jei.test.gui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.file.serializers.TypedIngredientSerializer;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.config.BookmarkJsonSerializer;
import mezz.jei.gui.config.file.serializers.RecipeBookmarkSerializer;
import net.minecraft.SharedConstants;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class BookmarkJsonSerializerTest {
	private static final ResourceLocation RECIPE_UID = ResourceLocation.fromNamespaceAndPath("test", "glass");
	private static final RecipeType<Object> RECIPE_TYPE = RecipeType.create("test", "crafting", Object.class);
	private static final IIngredientManager INGREDIENT_MANAGER = ingredientManager();

	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void groupRoundTrips() {
		BookmarkGroup group = new BookmarkGroup(
			"group_1",
			"Machines",
			BookmarkViewMode.TODO_LIST,
			true,
			true,
			Set.of(ResourceLocation.parse("test:plate"))
		);

		BookmarkGroup decoded = BookmarkJsonSerializer.deserializeGroup(
			BookmarkJsonSerializer.serializeGroup(group)
		).orElseThrow();

		Assertions.assertEquals(group, decoded);
	}

	@Test
	public void itemBookmarkRoundTripsThroughList() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		source.addGroupFromConfig(new BookmarkGroup("group_1", "Machines"));
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS, 4));
		IBookmark itemBookmark = IngredientBookmark.create(glass, INGREDIENT_MANAGER);
		source.addToListWithoutNotifying(itemBookmark, false);

		List<JsonElement> elements = BookmarkJsonSerializer.serialize(source, INGREDIENT_MANAGER);
		BookmarkList decoded = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		BookmarkJsonSerializer.deserialize(toArray(elements), decoded, null, INGREDIENT_MANAGER);

		Assertions.assertTrue(decoded.getBookmarkGroups().stream().anyMatch(group -> group.id().equals("group_1")));
		Assertions.assertEquals(1, decoded.getBookmarks().size());
		Assertions.assertTrue(decoded.getBookmarks().getFirst() instanceof IngredientBookmark);
	}

	@Test
	public void recipeBookmarkRoundTripsThroughList() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		source.addGroupFromConfig(new BookmarkGroup("group_1", "Machines"));
		Object recipe = new Object();
		ITypedIngredient<ItemStack> output = typed(new ItemStack(Items.GLASS));
		RecipeBookmark<Object, ItemStack> recipeBookmark = new RecipeBookmark<>(
			new TestRecipeCategory(),
			recipe,
			RECIPE_UID,
			output,
			RecipeIngredientRole.OUTPUT
		);
		source.addToListWithoutNotifying(recipeBookmark, false);

		List<JsonElement> elements = BookmarkJsonSerializer.serialize(source, INGREDIENT_MANAGER);
		BookmarkList decoded = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		BookmarkJsonSerializer.deserialize(toArray(elements), decoded, recipeBookmarkSerializer(recipe), INGREDIENT_MANAGER);

		Assertions.assertEquals(1, decoded.getBookmarks().size());
		Assertions.assertTrue(decoded.getBookmarks().getFirst() instanceof RecipeBookmark);
		Assertions.assertEquals(RECIPE_UID, ((RecipeBookmark<?, ?>) decoded.getBookmarks().getFirst()).getRecipeUid());
	}

	@Test
	public void groupSnapshotImportsWithFreshGroupId() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS, 4));
		String sharedGroupId = source.addRecipeBookmarkGroup(
			"Shared Machines",
			List.of(IngredientBookmark.createWithAmount(glass, 4, INGREDIENT_MANAGER))
		);
		source.setGroupCraftingMode(sharedGroupId, true);

		String snapshot = BookmarkJsonSerializer.serializeGroupSnapshot(source, sharedGroupId, INGREDIENT_MANAGER).orElseThrow();
		BookmarkList decoded = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		decoded.addGroupFromConfig(new BookmarkGroup("group_1", "Existing"));
		decoded.addToListWithoutNotifying(IngredientBookmark.create(typed(new ItemStack(Items.GLASS)), INGREDIENT_MANAGER), false);

		String importedGroupId = BookmarkJsonSerializer.deserializeGroupSnapshot(snapshot, decoded, null, INGREDIENT_MANAGER).orElseThrow();

		Assertions.assertEquals("group_2", importedGroupId);
		BookmarkGroup importedGroup = decoded.getBookmarkGroups().getLast();
		Assertions.assertEquals("Shared Machines", importedGroup.title());
		Assertions.assertTrue(importedGroup.craftingMode());
		Assertions.assertEquals(2, decoded.getBookmarks().size());
	}

	private static JsonArray toArray(List<JsonElement> elements) {
		JsonArray array = new JsonArray();
		elements.forEach(array::add);
		return array;
	}

	private static RecipeBookmarkSerializer recipeBookmarkSerializer(Object recipe) {
		IRecipeLookup<Object> lookup = new IRecipeLookup<>() {
			@Override
			public IRecipeLookup<Object> limitFocus(java.util.Collection<? extends IFocus<?>> focuses) {
				return this;
			}

			@Override
			public IRecipeLookup<Object> includeHidden() {
				return this;
			}

			@Override
			public Stream<Object> get() {
				return Stream.of(recipe);
			}
		};
		IRecipeManager recipeManager = (IRecipeManager) Proxy.newProxyInstance(
			BookmarkJsonSerializerTest.class.getClassLoader(),
			new Class<?>[]{IRecipeManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getRecipeType" -> Optional.of(RECIPE_TYPE);
				case "getRecipeCategory" -> new TestRecipeCategory();
				case "createRecipeLookup" -> lookup;
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
		IFocusFactory focusFactory = (IFocusFactory) Proxy.newProxyInstance(
			BookmarkJsonSerializerTest.class.getClassLoader(),
			new Class<?>[]{IFocusFactory.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "createFocus" -> new TestFocus((ITypedIngredient<?>) args[1]);
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
		return new RecipeBookmarkSerializer(
			recipeManager,
			focusFactory,
			new TypedIngredientSerializer(INGREDIENT_MANAGER)
		);
	}

	private static IIngredientManager ingredientManager() {
		return (IIngredientManager) Proxy.newProxyInstance(
			BookmarkJsonSerializerTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getIngredientHelper" -> itemHelper();
				case "createTypedIngredient" -> Optional.of(typed((ItemStack) args[1]));
				case "normalizeTypedIngredient" -> {
					ItemStack normalized = ((ITypedIngredient<?>) args[0]).getItemStack().orElseThrow().copy();
					normalized.setCount(1);
					yield typed(normalized);
				}
				case "getIngredientTypeForUid" -> "item_stack".equals(args[0]) ?
					Optional.of(VanillaTypes.ITEM_STACK) :
					Optional.empty();
				case "getTypedIngredientByUid" -> Optional.of(typed(
					new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse((String) args[1])))
				));
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
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

	private record TestFocus(ITypedIngredient<?> ingredient) implements IFocus<Object> {
		@Override
		public RecipeIngredientRole getRole() {
			return RecipeIngredientRole.OUTPUT;
		}

		@Override
		@SuppressWarnings("unchecked")
		public ITypedIngredient<Object> getTypedValue() {
			return (ITypedIngredient<Object>) ingredient;
		}

		@Override
		public <T> Optional<IFocus<T>> checkedCast(IIngredientType<T> ingredientType) {
			return Optional.empty();
		}
	}

	private record TestRecipeCategory() implements IRecipeCategory<Object> {
		@Override
		public RecipeType<Object> getRecipeType() {
			return RECIPE_TYPE;
		}

		@Override
		public Component getTitle() {
			return Component.literal("crafting");
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
}
