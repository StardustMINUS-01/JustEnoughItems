package mezz.jei.gui.overlay.elements;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import mezz.jei.gui.bookmarks.chain.RecipeChainItemType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProjectedBookmarkElementCheatAmountTest {
	@Test
	public void plainNonDefaultBookmarkUsesMetadataAmount() {
		BookmarkItemMetadata metadata = metadata(2, 3);
		ProjectedBookmarkElement<String> element = element(metadata, Optional.empty());

		assertEquals(Optional.of(6L), element.getCheatGiveAmount());
	}

	@Test
	public void chainItemUsesCalculatedAmount() {
		BookmarkItemMetadata metadata = metadata(1, 1);
		RecipeChainItem chainItem = new RecipeChainItem(0, metadata, RecipeChainItemType.INGREDIENT, 2, 3, 8, 1, 8);
		ProjectedBookmarkElement<String> element = element(metadata, Optional.of(chainItem));

		assertEquals(Optional.of(8L), element.getCheatGiveAmount());
	}

	@Test
	public void defaultPlainBookmarkHasNoCheatAmount() {
		BookmarkItemMetadata metadata = BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID);
		ProjectedBookmarkElement<String> element = element(metadata, Optional.empty());

		assertEquals(Optional.empty(), element.getCheatGiveAmount());
	}

	@Test
	public void zeroChainAmountHasNoCheatAmount() {
		BookmarkItemMetadata metadata = metadata(1, 1);
		RecipeChainItem chainItem = new RecipeChainItem(0, metadata, RecipeChainItemType.INGREDIENT, 0, 0, 0, 0, 0);
		ProjectedBookmarkElement<String> element = element(metadata, Optional.of(chainItem));

		assertEquals(Optional.empty(), element.getCheatGiveAmount());
	}

	private static ProjectedBookmarkElement<String> element(BookmarkItemMetadata metadata, Optional<RecipeChainItem> chainItem) {
		BookmarkDisplayEntry<String> entry = new BookmarkDisplayEntry<>(
			"item",
			0,
			metadata,
			BookmarkViewMode.DEFAULT,
			Optional.empty(),
			chainItem,
			false,
			false
		);
		return new ProjectedBookmarkElement<>(new IngredientElement<>(new TestTypedIngredient("item")), entry);
	}

	private static BookmarkItemMetadata metadata(long multiplier, long factor) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.ITEM,
			multiplier,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			null,
			null,
			Set.of()
		);
	}

	private record TestTypedIngredient(String ingredient) implements ITypedIngredient<String> {
		@Override
		public IIngredientType<String> getType() {
			return TestType.INSTANCE;
		}

		@Override
		public String getIngredient() {
			return ingredient;
		}
	}

	private static final class TestType implements IIngredientType<String> {
		private static final TestType INSTANCE = new TestType();

		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}

		@Override
		public String getUid() {
			return "test:string";
		}
	}
}
