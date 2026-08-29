package mezz.jei.test.gui.bookmarks;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures.TestRecipeLayout;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.typed;

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
			List.of(List.of(item(Items.IRON_INGOT))),
			List.of(List.of(item(Items.GOLD_INGOT)))
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
			List.of(List.of(item(Items.IRON_INGOT)), List.of(typed(mold))),
			List.of(List.of(item(Items.GOLD_INGOT)))
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
			List.of(List.of(item(Items.IRON_INGOT))),
			List.of(List.of(item(Items.GOLD_INGOT)))
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
			List.of(
				List.of(item(Items.IRON_INGOT)),
				List.of(typed(firstCatalyst)),
				List.of(typed(secondCatalyst))
			),
			List.of(List.of(item(Items.GOLD_INGOT)))
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
			List.of(List.of(identity("styrene_butadiene_rubber"))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);

		bookmarks.addRecipeBookmarks(layout, false);
		bookmarks.addRecipeBookmarks(layout, false);

		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
	}

	@Test
	public void projectionLocksSelectedInputPermutation() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		TestRecipeLayout layout = layout(
			new Object(),
			List.of(List.of(item(Items.GLASS), item(Items.RED_STAINED_GLASS), item(Items.BLUE_STAINED_GLASS))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);
		BookmarkIngredientKey selectedKey = BookmarkItemMetadataFactory.createPermutationKey(
			item(Items.RED_STAINED_GLASS),
			INGREDIENT_MANAGER
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, false, Map.of(0, selectedKey)));

		BookmarkItemMetadata metadata = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.RED_STAINED_GLASS))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(Set.of(selectedKey), metadata.permutations());
	}

	@Test
	public void projectionCountsMixedSelectionsBySelectedSlots() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		List<ITypedIngredient<?>> plankCandidates = List.of(item(Items.OAK_PLANKS), item(Items.CHERRY_PLANKS));
		TestRecipeLayout layout = layout(
			new Object(),
			List.of(plankCandidates, plankCandidates, plankCandidates, List.of(item(Items.STICK)), List.of(item(Items.STICK))),
			List.of(List.of(item(Items.WOODEN_PICKAXE)))
		);
		BookmarkIngredientKey oak = BookmarkItemMetadataFactory.createPermutationKey(item(Items.OAK_PLANKS), INGREDIENT_MANAGER);
		BookmarkIngredientKey cherry = BookmarkItemMetadataFactory.createPermutationKey(item(Items.CHERRY_PLANKS), INGREDIENT_MANAGER);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, false, Map.of(2, cherry)));

		Map<BookmarkIngredientKey, Long> factors = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmarks.getBookmarkMetadata(bookmark).type() == mezz.jei.gui.bookmarks.BookmarkItemType.INGREDIENT)
			.collect(java.util.stream.Collectors.toMap(
				bookmark -> BookmarkItemMetadataFactory.createPermutationKey(bookmark.getElement().getTypedIngredient(), INGREDIENT_MANAGER),
				bookmark -> bookmarks.getBookmarkMetadata(bookmark).factor()
			));
		Assertions.assertEquals(2, factors.get(oak));
		Assertions.assertEquals(1, factors.get(cherry));
	}

	@Test
	public void projectionWithoutSelectedInputKeepsAllVariants() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		TestRecipeLayout layout = layout(
			new Object(),
			List.of(List.of(item(Items.GLASS), item(Items.RED_STAINED_GLASS), item(Items.BLUE_STAINED_GLASS))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, false));

		BookmarkItemMetadata metadata = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.GLASS))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(
			Set.of(
				BookmarkItemMetadataFactory.createPermutationKey(item(Items.GLASS), INGREDIENT_MANAGER),
				BookmarkItemMetadataFactory.createPermutationKey(item(Items.RED_STAINED_GLASS), INGREDIENT_MANAGER),
				BookmarkItemMetadataFactory.createPermutationKey(item(Items.BLUE_STAINED_GLASS), INGREDIENT_MANAGER)
			),
			metadata.permutations()
		);
	}

	@Test
	public void projectionKeepsOnlyFilteredInputCandidates() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		TestRecipeLayout layout = layout(
			new Object(),
			List.of(List.of(item(Items.GLASS), item(Items.RED_STAINED_GLASS), item(Items.BLUE_STAINED_GLASS))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);
		BookmarkIngredientKey red = BookmarkItemMetadataFactory.createPermutationKey(item(Items.RED_STAINED_GLASS), INGREDIENT_MANAGER);
		BookmarkIngredientKey blue = BookmarkItemMetadataFactory.createPermutationKey(item(Items.BLUE_STAINED_GLASS), INGREDIENT_MANAGER);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(
			layout,
			false,
			Map.of(),
			Map.of(0, List.of(item(Items.RED_STAINED_GLASS), item(Items.BLUE_STAINED_GLASS)))
		));

		BookmarkItemMetadata metadata = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.RED_STAINED_GLASS))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(Set.of(red, blue), metadata.permutations());
	}

	private static TestRecipeLayout layout(
		Object recipe,
		List<List<@Nullable ITypedIngredient<?>>> inputs,
		List<List<@Nullable ITypedIngredient<?>>> outputs
	) {
		return RecipeLayoutTestFixtures.layout(
			RecipeType.create("gtceu", "assembler", Object.class),
			recipe,
			RECIPE_UID,
			inputs,
			outputs
		);
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
		return mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager(
			IDENTITY_INGREDIENT_TYPE,
			identityHelper()
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

}
