package mezz.jei.neoforge.compat.ae2;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridge.SendResult;
import mezz.jei.gui.compat.ae2.JeiPatternCatalyst;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeRequest;
import mezz.jei.gui.compat.ae2.JeiPatternStack;
import mezz.jei.neoforge.compat.ae2.patternencoding.PacketPatternEncodingResult;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PatternRequestConversionTest {
	@BeforeAll
	static void bootstrap() {
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void reportsInvalidCatalystsOncePerBatch(boolean mixed) {
		var type = ResourceLocation.parse("test:processing");
		var item = new JeiPatternStack(JeiPatternStack.Kind.ITEM, TypedIngredient.createUnvalidated(VanillaTypes.ITEM_STACK, new ItemStack(Items.STONE)), 1);
		var unsupported = new JeiPatternStack(JeiPatternStack.Kind.FLUID, item.ingredient(), 1);
		var invalid = new JeiPatternEncodeRequest(type, ResourceLocation.parse("test:bad"), JeiPatternEncodeMode.PROCESSING,
			List.of(item), List.of(item), List.of(new JeiPatternCatalyst(0, unsupported)), List.of(), null, false, false);
		var valid = new JeiPatternEncodeRequest(type, ResourceLocation.parse("test:good"), JeiPatternEncodeMode.PROCESSING,
			List.of(item), List.of(item), List.of(), List.of(), null, false, false);
		List<PacketPatternEncodingResult> local = new ArrayList<>();
		List<ResourceLocation> sent = new ArrayList<>();
		var outcome = Ae2RecipeChainPatternEncodingBridge.convertAndSend(mixed ? List.of(valid, invalid) : List.of(invalid), (converted, failures) -> {
			Assertions.assertEquals(List.of(invalid.recipeUid()), failures.stream().map(entry -> entry.recipeUid()).toList());
			converted.forEach(request -> sent.add(request.recipeUid()));
		}, local::add);
		Assertions.assertEquals(mixed ? SendResult.SENT : SendResult.INVALID_RECIPES, outcome);
		Assertions.assertEquals(mixed ? List.of(valid.recipeUid()) : List.of(), sent);
		Assertions.assertEquals(mixed ? 0 : 1, local.size());
	}
}
