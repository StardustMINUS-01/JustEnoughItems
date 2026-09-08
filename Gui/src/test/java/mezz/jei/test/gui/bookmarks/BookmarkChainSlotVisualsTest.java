package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkSlotBorder;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.overlay.bookmarks.BookmarkChainSlotVisuals;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotDisplayMode;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisuals;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
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
			BookmarkItemType.NONCONSUMABLE,
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
			BookmarkViewMode.DEFAULT,
			false,
			Optional.empty(),
			Optional.empty(),
			false,
			false
		);
	}

	@Test
	public void slotVisualsCarryBorderFromEntry() {
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.RESULT,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			ResourceLocation.parse("minecraft:crafting"),
			ResourceLocation.parse("test:plate"),
			Set.of(new BookmarkIngredientKey("test:item", "plate"))
		);
		BookmarkSlotBorder border = new BookmarkSlotBorder("recipe", 0x99A033A0, true, false, true, false);
		BookmarkDisplayEntry<Object> entry = entry(metadata).withBorder(border);

		BookmarkSlotVisuals visuals = BookmarkChainSlotVisuals.create(entry).orElseThrow();

		Assertions.assertEquals(Optional.of(border), visuals.border());
	}

	@Test
	public void catalystBookmarkShowsYellowHighlightInRealMode() {
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.NONCONSUMABLE,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			ResourceLocation.parse("test:category"),
			ResourceLocation.parse("test:recipe"),
			Set.of()
		);

		BookmarkSlotVisuals visuals = BookmarkChainSlotVisuals.create(
			entry(metadata),
			BookmarkSlotDisplayMode.REAL
		).orElseThrow();

		Assertions.assertEquals(OptionalInt.of(0x66E8C135), visuals.backgroundColor());
		Assertions.assertEquals(Optional.of("C"), visuals.recipeMarkerText());
	}

	@Test
	public void ingredientBookmarkKeepsGreenHighlightInRealMode() {
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.INGREDIENT,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			ResourceLocation.parse("test:category"),
			ResourceLocation.parse("test:recipe"),
			Set.of()
		);

		BookmarkSlotVisuals visuals = BookmarkChainSlotVisuals.create(
			entry(metadata),
			BookmarkSlotDisplayMode.REAL
		).orElseThrow();

		Assertions.assertEquals(OptionalInt.of(0x6645DA75), visuals.backgroundColor());
	}
}
