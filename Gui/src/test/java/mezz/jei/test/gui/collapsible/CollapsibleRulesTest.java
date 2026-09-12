package mezz.jei.test.gui.collapsible;

import mezz.jei.gui.collapsible.CollapsibleGroup;
import mezz.jei.gui.collapsible.CollapsibleRulesSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import com.mojang.brigadier.StringReader;
import mezz.jei.gui.match.ComponentPattern;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;

public class CollapsibleRulesTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void matchesComponents() throws Exception {
		ComponentPattern pattern = ComponentPattern.parse(new StringReader(
			"{minecraft:custom_data = {levels:{\"example:*\":*},data:[{id:\"example:*\",level:3}],bytes:[B;*,2b]}}"));
		var base = TagParser.parseTag("{levels:{'example:fire':1},data:[{id:'example:ice',level:3}],bytes:[B;1b,2b]}");
		List<String> changes = List.of("{}", "{levels:{'example:fire':2,'example:ice':3},bytes:[B;5b,2b]}",
			"{levels:{'example:fire':1,'minecraft:sharpness':1}}", "{levels:{}}",
			"{data:[{id:'example:ice',level:3b}]}", "{data:[]}", "{bytes:[I;1,2]}", "{extra:1}");
		for (int i = 0; i < changes.size(); i++) {
			var data = base.copy();
			var changeset = TagParser.parseTag(changes.get(i));
			changeset.getAllKeys().forEach(key -> data.put(key, changeset.get(key)));
			var components = DataComponentMap.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(data)).build();
			Assertions.assertEquals(i < 2, pattern.matches(ComponentPattern.createLookup(components, NbtOps.INSTANCE)), changes.get(i));
		}
		Assertions.assertFalse(pattern.matches(ComponentPattern.createLookup(DataComponentMap.EMPTY, NbtOps.INSTANCE)));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"item = minecraft:potion | minecraft:splash_potion",
		"item =\n  minecraft:potion | minecraft:splash_potion"
	})
	public void parsesCombinedGroup(String text) {
		Assertions.assertEquals(List.of("minecraft:potion | minecraft:splash_potion"), expressions(text.lines().toList()));
	}

	@Test
	public void parsesEntries() {
		Assertions.assertEquals(List.of("minecraft:potion", "minecraft:splash_potion"),
			expressions(List.of("[[group]]", "name = Example", "item = minecraft:potion", "typo = minecraft:dirt",
				"collapsedColor = 0x335555EE", "expandedColor = invalid", "item = foo::bar", "item = minecraft:splash_potion")));
	}

	private static List<String> expressions(List<String> lines) {
		return CollapsibleRulesSerializer.deserialize(lines).stream()
			.map(CollapsibleGroup::expressionText).toList();
	}
}
