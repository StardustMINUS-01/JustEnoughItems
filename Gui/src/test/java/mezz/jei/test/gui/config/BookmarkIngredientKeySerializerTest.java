package mezz.jei.test.gui.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.config.file.serializers.BookmarkIngredientKeySerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BookmarkIngredientKeySerializerTest {
	@Test
	public void roundTripsSimpleKey() {
		BookmarkIngredientKey key = new BookmarkIngredientKey("minecraft:item_stack", "minecraft:glass", null);

		BookmarkIngredientKey decoded = BookmarkIngredientKeySerializer.deserialize(
			BookmarkIngredientKeySerializer.serialize(key)
		);

		Assertions.assertEquals(key, decoded);
	}

	@Test
	public void roundTripsSerializedIngredient() {
		BookmarkIngredientKey key = new BookmarkIngredientKey("minecraft:item_stack", "minecraft:iron_pickaxe:abc", "{Count:1b}");

		BookmarkIngredientKey decoded = BookmarkIngredientKeySerializer.deserialize(
			BookmarkIngredientKeySerializer.serialize(key)
		);

		Assertions.assertEquals(key, decoded);
		Assertions.assertEquals("{Count:1b}", decoded.serializedIngredient());
	}

	@Test
	public void deserializesLegacyPrimitiveString() {
		BookmarkIngredientKey decoded = BookmarkIngredientKeySerializer.deserialize(
			JsonParser.parseString("\"minecraft:glass\"")
		);

		Assertions.assertEquals(BookmarkIngredientKey.legacy("minecraft:glass"), decoded);
	}

	@Test
	public void missingFieldsFallBackToUnknownValues() {
		JsonElement element = JsonParser.parseString("{}");

		BookmarkIngredientKey decoded = BookmarkIngredientKeySerializer.deserialize(element);

		Assertions.assertEquals(BookmarkIngredientKey.UNKNOWN_TYPE_UID, decoded.ingredientTypeUid());
		Assertions.assertEquals("fallback:unknown", decoded.ingredientUid());
		Assertions.assertNull(decoded.serializedIngredient());
	}
}
