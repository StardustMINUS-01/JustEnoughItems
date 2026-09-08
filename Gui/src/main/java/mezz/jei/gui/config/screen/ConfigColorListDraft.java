package mezz.jei.gui.config.screen;

import mezz.jei.api.runtime.config.IJeiConfigListValueSerializer;

import java.util.ArrayList;
import java.util.List;

final class ConfigColorListDraft {
	static final class Entry {
		String name;
		String rgb;
		int previewColor;

		Entry(String name, String rgb) {
			this.name = name;
			setRgb(rgb);
		}

		void setRgb(String rgb) {
			this.rgb = rgb;
			previewColor = isRgbValid() ? 0xFF000000 | Integer.parseInt(rgb, 16) : 0xFF202020;
		}

		boolean isRgbValid() { return rgb.matches("[0-9a-fA-F]{6}"); }

		boolean isValid() {
			return !name.isBlank() && isRgbValid();
		}
	}

	final ConfigDraft<?> draft;
	final List<Entry> entries = new ArrayList<>();

	<T> ConfigColorListDraft(ConfigDraft<?> draft, IJeiConfigListValueSerializer<T> serializer) {
		this.draft = draft;
		for (T value : serializer.deserialize(draft.getText()).getResult().orElseThrow()) {
			String encoded = serializer.getListValueSerializer().serialize(value);
			int separator = encoded.indexOf(':');
			entries.add(new Entry(encoded.substring(0, separator), encoded.substring(separator + 1)));
		}
	}

	void update() {
		// This editor owns the documented name:RGB format, including incomplete edits.
		draft.setText(String.join(", ", entries.stream().map(entry -> entry.name + ":" + entry.rgb).toList()));
	}

	void add() {
		entries.add(new Entry("", "FFFFFF"));
		update();
	}

	void remove(int index) {
		entries.remove(index);
		update();
	}

	boolean isValid() {
		return entries.stream().allMatch(Entry::isValid);
	}
}
