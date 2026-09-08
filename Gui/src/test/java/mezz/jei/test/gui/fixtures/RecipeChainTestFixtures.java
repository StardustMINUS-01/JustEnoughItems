package mezz.jei.test.gui.fixtures;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Lightweight chain data without Minecraft registries or ingredient-manager mocks. */
public final class RecipeChainTestFixtures {
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");

	private RecipeChainTestFixtures() {
	}

	public static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	public static BookmarkItemMetadata item(BookmarkIngredientKey key, long amount) {
		return metadata(null, BookmarkItemType.ITEM, key, amount, 1);
	}

	public static BookmarkItemMetadata result(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor, long multiplier) {
		return metadata(recipeUid, BookmarkItemType.RESULT, key, factor, multiplier);
	}

	public static BookmarkItemMetadata ingredient(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor) {
		return metadata(recipeUid, BookmarkItemType.INGREDIENT, key, factor, 1);
	}

	private static BookmarkItemMetadata metadata(ResourceLocation recipeUid, BookmarkItemType type, BookmarkIngredientKey key, long factor, long multiplier) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			multiplier,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(key)
		);
	}

	public static BookmarkIngredientKey key(String uid) {
		return new BookmarkIngredientKey("test:item", uid);
	}
}
