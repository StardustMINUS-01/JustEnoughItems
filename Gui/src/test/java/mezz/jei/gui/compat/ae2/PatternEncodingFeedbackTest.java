package mezz.jei.gui.compat.ae2;

import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.compat.ae2.PatternEncodingFeedback.Entry;
import mezz.jei.gui.compat.ae2.PatternEncodingFeedback.Outcome;
import mezz.jei.gui.compat.ae2.PatternEncodingFeedback.RecipeKey;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisuals;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PatternEncodingFeedbackTest {
	private static final RecipeKey RECIPE = new RecipeKey(ResourceLocation.parse("test:crafting"), ResourceLocation.parse("test:plate"));
	private static final BookmarkSlotVisuals SHIFT_VISUALS = new BookmarkSlotVisuals(OptionalInt.of(0x557799BB), OptionalInt.empty(),
		Optional.of("12"), OptionalInt.empty(), Optional.empty(), OptionalInt.empty(), Optional.empty(), OptionalInt.empty(), Optional.empty());
	private static final Optional<BookmarkSlotVisuals> SHIFT = Optional.of(SHIFT_VISUALS);
	private final AtomicLong clock = new AtomicLong();
	private final PatternEncodingFeedback feedback = new PatternEncodingFeedback(clock::get);

	@Test
	void fadesBackToCurrentSlotColorAndRefreshesOnRepeat() {
		var output = metadata(RECIPE, BookmarkItemType.RESULT);
		show(Outcome.ENCODED);
		var green = feedback.applyHighlight(output, SHIFT).orElseThrow();
		assertEquals(0x9956CF72, green.backgroundColor().orElseThrow());
		assertEquals(SHIFT_VISUALS.amountText(), green.amountText());
		clock.set(850);
		assertEquals(green, feedback.applyHighlight(output, SHIFT).orElseThrow());
		clock.set(925);
		int fading = feedback.applyHighlight(output, SHIFT).orElseThrow().backgroundColor().orElseThrow();
		assertNotEquals(green.backgroundColor().orElseThrow(), fading);
		assertNotEquals(SHIFT_VISUALS.backgroundColor().orElseThrow(), fading);
		show(Outcome.INVENTORY_FULL);
		assertEquals(0x99F4CA55, feedback.applyHighlight(output, SHIFT).orElseThrow().backgroundColor().orElseThrow());
		clock.set(1924);
		assertNotEquals(SHIFT, feedback.applyHighlight(output, SHIFT));
		clock.set(1925);
		assertSame(SHIFT, feedback.applyHighlight(output, SHIFT));
	}

	@Test
	void highlightsTheWholeRecipeAndClearsUnprocessedEntries() {
		show(Outcome.FAILED);
		clock.set(100);
		for (var type : List.of(BookmarkItemType.RESULT, BookmarkItemType.INGREDIENT, BookmarkItemType.NONCONSUMABLE)) {
			var slot = feedback.applyHighlight(metadata(RECIPE, type), Optional.empty()).orElseThrow();
			assertEquals(0x99EC5A59, slot.backgroundColor().orElseThrow());
		}
		var otherCategory = new RecipeKey(ResourceLocation.parse("test:processing"), RECIPE.id());
		assertSame(SHIFT, feedback.applyHighlight(metadata(otherCategory, BookmarkItemType.RESULT), SHIFT));
		var otherRecipe = new RecipeKey(RECIPE.type(), ResourceLocation.parse("test:other"));
		assertSame(SHIFT, feedback.applyHighlight(metadata(otherRecipe, BookmarkItemType.RESULT), SHIFT));
		show(Outcome.NONE);
		clock.set(200);
		assertSame(SHIFT, feedback.applyHighlight(metadata(RECIPE, BookmarkItemType.RESULT), SHIFT));
		assertEquals(Optional.empty(), feedback.applyHighlight(metadata(RECIPE, BookmarkItemType.INGREDIENT), Optional.empty()));
	}

	private void show(Outcome outcome) {
		feedback.show(Component.literal("Result"), List.of(new Entry(RECIPE, outcome)));
	}

	private static BookmarkItemMetadata metadata(RecipeKey key, BookmarkItemType type) {
		return new BookmarkItemMetadata(1, type, 1, 1, BookmarkItemMetadata.CHANCE_FULL, key.type(), key.id(), Set.of());
	}
}
