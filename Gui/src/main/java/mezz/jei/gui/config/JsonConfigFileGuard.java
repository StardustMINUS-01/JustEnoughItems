package mezz.jei.gui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/** Retains undecodable records in their native format and original chapter. */
final class JsonConfigFileGuard {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Codec<JsonElement> JSON_CODEC = Codec.PASSTHROUGH.xmap(
		value -> value.convert(JsonOps.INSTANCE).getValue(), value -> new Dynamic<>(JsonOps.INSTANCE, value));
	private final Path path;
	private final boolean chaptered;
	private @Nullable JsonArray document;
	private final BitSet retainedIndexes = new BitSet();
	private Map<Integer, RetainedChapter> retained = Map.of();
	private boolean recognized;
	private boolean incompleteStructure;
	private boolean revoked;
	private boolean reported;

	JsonConfigFileGuard(Path path) {
		this(path, false);
	}

	JsonConfigFileGuard(Path path, boolean chaptered) {
		this.path = path.toAbsolutePath().normalize();
		this.chaptered = chaptered;
	}

	synchronized <T> List<T> read(
		@Nullable Integer version,
		Codec<T> codec,
		DynamicOps<JsonElement> ops,
		BiConsumer<JsonElement, ? super DataResult.Error<Pair<T, JsonElement>>> onError,
		BiConsumer<JsonElement, RuntimeException> onException
	) throws IOException {
		JsonArray array = getDocument();
		if (array.isEmpty()) {
			recognized = true;
			return List.of();
		}
		boolean hasVersion = !array.isEmpty() && array.get(0).isJsonObject() && array.get(0).getAsJsonObject().has("version");
		if (version == null ? hasVersion : !hasVersion || !Objects.equals(version, getInteger(array.get(0), "version"))) {
			return List.of();
		}
		recognized = true;
		List<T> entries = new ArrayList<>();
		Set<Integer> chapters = new HashSet<>();
		Set<Integer> groups = new HashSet<>();
		for (int i = hasVersion ? 1 : 0; i < array.size(); i++) {
			JsonElement element = array.get(i);
			boolean chapter = chaptered && hasType(element, "chapter");
			boolean group = chaptered && hasType(element, "group");
			if (chapter || group) {
				Integer id = getInteger(element, "id");
				if (id == null || !(chapter ? chapters : groups).add(id)) {
					incompleteStructure = true;
				}
				if (chapter) {
					groups.clear();
				}
			}
			try {
				DataResult<Pair<T, JsonElement>> decoded = codec.decode(ops, element);
				if (decoded.error().isPresent()) {
					retainedIndexes.set(i);
					// Legacy compressed records cannot be embedded unchanged under the modern version header.
					incompleteStructure |= chapter || group || version == null;
					onError.accept(element, decoded.error().orElseThrow());
				}
				decoded.result().ifPresent(value -> entries.add(value.getFirst()));
			} catch (RuntimeException e) {
				retainedIndexes.set(i);
				incompleteStructure |= chapter || group || version == null;
				onException.accept(element, e);
			}
		}
		return entries;
	}

	private JsonArray getDocument() throws IOException {
		if (document == null) {
			if (!Files.exists(path)) {
				recognized = true;
				document = new JsonArray();
			} else {
				try (BufferedReader reader = Files.newBufferedReader(path)) {
					JsonElement parsed = JsonParser.parseReader(reader);
					if (!parsed.isJsonArray()) {
						throw new JsonSyntaxException("Expected a JSON array in " + path);
					}
					document = parsed.getAsJsonArray();
				} catch (RuntimeException | IOException e) {
					incompleteStructure = true;
					throw e;
				}
			}
		}
		return document;
	}

	private static @Nullable Integer getInteger(JsonElement element, String key) {
		try {
			return Integer.valueOf(element.getAsJsonObject().get(key).getAsString());
		} catch (RuntimeException e) {
			return null;
		}
	}

	synchronized void finishLoading() {
		retained = collectRetainedRecords();
		document = null;
		retainedIndexes.clear();
	}

	synchronized void preventWrites() {
		revoked = true;
	}

	synchronized void preventLossyWrite() {
		incompleteStructure = true;
	}

	synchronized boolean canWrite(Path target) {
		if (revoked || !path.equals(target.toAbsolutePath().normalize())) {
			return false;
		}
		if (recognized && !incompleteStructure) {
			return true;
		}
		if (!reported) {
			reported = true;
			LOGGER.warn("Cannot save {} because its version, structure or conflicting identities could not be loaded safely. The original file is unchanged. Repair the file and reload before saving changes.", path);
		}
		return false;
	}

