package mezz.jei.test.gui.bookmarks;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.typed;

public class RecipeBookmarkIdentityTest {
	private static final ResourceLocation RECIPE = ResourceLocation.parse("test:plate");
	private static final IIngredientType<String> TYPE = () -> String.class;

	@BeforeAll
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void respectsScope() {
		var unscoped = bookmark(null);
		var first = bookmark("group_1");

		Assertions.assertEquals(unscoped, bookmark(null));
		Assertions.assertEquals(first, bookmark("group_1"));
		Assertions.assertNotEquals(unscoped, first);
		Assertions.assertNotEquals(first, bookmark("group_2"));
	}

	@Test
	public void survivesStackMutation() {
		ItemStack stack = new ItemStack(Items.NETHERITE_BLOCK);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("before"));
		var bookmark = new RecipeBookmark<>(null, "recipe", RECIPE, typed(stack), RecipeIngredientRole.OUTPUT);
		Set<RecipeBookmark<?, ?>> set = new HashSet<>();
		set.add(bookmark);
		Assertions.assertTrue(set.contains(bookmark));

		stack.set(DataComponents.CUSTOM_NAME, Component.literal("after"));

		Assertions.assertTrue(set.contains(bookmark), "HashSet membership must survive ingredient mutation");
	}

	private static RecipeBookmark<String, String> bookmark(@Nullable Object scope) {
		return new RecipeBookmark<>(null, "recipe", RECIPE,
			TypedIngredient.createUnvalidated(TYPE, "plate"), RecipeIngredientRole.OUTPUT, scope);
	}
}
