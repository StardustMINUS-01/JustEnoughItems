package mezz.jei.test.gui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.common.config.file.GsonArrayFileHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class GsonArrayFileHelperTest {
	@Test
	public void writeCreatesVersionedArrayFile() throws IOException {
		Path dir = Files.createTempDirectory("jei-json-test");
		try {
			Path path = dir.resolve("test.json");
			JsonObject element = new JsonObject();
			element.addProperty("id", "group_1");

			GsonArrayFileHelper.write(path, 1, List.of(element));

			JsonElement parsed = JsonParser.parseReader(Files.newBufferedReader(path));
			Assertions.assertTrue(parsed.isJsonArray());
			JsonArray array = parsed.getAsJsonArray();
			Assertions.assertEquals(2, array.size());
			Assertions.assertTrue(GsonArrayFileHelper.isVersionHeader(array.get(0), 1));
			Assertions.assertFalse(GsonArrayFileHelper.isVersionHeader(array.get(0), 2));
			Assertions.assertEquals("group_1", array.get(1).getAsJsonObject().get("id").getAsString());
		} finally {
			try (var stream = Files.walk(dir)) {
				stream.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException ignored) {
					}
				});
			}
		}
	}
}
