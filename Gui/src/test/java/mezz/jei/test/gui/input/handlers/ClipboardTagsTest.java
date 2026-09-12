package mezz.jei.test.gui.input.handlers;

import mezz.jei.gui.input.handlers.IngredientClipboardText;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;
import mezz.jei.gui.config.ConfigLineReader;
import mezz.jei.gui.match.ComponentPattern;
import mezz.jei.gui.match.IngredientExpression;
import mezz.jei.gui.match.IngredientMatchInfo;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;

public class ClipboardTagsTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void copiesComponents() throws Exception {
		ItemStack stack = new ItemStack(Items.PAPER);
		var data = TagParser.parseTag("{text:'a*;$=\"b',values:[I;1,2]}");
		data.putString("multiline", "first  \n\n  second  \n third");
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		String rule = IngredientClipboardText.getComponentRule(stack, NbtOps.INSTANCE, true).orElseThrow();
		var entries = ConfigLineReader.read(rule.lines().toList());
		Assertions.assertEquals(1, entries.size());
		var expression = IngredientExpression.parseIngredient(entries.getFirst().value()).orElseThrow();
		var info = IngredientMatchInfo.item(ResourceLocation.parse("minecraft:paper"), Set.of());
		var lookup = ComponentPattern.createLookup(stack.getComponents(), NbtOps.INSTANCE);
		Assertions.assertTrue(expression.matches(info, lookup));
		Assertions.assertTrue(rule.contains("minecraft:max_stack_size = 64"));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(TagParser.parseTag("{text:'anything',values:[I;1,2]}")));
		Assertions.assertTrue(expression.matches(info, lookup));
		Assertions.assertFalse(expression.matches(info, ComponentPattern.createLookup(stack.getComponents(), NbtOps.INSTANCE)));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		stack.set(DataComponents.MAX_STACK_SIZE, 16);
		stack.remove(DataComponents.RARITY);
		String partial = IngredientClipboardText.getComponentRule(stack, NbtOps.INSTANCE, false).orElseThrow();
		var partialRule = IngredientExpression.parseIngredient(ConfigLineReader.read(partial.lines().toList()).getFirst().value()).orElseThrow();
		var selected = net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(data))
			.set(DataComponents.MAX_STACK_SIZE, 16).build();
		Assertions.assertTrue(partialRule.matches(info, ComponentPattern.createLookup(selected, NbtOps.INSTANCE)));
		Assertions.assertFalse(partialRule.matches(info, ComponentPattern.createLookup(new ItemStack(Items.PAPER).getComponents(), NbtOps.INSTANCE)));
		Assertions.assertTrue(partial.contains("minecraft:max_stack_size = 16"));
		Assertions.assertEquals("item = minecraft:paper", IngredientClipboardText.getComponentRule(new ItemStack(Items.PAPER), NbtOps.INSTANCE, false).orElseThrow());
	}
	@Test
	public void formatsTags() {
		List<String> text = IngredientClipboardText.formatTagLocations(Stream.of(
			ResourceLocation.parse("minecraft:planks"),
			ResourceLocation.parse("minecraft:mineable/pickaxe"),
			ResourceLocation.parse("minecraft:planks")
		));
		Assertions.assertEquals(List.of("#minecraft:mineable/pickaxe", "#minecraft:planks"), text);
		Assertions.assertTrue(IngredientClipboardText.formatTagLocations(Stream.empty()).isEmpty());
	}
}
