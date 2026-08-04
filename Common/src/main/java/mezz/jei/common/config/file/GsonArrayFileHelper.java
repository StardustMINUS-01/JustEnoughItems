package mezz.jei.common.config.file;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.common.util.PathUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public final class GsonArrayFileHelper {
	private GsonArrayFileHelper() {
	}

	public static void write(Path path, int version, Collection<? extends JsonElement> elements) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path tempFile = parent == null ?
			Files.createTempFile(null, null) :
			Files.createTempFile(parent, null, null);
		try {
			try (BufferedWriter out = Files.newBufferedWriter(tempFile)) {
				JsonArrayWriter writer = JsonArrayWriter.start(out);
				JsonObject versionElement = new JsonObject();
				versionElement.addProperty("version", version);
				writer.add(versionElement);
				for (JsonElement element : elements) {
					writer.add(element);
				}
				writer.end();
			}
			PathUtil.moveAtomicReplace(tempFile, path);
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	public static boolean isVersionHeader(JsonElement element, int version) {
		if (!element.isJsonObject()) {
			return false;
		}
		JsonElement versionElement = element.getAsJsonObject().get("version");
		return versionElement != null &&
			versionElement.isJsonPrimitive() &&
			versionElement.getAsInt() == version;
	}

	public static Optional<JsonArray> read(Path path, int version) {
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			JsonElement jsonElement = JsonParser.parseReader(reader);
			if (!jsonElement.isJsonArray()) {
				return Optional.empty();
			}
			JsonArray jsonArray = jsonElement.getAsJsonArray();
			if (jsonArray.isEmpty() || !isVersionHeader(jsonArray.get(0), version)) {
				return Optional.empty();
			}
			JsonArray entries = new JsonArray();
			for (int i = 1; i < jsonArray.size(); i++) {
				entries.add(jsonArray.get(i));
			}
			return Optional.of(entries);
		} catch (RuntimeException | IOException e) {
			return Optional.empty();
		}
	}
}
