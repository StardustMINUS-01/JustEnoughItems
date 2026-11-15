package mezz.jei.test.gui.config;

import mezz.jei.test.gui.lib.ConfigTestFixtures;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.serializer.ConfigListOrdering;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValueReloadTest {
	@Test
	void notifiesOnValueChanges(@TempDir Path directory) {
		var schema = ConfigTestFixtures.createSchema(directory.resolve("values.ini"));
		var category = schema.addCategory("test");
		var integer = category.addInteger("number", 1000, 0, 10000).build();
		var list = category.addList("order", List.of(1000, 2000),
			integer.getEditorInfo().getSerializer(), ConfigListOrdering.ORDERED)
			.build();
		schema.build();
		AtomicInteger notices = new AtomicInteger();
		integer.addListener(change -> notices.incrementAndGet());
		list.addListener(change -> notices.incrementAndGet());
		assertTrue(applySerializedValue(integer, "1000").isEmpty());
		assertTrue(applySerializedValue(list, list.getEditorInfo().getSerializer().serialize(List.of(1000, 2000))).isEmpty());
		assertEquals(0, notices.get());
		applySerializedValue(integer, "1001");
		assertEquals(1, notices.get());
		applySerializedValue(list, list.getEditorInfo().getSerializer().serialize(List.of(2000, 1000)));
		assertEquals(2, notices.get());
		assertEquals(1001, integer.get());
		assertEquals(List.of(2000, 1000), list.get());
		assertFalse(applySerializedValue(integer, "invalid").isEmpty());
		assertEquals(2, notices.get());
		assertEquals(1001, integer.get());
	}

	private static <T> List<String> applySerializedValue(IConfigValue<T> value, String text) {
		var result = value.getEditorInfo().getSerializer().deserialize(text);
		if (result.getDiagnostics().isEmpty()) {
			result.getResult().ifPresent(value::set);
		}
		return result.getDiagnostics();
	}
}
