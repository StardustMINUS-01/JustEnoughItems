package mezz.jei.test.gui.input.handlers;

import mezz.jei.gui.input.handlers.IngredientClipboardText;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

public class IngredientClipboardTextTest {
	@Test
	public void formatsSlashTags() {
		List<String> text = IngredientClipboardText.formatTagLocations(Stream.of(
			ResourceLocation.parse("minecraft:mineable/pickaxe"),
			ResourceLocation.parse("minecraft:planks")
		));
		Assertions.assertEquals(List.of("#minecraft:mineable/pickaxe", "#minecraft:planks"), text);
	}

	@Test
	public void deduplicatesAndSortsTags() {
		List<String> text = IngredientClipboardText.formatTagLocations(Stream.of(
			ResourceLocation.parse("minecraft:planks"),
			ResourceLocation.parse("minecraft:mineable/pickaxe"),
			ResourceLocation.parse("minecraft:planks")
		));
		Assertions.assertEquals(List.of("#minecraft:mineable/pickaxe", "#minecraft:planks"), text);
		Assertions.assertTrue(IngredientClipboardText.formatTagLocations(Stream.empty()).isEmpty());
	}
}
