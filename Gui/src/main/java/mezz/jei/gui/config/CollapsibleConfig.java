package mezz.jei.gui.config;

import mezz.jei.gui.collapsible.CollapsibleRules;
import mezz.jei.gui.collapsible.CollapsibleRulesSerializer;
import mezz.jei.gui.collapsible.CollapsibleGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

public final class CollapsibleConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String FILE_NAME = "collapsible-items-default.txt";

	private final Path path;

	public CollapsibleConfig(Path jeiConfigurationDir) {
		this.path = jeiConfigurationDir.resolve("collapsible-items");
	}

	public CollapsibleRules load() {
		List<CollapsibleGroup> groups = new ArrayList<>();
		try {
			for (Path file : getFiles()) {
				try {
					groups.addAll(CollapsibleRulesSerializer.deserialize(Files.readAllLines(file), file.toString()));
				} catch (IOException e) {
					LOGGER.error("Failed to read collapsible rules from {}", file, e);
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to load collapsible rules from {}", path, e);
		}
		return new CollapsibleRules(groups);
	}

	public void initialize() {
		try {
			Files.createDirectories(path);
			try (var files = Files.list(path.getParent())) {
				for (Path source : files.filter(Files::isRegularFile).filter(file -> {
					String name = file.getFileName().toString();
					return name.equals("collapsible-items.txt") || name.startsWith("collapsible-items-") && name.endsWith(".txt");
				}).toList()) {
					String name = source.getFileName().toString();
					try {
						Files.move(source, path.resolve(name.equals("collapsible-items.txt") ? FILE_NAME : name));
					} catch (IOException e) {
						LOGGER.error("Failed to migrate collapsible rules {} into {}", source, path, e);
					}
				}
			}
			if (getFiles().isEmpty()) {
				writeDefaultFile();
			}
		} catch (IOException e) {
			LOGGER.error("Failed to initialize collapsible rules directory {}", path, e);
		}
	}

	public Path getDirectory() {
		return path;
	}

	private List<Path> getFiles() throws IOException {
		try (var files = Files.list(path)) {
			return files.filter(Files::isRegularFile)
				.filter(file -> file.getFileName().toString().endsWith(".txt"))
				.sorted(Comparator.comparing((Path file) -> !hasPriority(file))
					.thenComparing(file -> file.getFileName().toString()))
				.toList();
		}
	}

	private static boolean hasPriority(Path file) {
		String name = file.getFileName().toString();
		return name.startsWith("[") && name.indexOf(']') > 1;
	}

	private void writeDefaultFile() {
		List<String> lines = List.of(
			"$ JEI collapsible items rules.",
			"$ Each \"item =\" line defines one fold group; items belong to the first matching group,",
			"$ and items that match no group are shown normally.",
			"$ Expressions support: ! not, & and, | or, ( ) grouping.",
			"$ Selectors: item:id, fluid:id, id, #tag, wildcards like gtceu:*_wire or *:path;",
			"$ tag wildcards like #*:ingots.",
			"$ \"$\" starts a line comment; \"$$ ... $$\" starts a block comment.",
			"$ Place .txt rule files in this folder. Files prefixed with [name] match first;",
			"$ files at the same priority are ordered by filename. All files participate.",
			"$ Default rules are generated at startup only when no .txt files exist.",
			"$ An empty .txt file prevents generation. Configure colors in JEI settings.",
			"",
			"item = minecraft:*_log & !minecraft:stripped_*_log",
			"item = minecraft:stripped_*_log",
			"item = minecraft:*_wood & !minecraft:stripped_*_wood",
			"item = minecraft:stripped_*_wood",
			"item = minecraft:*_planks",
			"item = #minecraft:mineable/axe & #minecraft:stairs",
			"item = #minecraft:wooden_slabs",
			"item = #minecraft:wooden_fences",
			"item = #minecraft:wooden_trapdoors",
			"item = #c:fence_gates",
			"item = #minecraft:wooden_doors",
			"item = #minecraft:*pressure_plates",
			"item = #minecraft:buttons",
			"item = #minecraft:wool",
			"item = #minecraft:wool_carpets",
			"item = minecraft:painting",
			"item = minecraft:*_sign & !minecraft:*_hanging_sign",
			"item = minecraft:*_hanging_sign",
			"item = #minecraft:candles",
			"item = #minecraft:beds",
			"item = #minecraft:pickaxes",
			"item = #minecraft:arrows",
			"item = minecraft:potion",
			"item = minecraft:splash_potion",
			"item = minecraft:lingering_potion",
			"item = minecraft:enchanted_book",
			"item = #minecraft:stairs & #minecraft:mineable/pickaxe",
			"item = #minecraft:walls",
			"item = #minecraft:terracotta",
			"item = #c:concretes",
			"item = #c:concrete_powders",
			"item = #minecraft:leaves",
			"item = #minecraft:banners",
			"item = #minecraft:axes",
			"item = #minecraft:hoes",
			"item = #minecraft:shovels",
			"item = #c:music_discs",
			"item = #minecraft:swords",
			"item = #minecraft:head_armor",
			"item = #minecraft:chest_armor",
			"item = #minecraft:leg_armor",
			"item = #minecraft:foot_armor",
			"item = #minecraft:boats & !#minecraft:chest_boats",
			"item = #minecraft:chest_boats",
			"item = #c:dyes",
			"item = minecraft:suspicious_stew",
			"item = #minecraft:coral_blocks",
			"item = minecraft:dead_*coral_block",
			"item = #minecraft:coral_plants",
			"item = minecraft:*_coral_fan & !minecraft:dead_*_coral_fan",
			"item = minecraft:dead_*_coral_fan",
			"item = minecraft:dead_*_coral",
			"item = #c:glass_blocks",
			"item = #c:glass_panes",
			"item = #c:glazed_terracottas",
			"item = #minecraft:shulker_boxes",
			"item = #minecraft:mineable/pickaxe & #minecraft:slabs",
			"item = minecraft:*_bricks",
			"item = #minecraft:mineable/pickaxe & #minecraft:trapdoors",
			"item = #minecraft:doors & #minecraft:mineable/pickaxe",
			"item = (minecraft:*_copper | minecraft:copper_* | minecraft:*_copper_*) & #minecraft:mineable/pickaxe & !(minecraft:*_ore | minecraft:raw_*_block)",
			"item = minecraft:*_spawn_egg",
			"item = minecraft:*_smithing_template",
			"item = minecraft:*_pottery_sherd",
			"item = #minecraft:flowers & !#minecraft:saplings",
			"item = #minecraft:saplings",
			"item = minecraft:goat_horn",
			"item = minecraft:*_banner_pattern",
			"item = minecraft:ominous_bottle"
		);
		try {
			Files.write(path.resolve(FILE_NAME), lines);
		} catch (IOException e) {
			LOGGER.error("Failed to create default collapsible items config at {}", path, e);
		}
	}
}
