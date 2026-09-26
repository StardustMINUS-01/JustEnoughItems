package mezz.jei.gui.recipes.filtering;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable runtime snapshots shared by background filtering and automatic recipe selection. */
public final class RecipeCategoryPreferences {
	private static volatile State state = new State(Set.of(), Set.of(), Set.of());
	private static @Nullable Path path;
	private static Runnable changed = () -> {};
	private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ResourceLocation.CODEC.fieldOf("category").forGetter(Entry::category),
			Codec.BOOL.optionalFieldOf("disabled", false).forGetter(Entry::disabled),
			Codec.BOOL.optionalFieldOf("preferred", false).forGetter(Entry::preferred)
		)
		.apply(instance, Entry::new));

	private RecipeCategoryPreferences() {}

	public static State get() { return state; }

	public static void clear() {
		state = new State(Set.of(), Set.of(), Set.of());
		path = null;
		changed = () -> {};
	}

	public static void load(Path configFile, FavoriteRecipeStore favorites, Runnable onChange) {
		changed = onChange;
		path = configFile;
		Set<ResourceLocation> disabled = new HashSet<>(), preferred = new HashSet<>();
		if (path != null && Files.exists(path)) {
			try (var reader = Files.newBufferedReader(path)) {
				for (Entry entry : JsonArrayFileHelper.read(reader, 1, CODEC, JsonOps.INSTANCE,
					(value, error) -> LogManager.getLogger().warn("Invalid recipe category preference {}: {}", value, error),
					(value, exception) -> LogManager.getLogger().warn("Invalid recipe category preference {}", value, exception))) {
					if (entry.disabled())
						disabled.add(entry.category());
					if (entry.preferred())
						preferred.add(entry.category());
				}
			} catch (IOException | RuntimeException e) {
				LogManager.getLogger().error("Failed to load recipe category preferences from {}", path, e);
			}
		}
		state = new State(disabled, preferred, Set.of());
		refreshFavorites(favorites);
	}

	public static void refreshFavorites(FavoriteRecipeStore favorites) {
		Set<FocusedRecipe> manual = favorites.entries().stream().map(FavoriteRecipeStore.Entry::recipe).collect(Collectors.toSet());
		State previous = state;
		if (!previous.manual().equals(manual))
			state = new State(previous.disabled(), previous.preferred(), manual);
	}

	public static void toggle(ResourceLocation category, boolean disable) {
		State previous = state;
		Set<ResourceLocation> disabled = new HashSet<>(previous.disabled()), preferred = new HashSet<>(previous.preferred());
		Set<ResourceLocation> target = disable ? disabled : preferred;
		if (!target.remove(category))
			target.add(category);
		state = new State(disabled, preferred, previous.manual());
		changed.run();
		if (path == null)
			return;
		Set<ResourceLocation> ids = new HashSet<>(disabled);
		ids.addAll(preferred);
		List<Entry> entries = ids.stream().sorted().map(id -> new Entry(id, disabled.contains(id), preferred.contains(id))).toList();
		try {
			Files.createDirectories(path.getParent());
			JsonArrayFileHelper.write(path, 1, entries, CODEC, JsonOps.INSTANCE,
				error -> { throw new IllegalStateException(error.message()); },
				(entry, exception) -> { throw exception; });
		} catch (IOException | RuntimeException e) {
			LogManager.getLogger().error("Failed to save recipe category preferences to {}", path, e);
		}
	}

	public record State(Set<ResourceLocation> disabled, Set<ResourceLocation> preferred, Set<FocusedRecipe> manual) {
		public State {
			disabled = Set.copyOf(disabled);
			preferred = Set.copyOf(preferred);
			manual = Set.copyOf(manual);
		}
		public boolean allows(ResourceLocation category, RecipeFilterMode mode) {
			return mode == RecipeFilterMode.ALL || disabled.contains(category) == (mode == RecipeFilterMode.DISABLED);
		}
		public boolean isPreferred(ResourceLocation category, FocusedRecipe recipe, Set<FocusedRecipe> rules) {
			return preferred.contains(category) || recipe != null && (manual.contains(recipe) || rules.contains(recipe));
		}
	}

	private record Entry(ResourceLocation category, boolean disabled, boolean preferred) {}
}
