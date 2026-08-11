package mezz.jei.test.gui.input.handlers;

import mezz.jei.gui.input.handlers.IngredientClipboardText;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

public class IngredientClipboardTextTest {
	@Test
	public void formatsSlashTags() {
		String text = IngredientClipboardText.formatTagLocations(Stream.of(
			ResourceLocation.parse("minecraft:mineable/pickaxe"),
			ResourceLocation.parse("minecraft:planks")
		));
		Assertions.assertEquals("#minecraft:mineable/pickaxe,#minecraft:planks", text);
	}

	@Test
	public void deduplicatesAndSortsTags() {
		String text = IngredientClipboardText.formatTagLocations(Stream.of(
			ResourceLocation.parse("minecraft:planks"),
			ResourceLocation.parse("minecraft:mineable/pickaxe"),
			ResourceLocation.parse("minecraft:planks")
		));
		Assertions.assertEquals("#minecraft:mineable/pickaxe,#minecraft:planks", text);
	}
}
