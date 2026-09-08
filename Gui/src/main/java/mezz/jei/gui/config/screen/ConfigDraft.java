package mezz.jei.gui.config.screen;

import mezz.jei.api.runtime.config.IJeiConfigValue;
import mezz.jei.api.runtime.config.IJeiConfigListValueSerializer;
import mezz.jei.api.runtime.config.IJeiConfigValueSerializer;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

/** A screen-owned edit; parsing must succeed completely before touching live configuration. */
final class ConfigDraft<T> {
	final IJeiConfigValue<T> config;
	private final String original;
	private String text;
	private IJeiConfigValueSerializer.IDeserializeResult<T> parsed;
	IntConsumer onChange = difference -> {};

	ConfigDraft(IJeiConfigValue<T> config) {
		this.config = config;
		this.original = config.getSerializer().serialize(config.getValue());
		this.text = original;
		this.parsed = config.getSerializer().deserialize(text);
	}

	String getText() { return text; }
	void setText(String text) {
		if (!this.text.equals(text)) {
			int previousChanged = isChanged() ? 1 : 0;
			this.text = text;
			parsed = config.getSerializer().deserialize(text);
			onChange.accept((isChanged() ? 1 : 0) - previousChanged);
		}
	}
	boolean isChanged() { return !original.equals(text); }
	boolean isDefault() {
		return parsed.getErrors().isEmpty() && parsed.getResult().filter(config.getDefaultValue()::equals).isPresent();
	}
	void reset() { select(config.getDefaultValue()); }
	List<T> getChoices() {
		return config.getDefaultValue() instanceof Number ? List.of() :
			config.getSerializer().getAllValidValues().map(List::copyOf).orElse(List.of());
	}
	Optional<ConfigListDraft<?>> getListEditor() {
		if (config.getSerializer() instanceof IJeiConfigListValueSerializer<?> serializer &&
			serializer.getListValueSerializer().getAllValidValues().isPresent()) {
			return Optional.of(new ConfigListDraft<>(this, serializer));
		}
		return Optional.empty();
	}
	void cycle() {
		var choices = getChoices();
		int current = parsed.getResult().map(choices::indexOf).orElse(-1);
		select(choices.get((current + 1) % choices.size()));
	}
	String getValidValuesDescription() {
		var values = config.getSerializer().getAllValidValues().map(List::copyOf).orElse(List.of());
		if (config.getDefaultValue() instanceof Number && !values.isEmpty()) {
			return describe(values.getFirst()) + " ~ " + describe(values.getLast());
		}
		if (config.getSerializer() instanceof IJeiConfigListValueSerializer<?> serializer) {
			return describeValidValues(serializer.getListValueSerializer());
		}
		return describeValidValues(config.getSerializer());
	}
	private static <V> String describeValidValues(IJeiConfigValueSerializer<V> serializer) {
		return serializer.getAllValidValues()
			.map(values -> String.join("\n", values.stream().map(serializer::serialize).toList()))
			.orElseGet(serializer::getValidValuesDescription);
	}
	void select(T value) { setText(config.getSerializer().serialize(value)); }
	String describe(T value) { return config.getSerializer().serialize(value); }
	List<String> getErrors() {
		if (!parsed.getErrors().isEmpty()) {
			return parsed.getErrors();
		}
		if (parsed.getResult().isEmpty() || !config.getSerializer().isValid(parsed.getResult().get())) {
			return List.of(config.getSerializer().getValidValuesDescription());
		}
		return List.of();
	}
	void apply() {
		if (isChanged() && getErrors().isEmpty()) {
			parsed.getResult().ifPresent(config::set);
		}
	}
}
