package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public class BookmarkVisualsTest {
	@ParameterizedTest
	@ValueSource(ints = {1, 4})
	public void showsBookmarkAmount(int amount) {
		var metadata = BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID)
			.withMultiplier(amount);
		var text = BookmarkChainSlotVisuals.create(entry(metadata)).flatMap(BookmarkSlotVisuals::amountText);

		Assertions.assertEquals(amount == 1 ? Optional.empty() : Optional.of("4"), text);
	}

	@ParameterizedTest
	@EnumSource(value = BookmarkSlotDisplayMode.class, names = {"DEFAULT", "REAL"})
	public void showsCatalystMarker(BookmarkSlotDisplayMode mode) {
		var visuals = BookmarkChainSlotVisuals.create(recipeEntry(BookmarkItemType.NONCONSUMABLE), mode).orElseThrow();

		Assertions.assertEquals(Optional.of("C"), visuals.recipeMarkerText());
		Assertions.assertEquals(0xFFFFFF55, visuals.recipeMarkerTextColor().orElseThrow());
		Assertions.assertEquals(mode == BookmarkSlotDisplayMode.REAL ? OptionalInt.of(0x66E8C135) : OptionalInt.empty(),
			visuals.backgroundColor());
	}

	@Test
	public void preservesBorder() {
		var border = new BookmarkSlotBorder("recipe", 0x99A033A0, true, false, true, false);
		var entry = recipeEntry(BookmarkItemType.RESULT).withBorder(border);

		var visuals = BookmarkChainSlotVisuals.create(entry).orElseThrow();

		Assertions.assertEquals(Optional.of(border), visuals.border());
	}

	@Test
	public void highlightsIngredient() {
		var visuals = BookmarkChainSlotVisuals.create(recipeEntry(BookmarkItemType.INGREDIENT), BookmarkSlotDisplayMode.REAL)
			.orElseThrow();

		Assertions.assertEquals(OptionalInt.of(0x6645DA75), visuals.backgroundColor());
	}

	private static BookmarkDisplayEntry<Object> recipeEntry(BookmarkItemType type) {
		return entry(new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID, type, 1, 1, BookmarkItemMetadata.CHANCE_FULL,
			ResourceLocation.parse("minecraft:crafting"), ResourceLocation.parse("test:recipe"), Set.of()
		));
	}

	private static BookmarkDisplayEntry<Object> entry(BookmarkItemMetadata metadata) {
		return new BookmarkDisplayEntry<>(
			new Object(), 0, metadata, BookmarkViewMode.DEFAULT, false,
			Optional.empty(), Optional.empty(), false, false
		);
	}
}