	synchronized <T> boolean write(Path target, int version, Collection<T> entries, Codec<T> codec, DynamicOps<JsonElement> ops) throws IOException {
		if (!canWrite(target)) {
			return false;
		}
		List<JsonElement> encoded = new ArrayList<>(entries.size());
		for (T entry : entries) {
			DataResult<JsonElement> result = codec.encodeStart(ops, entry);
			encoded.add(result.result().orElseThrow(() -> new IllegalStateException(result.error().orElseThrow().message())));
		}
		List<JsonElement> merged = mergeRetainedRecords(encoded, collectRetainedRecords());
		JsonArrayFileHelper.write(target, version, merged, JSON_CODEC, JsonOps.INSTANCE,
			error -> { throw new IllegalStateException(error.message()); },
			(entry, exception) -> { throw exception; });
		return true;
	}

	private Map<Integer, RetainedChapter> collectRetainedRecords() {
		if (document == null) {
			return retained;
		}
		if (retainedIndexes.isEmpty() || incompleteStructure) {
			return Map.of();
		}
		Map<Integer, RetainedChapter> result = new LinkedHashMap<>();
		int chapterId = 1;
		JsonElement header = null;
		List<JsonElement> groups = new ArrayList<>();
		List<JsonElement> records = new ArrayList<>();
		for (int i = 0; i < document.size(); i++) {
			JsonElement element = document.get(i);
			if (chaptered && hasType(element, "chapter")) {
				if (!records.isEmpty()) {
					result.put(chapterId, new RetainedChapter(header, List.copyOf(groups), List.copyOf(records)));
				}
				chapterId = Objects.requireNonNull(getInteger(element, "id"));
				header = element;
				groups.clear();
				records.clear();
			} else if (chaptered && hasType(element, "group")) {
				groups.add(element);
			} else if (retainedIndexes.get(i)) {
				records.add(element);
			}
		}
		if (!records.isEmpty()) {
			result.put(chapterId, new RetainedChapter(header, List.copyOf(groups), List.copyOf(records)));
		}
		return result;
	}

	private List<JsonElement> mergeRetainedRecords(List<JsonElement> entries, Map<Integer, RetainedChapter> retained) {
		if (retained.isEmpty()) {
			return entries;
		}
		List<JsonElement> result = new ArrayList<>(entries.size());
		Map<Integer, RetainedChapter> remaining = new LinkedHashMap<>(retained);
		Set<Integer> groupIds = new HashSet<>();
		int chapterId = 1;
		for (JsonElement entry : entries) {
			if (chaptered && hasType(entry, "chapter")) {
				Integer nextId = Objects.requireNonNull(getInteger(entry, "id"));
				if (!result.isEmpty()) {
					appendRetained(result, remaining.remove(chapterId), groupIds);
				}
				chapterId = nextId;
				groupIds.clear();
			} else if (chaptered && hasType(entry, "group")) {
				groupIds.add(Objects.requireNonNull(getInteger(entry, "id")));
			}
			result.add(entry);
		}
		if (!entries.isEmpty() || !chaptered) {
			appendRetained(result, remaining.remove(chapterId), groupIds);
		}
		for (var entry : remaining.entrySet()) {
			RetainedChapter chapter = entry.getValue();
			JsonElement header = chapter.header();
			if (header == null) {
				JsonObject defaultChapter = new JsonObject();
				defaultChapter.addProperty("type", "chapter");
				defaultChapter.addProperty("id", entry.getKey());
				header = defaultChapter;
			}
			JsonObject restoredHeader = header.getAsJsonObject().deepCopy();
			restoredHeader.addProperty("active", false);
			result.add(restoredHeader);
			appendRetained(result, chapter, Set.of());
		}
		return result;
	}

	private static void appendRetained(List<JsonElement> entries, @Nullable RetainedChapter chapter, Set<Integer> groupIds) {
		if (chapter != null) {
			for (JsonElement group : chapter.groups()) {
				if (!groupIds.contains(getInteger(group, "id"))) {
					entries.add(group);
				}
			}
			entries.addAll(chapter.records());
		}
	}

	private static boolean hasType(JsonElement element, String type) {
		if (!element.isJsonObject()) {
			return false;
		}
		JsonElement value = element.getAsJsonObject().get("type");
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && type.equals(value.getAsString());
	}

	private record RetainedChapter(@Nullable JsonElement header, List<JsonElement> groups, List<JsonElement> records) {}
}
