package mezz.jei.gui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import mezz.jei.gui.bookmarks.BookmarkChapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonConfigFileGuardTest {
	private static final Codec<JsonElement> JSON_CODEC = Codec.PASSTHROUGH.xmap(
		value -> value.convert(JsonOps.INSTANCE).getValue(), value -> new Dynamic<>(JsonOps.INSTANCE, value));
	private static final Codec<JsonElement> MISSING_MOD_CODEC = JSON_CODEC.validate(value -> value.isJsonObject() && value.getAsJsonObject().has("missingMod") ? DataResult.error(() -> "missing mod") : DataResult.success(value));
	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(ints = {2, 3})
	void unresolvedRecordsSurviveEditsAndRecoverAfterTheModReturns(int version) throws Exception {
		Path path = directory.resolve("entries.json");
		String original = "[ {\"version\":" + version + "}, 1, {\"recipe\":\"minecraft:old_synthetic\",\"chapter\":7}, 3 ]\n";
		Files.writeString(path, original);
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path);
		AtomicInteger errors = new AtomicInteger();
		assertEquals(List.of(1, 3), guard.read(version, Codec.INT, JsonOps.INSTANCE,
			(element, error) -> errors.incrementAndGet(), (element, exception) -> errors.incrementAndGet()));
		guard.finishLoading();
		assertEquals(1, errors.get());
		assertTrue(guard.write(path, version, List.of(4), Codec.INT, JsonOps.INSTANCE));
		JsonElement unknown = JsonParser.parseString(original).getAsJsonArray().get(2);
		assertEquals(unknown, saved(path).get(2));
		JsonConfigFileGuard reloaded = new JsonConfigFileGuard(path);
		assertEquals(List.of(4), read(reloaded, version, Codec.INT));
		reloaded.finishLoading();
		assertTrue(reloaded.write(path, version, List.of(), Codec.INT, JsonOps.INSTANCE));
		assertTrue(reloaded.write(path, version, List.of(), Codec.INT, JsonOps.INSTANCE));
		assertEquals(2, saved(path).size());
		assertEquals(List.of(unknown), read(new JsonConfigFileGuard(path), version, JSON_CODEC));
	}

	@Test
	void unsupportedVersionCannotBeMisreadAsLegacyEntries() throws Exception {
		Path path = directory.resolve("entries.json");
		String original = "[{\"version\":99},1]";
		Files.writeString(path, original);
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path);
		assertTrue(read(guard, 3, Codec.INT).isEmpty());
		assertTrue(read(guard, 2, Codec.INT).isEmpty());
		assertTrue(read(guard, null, Codec.INT).isEmpty());
		guard.finishLoading();
		assertFalse(guard.write(path, 3, List.of(), Codec.INT, JsonOps.INSTANCE));
		assertEquals(original, Files.readString(path));
	}

	@Test
	void malformedDocumentCannotBeReplacedByAnEmptySave() throws Exception {
		Path path = directory.resolve("entries.json");
		String original = "[{\"version\":3},";
		Files.writeString(path, original);
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path);
		assertThrows(RuntimeException.class, () -> read(guard, 3, Codec.INT));
		guard.finishLoading();
		assertFalse(guard.write(path, 3, List.of(), Codec.INT, JsonOps.INSTANCE));
		assertEquals(original, Files.readString(path));
	}

	@Test
	void encodingFailureAbortsTheWholeAtomicReplacement() throws Exception {
		Path path = directory.resolve("entries.json");
		String original = "[{\"version\":2},1]";
		Files.writeString(path, original);
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path);
		assertEquals(List.of(1), read(guard, 2, Codec.INT));
		guard.finishLoading();
		Codec<Integer> nonNegative = Codec.INT.validate(value -> value >= 0 ? DataResult.success(value) : DataResult.error(() -> "negative"));
		assertThrows(IllegalStateException.class, () -> guard.write(path, 2, List.of(2, -1, 3), nonNegative, JsonOps.INSTANCE));
		assertEquals(original, Files.readString(path));
		assertTrue(guard.write(path, 2, List.of(2, 3), nonNegative, JsonOps.INSTANCE));
		assertEquals(List.of(2, 3), read(new JsonConfigFileGuard(path), 2, Codec.INT));
	}

	@Test
	void missingFilesAndCompletelyReadLegacyFilesRemainWritable() throws Exception {
		Path missing = directory.resolve("new.json");
		JsonConfigFileGuard fresh = new JsonConfigFileGuard(missing);
		assertTrue(read(fresh, 3, Codec.INT).isEmpty());
		fresh.finishLoading();
		assertTrue(fresh.write(missing, 3, List.of(1), Codec.INT, JsonOps.INSTANCE));
		Path legacy = directory.resolve("legacy.json");
		Files.writeString(legacy, "[1,2]");
		JsonConfigFileGuard guard = new JsonConfigFileGuard(legacy);
		assertTrue(read(guard, 3, Codec.INT).isEmpty());
		assertEquals(List.of(1, 2), read(guard, null, Codec.INT));
		guard.finishLoading();
		assertTrue(guard.write(legacy, 3, List.of(1, 2), Codec.INT, JsonOps.INSTANCE));
	}

	@Test
	void revokedAndWrongWorldSnapshotsCannotWrite() throws Exception {
		Path first = directory.resolve("first.json");
		Path second = directory.resolve("second.json");
		Files.writeString(first, "[{\"version\":2},1]");
		JsonConfigFileGuard guard = new JsonConfigFileGuard(first);
		read(guard, 2, Codec.INT);
		guard.finishLoading();
		assertFalse(guard.write(second, 2, List.of(2), Codec.INT, JsonOps.INSTANCE));
		assertFalse(Files.exists(second));
		guard.preventWrites();
		assertFalse(guard.write(first, 2, List.of(2), Codec.INT, JsonOps.INSTANCE));
		assertEquals("[{\"version\":2},1]", Files.readString(first));
	}

	@Test
	void uniquelyResolvedAliasCanBeSavedNormally() throws Exception {
		Path path = directory.resolve("entries.json");
		String original = "[{\"version\":3},\"old\"]";
		Files.writeString(path, original);
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path);
		Codec<String> alias = Codec.STRING.xmap(value -> "/" + value, value -> value);
		assertEquals(List.of("/old"), read(guard, 3, alias));
		guard.finishLoading();
		assertTrue(guard.write(path, 3, List.of("/old", "new"), Codec.STRING, JsonOps.INSTANCE));
		assertEquals(List.of("/old", "new"), read(new JsonConfigFileGuard(path), 3, Codec.STRING));
	}

	@Test
	void throwingDecoderAlsoPreservesRawRecords() throws Exception {
		Path path = directory.resolve("entries.json");
		String original = "[{\"version\":2},1,2,3]";
		Files.writeString(path, original);
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path);
		Codec<Integer> broken = Codec.INT.xmap(value -> {
			if (value == 2)
				throw new IllegalStateException("unavailable recipe");
			return value;
		}, value -> value);
		assertEquals(List.of(1, 3), read(guard, 2, broken));
		guard.finishLoading();
		assertTrue(guard.write(path, 2, List.of(1, 3, 4), Codec.INT, JsonOps.INSTANCE));
		assertEquals(List.of(1, 3, 4, 2), read(new JsonConfigFileGuard(path), 2, Codec.INT));
	}

	@Test
	void failedRecordsFollowTheirChapterWhenChaptersAreReordered() throws Exception {
		Path path = directory.resolve("bookmarks.json");
		Files.writeString(path, "[{\"version\":3},{\"type\":\"chapter\",\"id\":1},{\"type\":\"group\",\"id\":7},{\"missingMod\":\"a\",\"forkData\":{\"group\":7}},{\"type\":\"chapter\",\"id\":2},{\"missingMod\":\"b\"}]");
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path, true);
		assertEquals(3, read(guard, 3, MISSING_MOD_CODEC).size());
		guard.finishLoading();
		List<JsonElement> edits = jsonEntries("[{\"type\":\"chapter\",\"id\":2,\"title\":\"edited\"},{\"new\":1},{\"type\":\"chapter\",\"id\":1},{\"type\":\"group\",\"id\":7,\"title\":\"renamed\"}]");
		assertTrue(guard.write(path, 3, edits, JSON_CODEC, JsonOps.INSTANCE));
		JsonArray result = saved(path);
		assertEquals("b", result.get(3).getAsJsonObject().get("missingMod").getAsString());
		assertEquals("a", result.get(6).getAsJsonObject().get("missingMod").getAsString());
		assertEquals("renamed", result.get(5).getAsJsonObject().get("title").getAsString());
		assertEquals(6, read(new JsonConfigFileGuard(path, true), 3, JSON_CODEC).size());
	}

	@Test
	void deletedChapterAndGroupContextAreRetainedWithoutChangingTheActiveChapter() throws Exception {
		Path path = directory.resolve("bookmarks.json");
		Files.writeString(path, "[{\"version\":3},{\"type\":\"chapter\",\"id\":1,\"active\":true},{\"type\":\"group\",\"id\":7},{\"missingMod\":\"a\",\"forkData\":{\"group\":7}}]");
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path, true);
		read(guard, 3, MISSING_MOD_CODEC);
		guard.finishLoading();
		assertTrue(guard.write(path, 3, jsonEntries("[{\"type\":\"chapter\",\"id\":2,\"active\":true},{\"new\":1}]"), JSON_CODEC, JsonOps.INSTANCE));
		JsonArray result = saved(path);
		assertEquals(1, result.get(3).getAsJsonObject().get("id").getAsInt());
		assertFalse(result.get(3).getAsJsonObject().get("active").getAsBoolean());
		assertEquals(7, result.get(4).getAsJsonObject().get("id").getAsInt());
		assertEquals("a", result.get(5).getAsJsonObject().get("missingMod").getAsString());
		assertTrue(guard.write(path, 3, List.of(), JSON_CODEC, JsonOps.INSTANCE));
		assertEquals(1, saved(path).get(1).getAsJsonObject().get("id").getAsInt());
	}

	@Test
	void versionTwoNativeRecordsCanRecoverUnderVersionThree() throws Exception {
		Path path = directory.resolve("bookmarks.json");
		Files.writeString(path, "[{\"version\":2},{\"type\":\"group\",\"id\":7},{\"missingMod\":\"a\",\"forkData\":{\"group\":7}}]");
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path, true);
		assertTrue(read(guard, 3, MISSING_MOD_CODEC).isEmpty());
		assertEquals(1, read(guard, 2, MISSING_MOD_CODEC).size());
		guard.finishLoading();
		assertTrue(guard.write(path, 3, jsonEntries("[{\"type\":\"chapter\",\"id\":1},{\"new\":1}]"), JSON_CODEC, JsonOps.INSTANCE));
		assertEquals(3, saved(path).get(0).getAsJsonObject().get("version").getAsInt());
		assertEquals(4, read(new JsonConfigFileGuard(path, true), 3, JSON_CODEC).size());
	}

	@Test
	void partialLegacyCompressedDataIsNotMixedIntoModernDocuments() throws Exception {
		Path path = directory.resolve("legacy.json");
		String original = "[1,{\"missingMod\":\"a\"}]";
		Files.writeString(path, original);
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path);
		assertEquals(List.of(1), read(guard, null, Codec.INT));
		guard.finishLoading();
		assertFalse(guard.write(path, 3, List.of(1, 2), Codec.INT, JsonOps.INSTANCE));
		assertEquals(original, Files.readString(path));
	}

	@ParameterizedTest
	@ValueSource(strings = {"chapter", "group"})
	void conflictingStructuralIdsAreNotSilentlyMerged(String type) throws Exception {
		Path path = directory.resolve("bookmarks.json");
		String original = "[{\"version\":3},{\"type\":\"" + type + "\",\"id\":1},{\"type\":\"" + type + "\",\"id\":1}]";
		Files.writeString(path, original);
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path, true);
		read(guard, 3, JSON_CODEC);
		guard.finishLoading();
		assertFalse(guard.write(path, 3, List.of(), JSON_CODEC, JsonOps.INSTANCE));
		assertEquals(original, Files.readString(path));
	}

	@Test
	void migrationWriterAlsoKeepsUnresolvedRecords() throws Exception {
		Path path = directory.resolve("bookmarks.json");
		String original = "[{\"version\":2},1,{\"missingMod\":\"a\"}]";
		Files.writeString(path, original);
		Path legacy = directory.resolve("bookmarks.ini");
		Files.writeString(legacy, "legacy");
		Codec<BookmarkConfigEntry> codec = Codec.INT.xmap(
			id -> BookmarkConfigEntry.chapter(new BookmarkChapter(id, "", 0, false)), entry -> entry.chapter().id());
		JsonConfigFileGuard guard = new JsonConfigFileGuard(path);
		List<BookmarkConfigEntry> entries = read(guard, 2, codec);
		guard.finishLoading();
		BookmarkJsonSerializer.migrate(path, 3, Optional.of(legacy), entries, codec, JsonOps.INSTANCE, guard);
		assertEquals("a", saved(path).get(2).getAsJsonObject().get("missingMod").getAsString());
		assertEquals(original, Files.readString(path.resolveSibling("bookmarks.json.bak")));
		assertFalse(Files.exists(legacy));
		assertEquals("legacy", Files.readString(legacy.resolveSibling("bookmarks.ini.bak")));
	}

	private static List<JsonElement> jsonEntries(String json) {
		return java.util.stream.StreamSupport.stream(JsonParser.parseString(json).getAsJsonArray().spliterator(), false).toList();
	}

	private static JsonArray saved(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonArray();
	}

	private static <T> List<T> read(JsonConfigFileGuard guard, Integer version, Codec<T> codec) throws Exception {
		return guard.read(version, codec, JsonOps.INSTANCE, (element, error) -> {}, (element, exception) -> {});
	}
}
