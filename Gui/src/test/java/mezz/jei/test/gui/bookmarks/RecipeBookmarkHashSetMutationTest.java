package mezz.jei.test.gui.bookmarks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

public class RecipeBookmarkHashSetMutationTest {
	private static final ResourceLocation RECIPE = ResourceLocation.parse("test:smithing");

	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void mutatingIngredientComponentsBreaksHashSetMembership() {
		ItemStack stack = new ItemStack(Items.NETHERITE_BLOCK);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("before"));
		ITypedIngredient<ItemStack> typed = new ITypedIngredient<>() {
			@Override
			public mezz.jei.api.ingredients.IIngredientType<ItemStack> getType() {
				return VanillaTypes.ITEM_STACK;
			}

			@Override
			public ItemStack getIngredient() {
				return stack;
			}
		};
		RecipeBookmark<String, ItemStack> bookmark = new RecipeBookmark<>(
			null,
			"recipe",
			RECIPE,
			typed,
			RecipeIngredientRole.OUTPUT,
			null
		);

		Set<RecipeBookmark<?, ?>> set = new HashSet<>();
		set.add(bookmark);
		Assertions.assertTrue(set.contains(bookmark));

		// Mutate the shared ingredient stack after insertion.
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("after"));

		Assertions.assertTrue(set.contains(bookmark), "HashSet membership must survive ingredient mutation");
	}
}
