package mezz.jei.gui.config.screen;

import mezz.jei.api.runtime.config.IJeiConfigListValueSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ConfigListDraft<T> {
	final ConfigDraft<?> draft;
	private final IJeiConfigListValueSerializer<T> serializer;

	ConfigListDraft(ConfigDraft<?> draft, IJeiConfigListValueSerializer<T> serializer) {
		this.draft = draft;
		this.serializer = serializer;
	}

	List<T> getSelected() {
		return serializer.deserialize(draft.getText()).getResult().orElseThrow();
	}

	List<T> getChoices(List<T> selected) {
		List<T> choices = new ArrayList<>(selected);
		for (T value : serializer.getListValueSerializer().getAllValidValues().orElseThrow()) {
			if (!choices.contains(value)) {
				choices.add(value);
			}
		}
		return choices;
	}

	String describe(T value) {
		return serializer.getListValueSerializer().serialize(value);
	}

	void toggle(T value) {
		List<T> selected = new ArrayList<>(getSelected());
		if (!selected.remove(value)) {
			selected.add(value);
		}
		draft.setText(serializer.serialize(selected));
	}

	void move(int index, int offset) {
		List<T> selected = new ArrayList<>(getSelected());
		Collections.swap(selected, index, index + offset);
		draft.setText(serializer.serialize(selected));
	}
}
