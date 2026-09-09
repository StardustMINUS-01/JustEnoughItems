package mezz.jei.gui.config.screen;

import mezz.jei.common.config.file.ConfigValue;
import mezz.jei.common.config.file.serializers.IntegerSerializer;
import mezz.jei.common.config.file.serializers.ListSerializer;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ConfigDraftTest {
	@Test
	void listChoicesKeepSelectionOrderWhileEditsRemainLocal() {
		var serializer = new ListSerializer<>(new IntegerSerializer(0, 3));
		var config = new ConfigValue<>("test", "order", List.of(2, 1), serializer);
		var draft = new ConfigDraft<>(config);
		var editor = new ConfigListDraft<>(draft, serializer);
		assertEquals(List.of(2, 1, 0, 3), editor.getChoices(editor.getSelected()));
		editor.move(0, 1);
		editor.toggle(3);
		assertEquals(List.of(1, 2, 3), editor.getSelected());
		assertEquals(List.of(2, 1), config.getValue());
	}

	@Test
	void rgbAllowsPartialEditsButRequiresSixAsciiHexDigits() {
		for (String value : List.of("", "a", "12aBc")) {
			assertTrue(ConfigColorListDraft.isRgbInput(value));
			assertFalse(new ConfigColorListDraft.Entry("color", value).isValid());
		}
		assertTrue(new ConfigColorListDraft.Entry("color", "aB12fF").isValid());
		for (String value : List.of("abcdef0", "GG0000", "\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16")) {
			assertFalse(new ConfigColorListDraft.Entry("color", value).isValid());
		}
		assertFalse(ConfigColorListDraft.isRgbInput("#ffffff"));
	}

	@Test
	void editsAndDefaultsStayLocalUntilApplied() {
		var config = new ConfigValue<>("test", "number", 3, new IntegerSerializer(0, 10));
		config.set(7);
		AtomicInteger notifications = new AtomicInteger();
		config.addListener(value -> notifications.incrementAndGet());
		var draft = new ConfigDraft<>(config);
		AtomicInteger changed = new AtomicInteger();
		draft.onChange = changed::addAndGet;
		draft.setText("8");
		draft.setText("9");
		assertEquals(1, changed.get());
		draft.setText("7");
		assertEquals(0, changed.get());
		draft.setText("8");
		assertEquals(7, config.getValue());
		draft.reset();
		assertEquals("3", draft.getText());
		assertEquals(7, config.getValue());
		draft.apply();
		assertEquals(3, config.getValue());
		assertEquals(1, notifications.get());
	}

	@Test
	void partialListParseNeverChangesLiveConfiguration() {
		var config = new ConfigValue<>("test", "order", List.of(2, 1), new ListSerializer<>(new IntegerSerializer(0, 10)));
		var draft = new ConfigDraft<>(config);
		draft.setText("3, invalid, 1");
		assertFalse(draft.getErrors().isEmpty());
		draft.apply();
		assertEquals(List.of(2, 1), config.getValue());
		draft.setText("3, 1");
		assertTrue(draft.getErrors().isEmpty());
		draft.apply();
		assertEquals(List.of(3, 1), config.getValue());
	}

	@Test
	void untouchedDraftDoesNotOverwriteExternalChanges() {
		var config = new ConfigValue<>("test", "number", 3, new IntegerSerializer(0, 10));
		var draft = new ConfigDraft<>(config);
		config.set(9);
		draft.apply();
		assertEquals(9, config.getValue());
		draft.setText("11");
		assertFalse(draft.getErrors().isEmpty());
		draft.apply();
		assertEquals(9, config.getValue());
	}

}
