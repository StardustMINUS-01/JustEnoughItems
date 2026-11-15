package mezz.jei.test;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.common.recipes.RecipeIdentifierLookup;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeIdentifierLookupTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void tippedArrowAliasesRequireOneMatchingOutputAndExactIdsWin() {
		ItemStack output = new ItemStack(Items.TIPPED_ARROW);
		output.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
		CraftingRecipe recipe = new ShapelessRecipe("test", CraftingBookCategory.MISC, output, NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.ARROW)));
		RecipeHolder<CraftingRecipe> current = new RecipeHolder<>(ResourceLocation.parse("minecraft:/jei.tipped.arrow.minecraft.poison"), recipe);
		ResourceLocation oldId = ResourceLocation.withDefaultNamespace("jei.tipped.arrow." + output.getDescriptionId());
		IRecipeCategory<RecipeHolder<CraftingRecipe>> category = category(RecipeTypes.CRAFTING, RecipeHolder::id);
		IRecipeManager manager = ingredientManager(value -> ((RecipeHolder<?>) value).value().getResultItem(RegistryAccess.EMPTY));
		assertSame(current, RecipeIdentifierLookup.findRecipe(category, lookup(List.of(current)), oldId, manager).orElseThrow());
		assertSame(current, RecipeIdentifierLookup.findRecipe(category, lookup(List.of(current)), oldId.withPrefix("/"), manager).orElseThrow());
		RecipeHolder<CraftingRecipe> ambiguous = new RecipeHolder<>(ResourceLocation.parse("minecraft:/jei.tipped.arrow.other.poison"), recipe);
		assertTrue(RecipeIdentifierLookup.findRecipe(category, lookup(List.of(current, ambiguous)), oldId, manager).isEmpty());
		RecipeHolder<CraftingRecipe> exact = new RecipeHolder<>(oldId, recipe);
		assertSame(exact, RecipeIdentifierLookup.findRecipe(category, lookup(List.of(current, exact)), oldId, manager).orElseThrow());
		assertEquals(oldId, exact.id());
	}

	@Test
	void grindstoneAliasesRejectTheOldMissingNamespaceCollision() {
		IJeiGrindstoneRecipe vanilla = grindstone("minecraft");
		IJeiGrindstoneRecipe modded = grindstone("other");
		ResourceLocation oldId = ResourceLocation.withDefaultNamespace("grindstone.disenchantment." + Items.DIAMOND_SWORD.getDescriptionId() + ".sharpness.1");
		IRecipeCategory<IJeiGrindstoneRecipe> category = category(RecipeTypes.GRINDSTONE, IJeiGrindstoneRecipe::getUid);
		assertSame(vanilla, RecipeIdentifierLookup.findRecipe(category, lookup(List.of(vanilla)), oldId, null).orElseThrow());
		assertTrue(RecipeIdentifierLookup.findRecipe(category, lookup(List.of(vanilla, modded)), oldId, null).isEmpty());
		assertSame(modded, RecipeIdentifierLookup.findRecipe(category, lookup(List.of(vanilla, modded)), modded.getUid(), null).orElseThrow());
	}

	@SuppressWarnings("unchecked")
	private static IJeiGrindstoneRecipe grindstone(String namespace) {
		ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(namespace, "sharpness"));
		Holder<Enchantment> enchantment = (Holder<Enchantment>) Proxy.newProxyInstance(Holder.class.getClassLoader(), new Class<?>[]{Holder.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "unwrapKey" -> Optional.of(key);
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> throw new AssertionError(method.getName());
			});
		ItemStack input = new ItemStack(Items.DIAMOND_SWORD);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(enchantment, 1);
		input.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
		return new IJeiGrindstoneRecipe() {
			@Override
			public List<ItemStack> getTopInputs() { return List.of(input); }
			@Override
			public List<ItemStack> getBottomInputs() { return List.of(ItemStack.EMPTY); }
			@Override
			public List<ItemStack> getOutputs() { return List.of(new ItemStack(Items.DIAMOND_SWORD)); }
			@Override
			public int getMinXpReward() { return 0; }
			@Override
			public int getMaxXpReward() { return 0; }
			@Override
			public ResourceLocation getUid() { return ResourceLocation.withDefaultNamespace("/grindstone.disenchantment." + namespace + ".sharpness.1"); }
			@Override
			public boolean isOutputRenderOnly() { return true; }
		};
	}

	@SuppressWarnings("unchecked")
	private static <T> IRecipeCategory<T> category(RecipeType<T> type, Function<T, ResourceLocation> id) {
		return (IRecipeCategory<T>) Proxy.newProxyInstance(IRecipeCategory.class.getClassLoader(), new Class<?>[]{IRecipeCategory.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getRecipeType" -> type;
				case "getRegistryName" -> id.apply((T) args[0]);
				default -> throw new AssertionError(method.getName());
			});
	}

	private static IRecipeManager ingredientManager(Function<Object, ItemStack> output) {
		return (IRecipeManager) Proxy.newProxyInstance(IRecipeManager.class.getClassLoader(), new Class<?>[]{IRecipeManager.class},
			(proxy, method, args) -> {
				if (!method.getName().equals("getRecipeIngredients")) {
					throw new AssertionError(method.getName());
				}
				return (IIngredientSupplier) role -> role == RecipeIngredientRole.OUTPUT
					? List.of(TypedIngredient.createUnvalidated(VanillaTypes.ITEM_STACK, output.apply(args[1]))) : List.of();
			});
	}

	private static <T> IRecipeLookup<T> lookup(List<T> recipes) {
		return new IRecipeLookup<>() {
			@Override
			public IRecipeLookup<T> limitFocus(Collection<? extends IFocus<?>> focuses) { return this; }
			@Override
			public IRecipeLookup<T> includeHidden() { return this; }
			@Override
			public Stream<T> get() { return recipes.stream(); }
		};
	}
}
