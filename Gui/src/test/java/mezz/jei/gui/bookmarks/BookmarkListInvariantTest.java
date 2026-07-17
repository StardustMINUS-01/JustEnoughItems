package mezz.jei.gui.bookmarks;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class BookmarkListInvariantTest {
	private static final ResourceLocation RECIPE_TYPE = ResourceLocation.parse("minecraft:crafting");
	private static final ResourceLocation RECIPE = ResourceLocation.parse("test:plate");

	@Test
	public void movingLooseBookmarkCannotSplitRecipeBlock() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark result = bookmark("result");
		TestBookmark loose = bookmark("loose");
		TestBookmark input = bookmark("input");

		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.addToListWithoutNotifying(loose, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "plate"));
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(BookmarkItemType.INGREDIENT, RECIPE, "ingot"));

		bookmarks.moveBookmarks(List.of(loose), input, BookmarkGroupManager.DEFAULT_GROUP_ID, 0);

		Assertions.assertEquals(List.of(result, input, loose), bookmarks.getBookmarks());
	}

	@Test
	public void removingLastBookmarkInGroupRemovesEmptyGroupMetadata() {
		BookmarkList bookmarks = bookmarkList();
		String groupId = "group_1";
		TestBookmark bookmark = bookmark("plate");

		bookmarks.addGroupFromConfig(new BookmarkGroup(groupId, "Machines", false));
		bookmarks.addToListWithoutNotifying(bookmark, false);
		bookmarks.moveBookmarkMetadataFromConfig(bookmark, BookmarkItemMetadata.defaultForGroup(groupId));

		bookmarks.remove(bookmark);

		Assertions.assertTrue(bookmarks.getBookmarkGroups().stream().noneMatch(group -> group.id().equals(groupId)));
	}

	private static BookmarkList bookmarkList() {
		return new BookmarkList(null, null, null, null, null, null, null);
	}

	private static TestBookmark bookmark(String id) {
		return new TestBookmark(id);
	}

	private static BookmarkItemMetadata metadata(BookmarkItemType type, ResourceLocation recipeUid, String ingredientUid) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			RECIPE_TYPE,
			recipeUid,
			Set.of(new BookmarkIngredientKey("test:item", ingredientUid, null))
		);
	}

	private record TestBookmark(String id) implements IBookmark {
		@Override
		public BookmarkType getType() {
			return BookmarkType.INGREDIENT;
		}

		@Override
		public IElement<?> getElement() {
			return new TestElement(this);
		}

		@Override
		public boolean isVisible() {
			return true;
		}

		@Override
		public void setVisible(boolean visible) {
		}
	}

	private record TestElement(IBookmark bookmark) implements IElement<Object> {
		@Override
		public ITypedIngredient<Object> getTypedIngredient() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<IBookmark> getBookmark() {
			return Optional.of(bookmark);
		}

		@Override
		public @Nullable IDrawable createRenderOverlay() {
			return null;
		}

		@Override
		public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
		}

		@Override
		public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper, IIngredientRenderer<Object> ingredientRenderer, IIngredientHelper<Object> ingredientHelper) {
		}

		@Override
		public boolean isVisible() {
			return true;
		}

		@Override
		public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
			return false;
		}
	}
}
