package mezz.jei.test.gui.bookmarks;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures.TestRecipeLayout;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.typed;

public class BookmarkProjectionTest {
	private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("test", "assembler");
	private static final IIngredientType<IdentityIngredient> TYPE = () -> IdentityIngredient.class;
	private static final IIngredientManager INGREDIENTS = ingredientManager(TYPE, identityHelper());

	@BeforeAll
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void addsVirtualCircuit() {
		var book = book();
		var layout = layout(new GTRecipe(7), List.of(List.of(item(Items.IRON_INGOT))),
			List.of(List.of(item(Items.GOLD_INGOT))));
		Assertions.assertTrue(book.addRecipeBookmarks(layout, false));

		var metadata = getMetadata(book, Items.REPEATER);
		Assertions.assertEquals(0, metadata.factor());
		Assertions.assertEquals(0, metadata.amount());
		Assertions.assertEquals(RECIPE, metadata.recipeUid());
		Assertions.assertEquals(BookmarkItemType.NONCONSUMABLE, metadata.type());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void preservesNonconsumables(boolean visible) {
		var book = book();
		ItemStack mold = new ItemStack(Items.SHEARS, 3);
		List<List<ITypedIngredient<?>>> inputs = visible
			? List.of(List.of(item(Items.IRON_INGOT)), List.of(typed(mold)))
			: List.of(List.of(item(Items.IRON_INGOT)));
		var layout = layout(new GTRecipe(7, mold), inputs, List.of(List.of(item(Items.GOLD_INGOT))));
		Assertions.assertTrue(book.addRecipeBookmarks(layout, true));

		var metadata = getMetadata(book, Items.SHEARS);
		Assertions.assertEquals(BookmarkItemType.NONCONSUMABLE, metadata.type());
		if (!visible) {
			Assertions.assertEquals(3, metadata.factor());
			Assertions.assertEquals(3, metadata.amount());
		}
	}

	@Test
	public void keepsDistinctCatalysts() {
		var book = book();
		ItemStack first = new ItemStack(Items.SHEARS, 3);
		ItemStack second = new ItemStack(Items.FLINT_AND_STEEL, 2);
		var layout = layout(new GTRecipe(7, List.of(first, second)),
			List.of(List.of(item(Items.IRON_INGOT)), List.of(typed(first)), List.of(typed(second))),
			List.of(List.of(item(Items.GOLD_INGOT))));
		Assertions.assertTrue(book.addRecipeBookmarks(layout, true));

		var catalysts = book.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.SHEARS) || stack.is(Items.FLINT_AND_STEEL)).orElse(false))
			.map(book::getBookmarkMetadata).toList();
		Assertions.assertEquals(2, catalysts.size());
		Assertions.assertTrue(catalysts.stream().allMatch(metadata -> metadata.type() == BookmarkItemType.NONCONSUMABLE));
	}

	@Test
	public void deduplicatesIngredients() {
		var book = book();
		var layout = layout(new Object(), List.of(List.of(identity("styrene_butadiene_rubber"))),
			List.of(List.of(item(Items.GOLD_INGOT))));
		book.addRecipeBookmarks(layout, false);
		book.addRecipeBookmarks(layout, false);
		Assertions.assertEquals(2, book.getBookmarks().size());
	}

	@ParameterizedTest
	@CsvSource({"false, false", "true, false", "false, true"})
	public void preservesCandidates(boolean selected, boolean filtered) {
		var book = book();
		List<ITypedIngredient<?>> candidates = List.of(item(Items.GLASS), item(Items.RED_STAINED_GLASS), item(Items.BLUE_STAINED_GLASS));
		var layout = layout(new Object(), List.of(candidates), List.of(List.of(item(Items.GOLD_INGOT))));
		Set<BookmarkIngredientKey> expected;
		if (selected) {
			var red = createKey(item(Items.RED_STAINED_GLASS));
			Assertions.assertTrue(book.addRecipeBookmarks(layout, false, Map.of(0, red)));
			expected = Set.of(red);
		} else if (filtered) {
			Assertions.assertTrue(book.addRecipeBookmarks(layout, false, Map.of(), Map.of(0, candidates.subList(1, 3))));
			expected = Set.of(createKey(item(Items.RED_STAINED_GLASS)), createKey(item(Items.BLUE_STAINED_GLASS)));
		} else {
			Assertions.assertTrue(book.addRecipeBookmarks(layout, false));
			expected = Set.of(createKey(item(Items.GLASS)), createKey(item(Items.RED_STAINED_GLASS)), createKey(item(Items.BLUE_STAINED_GLASS)));
		}
		var metadata = getMetadata(book, selected || filtered ? Items.RED_STAINED_GLASS : Items.GLASS);
		Assertions.assertEquals(expected, metadata.permutations());
	}

	@Test
	public void countsSelectedSlots() {
		var book = book();
		List<ITypedIngredient<?>> planks = List.of(item(Items.OAK_PLANKS), item(Items.CHERRY_PLANKS));
		var layout = layout(new Object(),
			List.of(planks, planks, planks, List.of(item(Items.STICK)), List.of(item(Items.STICK))),
			List.of(List.of(item(Items.WOODEN_PICKAXE))));
		var oak = createKey(item(Items.OAK_PLANKS));
		var cherry = createKey(item(Items.CHERRY_PLANKS));
		Assertions.assertTrue(book.addRecipeBookmarks(layout, false, Map.of(2, cherry)));

		Map<BookmarkIngredientKey, Long> factors = book.getBookmarks().stream()
			.filter(bookmark -> book.getBookmarkMetadata(bookmark).type() == BookmarkItemType.INGREDIENT)
			.collect(Collectors.toMap(
				bookmark -> createKey(bookmark.getElement().getTypedIngredient()),
				bookmark -> book.getBookmarkMetadata(bookmark).factor()));
		Assertions.assertEquals(2, factors.get(oak));
		Assertions.assertEquals(1, factors.get(cherry));
	}

	private static BookmarkList book() {
		return new BookmarkList(null, null, INGREDIENTS, null, null, null, null);
	}

	private static BookmarkItemMetadata getMetadata(BookmarkList book, Item item) {
		return book.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(item)).orElse(false))
			.map(book::getBookmarkMetadata).findFirst().orElseThrow();
	}

	private static BookmarkIngredientKey createKey(ITypedIngredient<?> ingredient) {
		return BookmarkItemMetadataFactory.createPermutationKey(ingredient, INGREDIENTS);
	}

	private static TestRecipeLayout layout(
		Object recipe,
		List<List<@Nullable ITypedIngredient<?>>> inputs,
		List<List<@Nullable ITypedIngredient<?>>> outputs
	) {
		return RecipeLayoutTestFixtures.layout(RecipeType.create("gtceu", "assembler", Object.class), recipe, RECIPE, inputs, outputs);
	}

	private static ITypedIngredient<IdentityIngredient> identity(String id) {
		return new ITypedIngredient<>() {
			@Override
			public ITypedIngredient<IdentityIngredient> normalize(IIngredientHelper<IdentityIngredient> helper) {
				return TypedIngredient.createUnvalidated(getType(), helper.normalizeIngredient(getIngredient()));
			}

			@Override
			public IIngredientType<IdentityIngredient> getType() {
				return TYPE;
			}

			@Override
			public IdentityIngredient getIngredient() {
				return new IdentityIngredient(id);
			}
		};
	}

	private static IIngredientHelper<IdentityIngredient> identityHelper() {
		return new IIngredientHelper<>() {
			@Override
			public IIngredientType<IdentityIngredient> getIngredientType() {
				return TYPE;
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
