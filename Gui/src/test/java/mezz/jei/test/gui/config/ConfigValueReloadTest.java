package mezz.jei.test.gui.config;

import mezz.jei.common.config.file.ConfigValue;
import mezz.jei.common.config.file.serializers.IntegerSerializer;
import mezz.jei.common.config.file.serializers.ListSerializer;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValueReloadTest {
	@Test
	void notifiesOnValueChanges() {
		var integer = new ConfigValue<>("test", "number", 1000, new IntegerSerializer(0, 10000));
		var list = new ConfigValue<>("test", "order", List.of(1000, 2000), new ListSerializer<>(new IntegerSerializer(0, 10000)));
		AtomicInteger notices = new AtomicInteger();
		integer.addListener(v -> notices.incrementAndGet());
		list.addListener(v -> notices.incrementAndGet());
		assertTrue(integer.setFromSerializedValue("1000").isEmpty());
		assertTrue(list.setFromSerializedValue("1000, 2000").isEmpty());
		assertEquals(0, notices.get());
		integer.setFromSerializedValue("1001");
		assertEquals(1, notices.get());
		list.setFromSerializedValue("2000, 1000");
		assertEquals(2, notices.get());
		assertEquals(1001, integer.getValue());
		assertEquals(List.of(2000, 1000), list.getValue());
		assertFalse(integer.setFromSerializedValue("invalid").isEmpty());
		assertEquals(2, notices.get());
		assertEquals(1001, integer.getValue());
	}
}
