package mezz.jei.gui.input.handlers;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IngredientClipboardText {
	private IngredientClipboardText() {
	}

	public static <T> String getIngredientName(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		return typedIngredient.getIngredient(VanillaTypes.ITEM_STACK)
			.map(IngredientClipboardText::getItemStackName)
			.orElseGet(() -> {
				IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
				String displayName = ingredientHelper.getDisplayName(typedIngredient.getIngredient());
				return stripFormatting(displayName);
			});
	}

	public static <T> String getIngredientId(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		return typedIngredient.getIngredient(VanillaTypes.ITEM_STACK)
			.map(IngredientClipboardText::getItemStackId)
			.orElseGet(() -> {
				IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
				ResourceLocation resourceLocation = ingredientHelper.getResourceLocation(typedIngredient.getIngredient());
				return resourceLocation.toString();
			});
	}

	public static <T> String getIngredientTags(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		return formatTagLocations(ingredientHelper.getTagStream(typedIngredient.getIngredient()));
	}

	public static String getItemStackName(ItemStack stack) {
		return stripFormatting(stack.getHoverName().getString());
	}

	public static String getItemStackId(ItemStack stack) {
		ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return resourceLocation.toString();
	}

	public static String formatTagLocations(Stream<ResourceLocation> tags) {
		return tags
			.distinct()
			.map(tag -> "#" + tag)
			.sorted()
			.collect(Collectors.joining(","));
	}

	private static String stripFormatting(String text) {
		String stripped = ChatFormatting.stripFormatting(text);
		return stripped == null ? text : stripped;
	}
}
