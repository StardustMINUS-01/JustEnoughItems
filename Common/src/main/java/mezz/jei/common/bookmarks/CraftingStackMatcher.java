package mezz.jei.common.bookmarks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 1.20.1 NBT-based port of the JEI 1.21.1
 * {@code mezz.jei.common.bookmarks.CraftingStackMatcher}. The 1.21.1
 * data-component matching ({@code DataComponentPatch}) is adapted to
 * {@code ItemStack#getTag()} NBT matching.
 */
public final class CraftingStackMatcher {
	private static final List<SpecialItemCompatibility> SPECIAL_ITEM_COMPATIBILITIES = new CopyOnWriteArrayList<>();
	private static final Set<String> NBT_RELAXED_CRAFTING_NAMESPACES = new CopyOnWriteArraySet<>(List.of("mekanism", "gtceu"));

	private CraftingStackMatcher() {
	}

	/**
	 * Forge 1.20.1 extension point for NEI's GT.ToolStats/tool compatibility branch.
	 * Keep this limited to crafting ingredient template matching; server extraction still uses exact stacks.
	 */
	public static void registerSpecialItemCompatibility(SpecialItemCompatibility compatibility) {
		SPECIAL_ITEM_COMPATIBILITIES.add(compatibility);
	}

	/**
	 * User-approved modern compatibility hook for mods whose recipe templates omit volatile capability NBT.
	 * This only affects Shift+C/fill crafting ingredient matching; bookmarks, chat links, pulls, and result insertion stay exact.
	 */
	public static AutoCloseable registerNbtRelaxedCraftingNamespace(String namespace) {
		String normalizedNamespace = normalizeNamespace(namespace);
		boolean added = NBT_RELAXED_CRAFTING_NAMESPACES.add(normalizedNamespace);
		return () -> {
			if (added) {
				NBT_RELAXED_CRAFTING_NAMESPACES.remove(normalizedNamespace);
			}
		};
	}

	public static boolean matchesIngredientTemplate(ItemStack template, ItemStack available) {
		if (template.isEmpty() || available.isEmpty() || !ItemStack.isSameItem(template, available)) {
			return false;
		}
		if (isNbtRelaxedCraftingNamespace(template) || isNbtRelaxedCraftingNamespace(available)) {
			return true;
		}
		// GregTech tools carry volatile tool data (durability, stats) that recipe
		// templates and inventory copies rarely match exactly; any GT tool of the
		// same item id can fill the crafting slot.
		if (isGtTool(template) || isGtTool(available)) {
			return true;
		}
		return matchesNeiIngredientTemplate(template, available);
	}

	private static boolean isGtTool(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag != null && tag.contains("GT.Tool");
	}

	public static boolean matchesExactStack(ItemStack first, ItemStack second) {
		return ItemStack.isSameItemSameTags(first, second);
	}

	public static boolean isNbtRelaxedCraftingNamespace(String namespace) {
		return NBT_RELAXED_CRAFTING_NAMESPACES.contains(normalizeNamespace(namespace));
	}

	private static boolean matchesNeiIngredientTemplate(ItemStack template, ItemStack available) {
		if (matchComponents(template, available)) {
			return true;
		}
		for (SpecialItemCompatibility compatibility : SPECIAL_ITEM_COMPATIBILITIES) {
			if (compatibility.matches(template, available)) {
				return true;
			}
		}
		// Unstackable tools (TConstruct cleavers, GT tools, etc.) carry volatile NBT
		// (durability, stats, modifiers) that recipe templates and inventory copies
		// rarely share exactly. Any tool of the same item id can fill the crafting slot.
		return template.getMaxStackSize() == 1 && available.getMaxStackSize() == 1;
	}

	private static boolean isNbtRelaxedCraftingNamespace(ItemStack stack) {
		ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key != null && NBT_RELAXED_CRAFTING_NAMESPACES.contains(key.getNamespace());
	}

	private static String normalizeNamespace(String namespace) {
		String normalizedNamespace = Objects.requireNonNull(namespace, "namespace").trim().toLowerCase(Locale.ROOT);
		if (normalizedNamespace.isEmpty() || !normalizedNamespace.chars().allMatch(CraftingStackMatcher::isValidNamespaceChar)) {
			throw new IllegalArgumentException("Invalid item namespace: " + namespace);
		}
		return normalizedNamespace;
	}

	private static boolean isValidNamespaceChar(int codePoint) {
		return codePoint >= 'a' && codePoint <= 'z' ||
			codePoint >= '0' && codePoint <= '9' ||
			codePoint == '_' ||
			codePoint == '-' ||
			codePoint == '.';
	}

	/**
	 * 1.20.1 NBT adaptation of the 1.21.1 data-component subset matching: when
	 * the template has no NBT it matches anything, otherwise the target must
	 * carry identical NBT.
	 */
	private static boolean matchComponents(ItemStack template, ItemStack target) {
		CompoundTag templateTag = template.getTag();
		if (templateTag == null) {
			return true;
		}
		CompoundTag targetTag = target.getTag();
		return templateTag.equals(targetTag);
	}

	@FunctionalInterface
	public interface SpecialItemCompatibility {
		boolean matches(ItemStack template, ItemStack available);
	}
}
