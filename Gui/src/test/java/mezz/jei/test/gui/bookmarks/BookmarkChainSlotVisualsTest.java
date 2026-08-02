package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.overlay.bookmarks.BookmarkChainSlotVisuals;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisuals;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

public class BookmarkChainSlotVisualsTest {
	@Test
	public void defaultIngredientBookmarkDoesNotShowAmountOne() {
		BookmarkDisplayEntry<Object> entry = entry(BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID));

		Optional<String> amountText = BookmarkChainSlotVisuals.create(entry)
			.flatMap(BookmarkSlotVisuals::amountText);

		Assertions.assertTrue(amountText.isEmpty());
	}

	@Test
	public void ingredientBookmarkWithExplicitAmountShowsAmount() {
		BookmarkDisplayEntry<Object> entry = entry(new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.ITEM,
			4,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			null,
			null,
			Set.of()
		));

		Optional<String> amountText = BookmarkChainSlotVisuals.create(entry)
			.flatMap(BookmarkSlotVisuals::amountText);

		Assertions.assertEquals(Optional.of("4"), amountText);
	}

	@Test
	public void catalystBookmarkShowsYellowCatalystMarker() {
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.CATALYST,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			Set.of()
		);

		BookmarkSlotVisuals visuals = BookmarkChainSlotVisuals.create(entry(metadata)).orElseThrow();

		Assertions.assertEquals(Optional.of("C"), visuals.recipeMarkerText());
		Assertions.assertEquals(0xFFFFFF55, visuals.recipeMarkerTextColor().orElseThrow());
		Assertions.assertTrue(visuals.backgroundColor().isEmpty());
	}

	private static BookmarkDisplayEntry<Object> entry(BookmarkItemMetadata metadata) {
		return new BookmarkDisplayEntry<>(
			new Object(),
			0,
			metadata,
			false,
			false,
			Optional.empty(),
			Optional.empty(),
			false,
			false
		);
	}
}
