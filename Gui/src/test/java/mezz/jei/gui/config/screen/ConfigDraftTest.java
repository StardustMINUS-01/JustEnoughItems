package mezz.jei.gui.config.screen;

import mezz.jei.common.config.ConfigManagerAdapter;
import mezz.jei.api.runtime.config.IJeiConfigValue;
import mezz.jei.api.runtime.config.IJeiConfigListValueSerializer;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.serializers.ListSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("removal")
class ConfigDraftTest {
	@TempDir
	Path directory;

	@SuppressWarnings("unchecked")
	private <T> IJeiConfigValue<T> config(String name, T defaultValue, IConfigValueSerializer<T> serializer) {
		var settings = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		var manager = new ConfigManager("JEI config draft test", settings, settings);
		var builder = new ConfigSchemaBuilder("jei", directory.resolve("draft.ini"), "jei.config.draft", manager);
		builder.addCategory("test").addValue(name, defaultValue, serializer).build();
		var schema = builder.build();
		return (IJeiConfigValue<T>) ConfigManagerAdapter.create(() -> List.of(schema))
			.getConfigFiles().iterator().next().getCategories().get(0).getConfigValues().iterator().next();
	}

	@Test
	void listChoicesKeepSelectionOrderWhileEditsRemainLocal() {
		var serializer = new ListSerializer<>(new IntegerSerializer(0, 3));
		var config = config("order", List.of(2, 1), serializer);
		var draft = new ConfigDraft<>(config);
		var editor = new ConfigListDraft<>(draft, (IJeiConfigListValueSerializer<Integer>) config.getSerializer());
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
		var config = config("number", 3, new IntegerSerializer(0, 10));
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
		assertFalse(draft.isChanged());
		assertEquals(0, changed.get());
		draft.apply();
		assertEquals(1, notifications.get());
		draft.setText("8");
		assertEquals(1, changed.get());
		assertEquals(3, config.getValue());
		draft.setText("3");
		assertFalse(draft.isChanged());
	}

	@Test
	void partialListParseNeverChangesLiveConfiguration() {
		var config = config("order", List.of(2, 1), new ListSerializer<>(new IntegerSerializer(0, 10)));
		var draft = new ConfigDraft<>(config);
		draft.setText("[3, \"invalid\", 1]");
		assertFalse(draft.getErrors().isEmpty());
		draft.apply();
		assertEquals(List.of(2, 1), config.getValue());
		draft.setText("[3, 1]");
		assertTrue(draft.getErrors().isEmpty());
		draft.apply();
		assertEquals(List.of(3, 1), config.getValue());
	}

	@Test
	void untouchedDraftDoesNotOverwriteExternalChanges() {
		var config = config("number", 3, new IntegerSerializer(0, 10));
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
