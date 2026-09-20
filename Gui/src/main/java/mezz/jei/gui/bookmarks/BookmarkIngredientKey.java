package mezz.jei.gui.bookmarks;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.CompoundTag;
import mezz.jei.common.platform.IPlatformFluidHelperInternal;
import mezz.jei.common.platform.Services;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.ingredients.ITypedIngredient;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mezz.jei.common.bookmarks.CraftingStackMatcher;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record BookmarkIngredientKey(
	String ingredientTypeUid,
	String ingredientUid,
	@Nullable String serializedIngredient,
	@Nullable ITypedIngredient<?> typedIngredient
) implements Comparable<BookmarkIngredientKey> {
	public static final String LEGACY_TYPE_UID = "legacy";
	public static final String UNKNOWN_TYPE_UID = "unknown";
	private static final String ITEM_STACK_TYPE_UID = "minecraft:item_stack";

	public BookmarkIngredientKey {
		ingredientTypeUid = clean(ingredientTypeUid, UNKNOWN_TYPE_UID);
		if ("item_stack".equals(ingredientTypeUid)) {
			ingredientTypeUid = ITEM_STACK_TYPE_UID;
		}
		serializedIngredient = serializedIngredient == null || serializedIngredient.isBlank() ? null : serializedIngredient;
		ingredientUid = clean(ingredientUid, "fallback:unknown");
	}

	public BookmarkIngredientKey(String ingredientTypeUid, String ingredientUid) {
		this(ingredientTypeUid, ingredientUid, null, null);
	}

	public BookmarkIngredientKey(String ingredientTypeUid, String ingredientUid, @Nullable String serializedIngredient) {
		this(ingredientTypeUid, ingredientUid, serializedIngredient, null);
	}

	public static BookmarkIngredientKey of(String ingredientTypeUid, String ingredientUid) {
		return new BookmarkIngredientKey(ingredientTypeUid, ingredientUid, null);
	}

	public static BookmarkIngredientKey fallback(String ingredientUid) {
		return new BookmarkIngredientKey(UNKNOWN_TYPE_UID, ingredientUid, null);
	}

	public static BookmarkIngredientKey legacy(String value) {
		int separatorIndex = value.indexOf('|');
		if (separatorIndex > 0 && separatorIndex + 1 < value.length()) {
			return new BookmarkIngredientKey(value.substring(0, separatorIndex), value.substring(separatorIndex + 1), null);
		}
		return new BookmarkIngredientKey(LEGACY_TYPE_UID, value, null);
	}

	public String stableKey() {
		return ingredientTypeUid + "|" + ingredientUid;
	}

	@SuppressWarnings({"removal", "rawtypes", "unchecked"})
	public Optional<ITypedIngredient<?>> resolveIngredient(IIngredientManager manager) {
		if (typedIngredient != null) {
			return Optional.of(typedIngredient);
		}
		if (serializedIngredient != null && ITEM_STACK_TYPE_UID.equals(ingredientTypeUid)) {
			// The 1.20.1 save format stores raw ItemStack SNBT, not an ingredient UID string.
			try {
				var stack = ItemStack.of(TagParser.parseTag(serializedIngredient));
				return manager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false).map(value -> value);
			} catch (CommandSyntaxException | RuntimeException e) {
				return Optional.empty();
			}
		}
		if (serializedIngredient != null && "fluid_stack".equals(ingredientTypeUid)) {
			try {
				return resolveFluidSnapshot(manager, Services.PLATFORM.getFluidHelper(), TagParser.parseTag(serializedIngredient));
			} catch (CommandSyntaxException | RuntimeException e) {
				// Saved snapshots may be malformed or refer to a removed mod.
				return Optional.empty();
			}
		}
		return manager.getIngredientTypeForUid(ingredientTypeUid)
			.flatMap(type -> manager.getTypedIngredientByUid((IIngredientType) type, ingredientUid))
			.map(value -> (ITypedIngredient<?>) value);
	}

	private static <T> Optional<ITypedIngredient<?>> resolveFluidSnapshot(
		IIngredientManager manager,
		IPlatformFluidHelperInternal<T> helper,
		CompoundTag snapshot
	) {
		long amount = snapshot.getLong("Amount");
		if (amount <= 0 || snapshot.contains("Tag") && !snapshot.contains("Tag", Tag.TAG_COMPOUND)) {
			return Optional.empty();
		}
		return BuiltInRegistries.FLUID.getOptional(new ResourceLocation(snapshot.getString("FluidName")))
			.filter(fluid -> fluid != Fluids.EMPTY)
			.flatMap(fluid -> {
				T value = helper.create(fluid, amount, snapshot.contains("Tag") ? snapshot.getCompound("Tag") : null);
				if (helper.getAmount(value) != amount) {
					return Optional.empty();
				}
				return manager.createTypedIngredient(helper.getFluidIngredientType(), value, false).map(ingredient -> ingredient);
			});
	}

	public boolean matchesCraftingAvailable(BookmarkIngredientKey available) {
		if (equals(available)) {
			return true;
		}
		if (!ITEM_STACK_TYPE_UID.equals(ingredientTypeUid) ||
			!ITEM_STACK_TYPE_UID.equals(available.ingredientTypeUid)
		) {
			return false;
		}
		String requiredBase = itemRegistryUid(ingredientUid);
		if (!requiredBase.equals(itemRegistryUid(available.ingredientUid))) {
			return false;
		}
		return itemNamespace(requiredBase)
			.map(CraftingStackMatcher::isNbtRelaxedCraftingNamespace)
			.orElse(false);
	}

	public BookmarkIngredientKey getCraftingAvailabilityKey() {
		if (!ITEM_STACK_TYPE_UID.equals(ingredientTypeUid)) {
			return this;
		}
		String baseUid = itemRegistryUid(ingredientUid);
		if (itemNamespace(baseUid)
			.map(CraftingStackMatcher::isNbtRelaxedCraftingNamespace)
			.orElse(false)
		) {
			return new BookmarkIngredientKey(ingredientTypeUid, baseUid);
		}
		return this;
	}

	@Override
	public int compareTo(BookmarkIngredientKey other) {
		int type = ingredientTypeUid.compareTo(other.ingredientTypeUid);
		if (type != 0) {
			return type;
		}
		return ingredientUid.compareTo(other.ingredientUid);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof BookmarkIngredientKey other &&
			ingredientTypeUid.equals(other.ingredientTypeUid) &&
			ingredientUid.equals(other.ingredientUid);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ingredientTypeUid, ingredientUid);
	}

	private static String clean(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String itemRegistryUid(String uid) {
		int namespaceSeparator = uid.indexOf(':');
		if (namespaceSeparator < 0) {
			return uid;
		}
		int subtypeSeparator = uid.indexOf(':', namespaceSeparator + 1);
		if (subtypeSeparator < 0) {
			return uid;
		}
		return uid.substring(0, subtypeSeparator);
	}

	private static Optional<String> itemNamespace(String uid) {
		int namespaceSeparator = uid.indexOf(':');
		if (namespaceSeparator <= 0) {
			return Optional.empty();
		}
		return Optional.of(uid.substring(0, namespaceSeparator));
	}
}
