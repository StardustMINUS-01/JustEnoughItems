package mezz.jei.gui.config;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.codecs.EnumCodec;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientAmountResolver;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class BookmarkConfigEntryCodec {
	private static final String TYPE_GROUP = "group";
	private static final Codec<String> GROUP_TYPE_CODEC = Codec.STRING.flatXmap(
		type -> TYPE_GROUP.equals(type) ?
			DataResult.success(type) :
			DataResult.error(() -> "Unknown bookmark config entry type: " + type),
		DataResult::success
	);
	private static final Codec<Set<ResourceLocation>> RESOURCE_LOCATION_SET_CODEC = ResourceLocation.CODEC.listOf()
		.xmap(LinkedHashSet::new, List::copyOf);

	public static final Codec<BookmarkGroup> GROUP_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		GROUP_TYPE_CODEC.fieldOf("type").forGetter(group -> TYPE_GROUP),
		Codec.INT.fieldOf("id").forGetter(BookmarkGroup::id),
		Codec.STRING.fieldOf("title").forGetter(BookmarkGroup::title),
		EnumCodec.create(BookmarkViewMode.class).optionalFieldOf("viewMode", BookmarkViewMode.DEFAULT).forGetter(BookmarkGroup::viewMode),
		Codec.BOOL.optionalFieldOf("collapsed", false).forGetter(BookmarkGroup::collapsed),
		Codec.BOOL.optionalFieldOf("crafting", false).forGetter(BookmarkGroup::craftingMode),
		RESOURCE_LOCATION_SET_CODEC.optionalFieldOf("collapsedRecipes", Set.of()).forGetter(BookmarkGroup::collapsedRecipeIds)
	).apply(instance, (type, id, title, viewMode, collapsed, crafting, collapsedRecipes) ->
		new BookmarkGroup(id, title, viewMode, collapsed, crafting, collapsedRecipes)
	));

	private BookmarkConfigEntryCodec() {
	}

	public static Codec<BookmarkConfigEntry> create(
		ICodecHelper codecHelper,
		IIngredientManager ingredientManager,
		Codec<IBookmark> bookmarkCodec
	) {
		Codec<ITypedIngredient<?>> typedIngredientCodec = codecHelper.getTypedIngredientCodec().codec();
		Codec<BookmarkIngredientKey> keyCodec = createIngredientKeyCodec(typedIngredientCodec, ingredientManager);
		Codec<ForkData> forkDataCodec = createForkDataCodec(typedIngredientCodec, keyCodec);
		Codec<BookmarkConfigEntry> bookmarkEntryCodec = Codec.pair(
			bookmarkCodec,
			forkDataCodec.optionalFieldOf("forkData").codec()
		).flatXmap(
			pair -> decodeBookmark(pair.getFirst(), pair.getSecond().orElse(null), ingredientManager),
			entry -> {
				ForkData forkData = ForkData.create(entry, ingredientManager);
				Optional<ForkData> encodedForkData = forkData.isDefaultIngredient(entry.bookmark()) ?
					Optional.empty() :
					Optional.of(forkData);
				return DataResult.success(Pair.of(normalizeIngredientBookmark(entry.bookmark(), ingredientManager), encodedForkData));
			}
		);
		return Codec.either(GROUP_CODEC, bookmarkEntryCodec)
			.xmap(
				either -> either.map(BookmarkConfigEntry::group, entry -> entry),
				entry -> entry.group() != null ? Either.left(entry.group()) : Either.right(entry)
			);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static IBookmark normalizeIngredientBookmark(IBookmark bookmark, IIngredientManager ingredientManager) {
		if (bookmark instanceof IngredientBookmark ingredientBookmark) {
			return IngredientBookmark.create(ingredientBookmark.getIngredient(), ingredientManager);
		}
		return bookmark;
	}

	static Codec<BookmarkIngredientKey> createIngredientKeyCodec(
		Codec<ITypedIngredient<?>> typedIngredientCodec,
		IIngredientManager ingredientManager
	) {
		return typedIngredientCodec.flatXmap(
			ingredient -> DataResult.success(BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager)),
			key -> Optional.ofNullable(key.typedIngredient())
				.map(DataResult::success)
				.orElseGet(() -> DataResult.error(() -> "Bookmark ingredient has no typed value: " + key.stableKey()))
		);
	}

	private static Codec<ForkData> createForkDataCodec(
		Codec<ITypedIngredient<?>> typedIngredientCodec,
		Codec<BookmarkIngredientKey> keyCodec
	) {
		return RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("group", BookmarkGroupManager.DEFAULT_GROUP_ID).forGetter(ForkData::groupId),
			EnumCodec.create(BookmarkItemType.class).optionalFieldOf("kind", BookmarkItemType.ITEM).forGetter(ForkData::type),
			typedIngredientCodec.optionalFieldOf("displayIngredient").forGetter(ForkData::displayIngredient),
			Codec.LONG.optionalFieldOf("amount", 1L).forGetter(ForkData::amount),
			Codec.LONG.optionalFieldOf("multiplier", 1L).forGetter(ForkData::multiplier),
			Codec.LONG.optionalFieldOf("factor", 1L).forGetter(ForkData::factor),
			Codec.LONG.optionalFieldOf("chance", BookmarkItemMetadata.CHANCE_FULL).forGetter(ForkData::chance),
			keyCodec.listOf().optionalFieldOf("permutations", List.of()).forGetter(ForkData::permutations),
			keyCodec.optionalFieldOf("containerItem").forGetter(ForkData::containerItem),
			Codec.LONG.optionalFieldOf("containerItemCraftingUses", 1L).forGetter(ForkData::containerItemCraftingUses),
			keyCodec.optionalFieldOf("brokenContainerItem").forGetter(ForkData::brokenContainerItem)
		).apply(instance, ForkData::new));
	}

	private static DataResult<BookmarkConfigEntry> decodeBookmark(
		IBookmark decodedBookmark,
		ForkData forkData,
		IIngredientManager ingredientManager
	) {
		ForkData data = forkData == null ? ForkData.createDefault(decodedBookmark, ingredientManager) : forkData;
		IBookmark bookmark = applyForkData(decodedBookmark, data, ingredientManager);
		ResourceLocation recipeTypeUid = null;
		ResourceLocation recipeUid = null;
		if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
			recipeTypeUid = recipeBookmark.getRecipeCategory().getRecipeType().getUid();
			recipeUid = recipeBookmark.getRecipeUid();
		}
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			data.groupId(),
			data.type(),
			data.multiplier(),
			data.factor(),
			data.chance(),
			recipeTypeUid,
			recipeUid,
			new LinkedHashSet<>(data.permutations()),
			data.containerItem().orElse(null),
			data.containerItemCraftingUses(),
			data.brokenContainerItem().orElse(null)
		);
		return DataResult.success(BookmarkConfigEntry.bookmark(bookmark, metadata));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static IBookmark applyForkData(IBookmark bookmark, ForkData data, IIngredientManager ingredientManager) {
		if (bookmark instanceof IngredientBookmark ingredientBookmark) {
			bookmark = IngredientBookmark.createWithAmount(ingredientBookmark.getIngredient(), data.amount(), ingredientManager);
		} else if (bookmark instanceof RecipeBookmark recipeBookmark && data.displayIngredient().isPresent()) {
			RecipeIngredientRole role = data.type() == BookmarkItemType.RESULT ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT;
			bookmark = new RecipeBookmark(
				recipeBookmark.getRecipeCategory(),
				recipeBookmark.getRecipe(),
				recipeBookmark.getRecipeUid(),
				data.displayIngredient().get(),
				role
			);
		}
		if (data.groupId() != BookmarkGroupManager.DEFAULT_GROUP_ID) {
			if (bookmark instanceof IngredientBookmark<?> ingredientBookmark) {
				bookmark = ingredientBookmark.withEqualityScope(data.groupId());
			} else if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
				bookmark = recipeBookmark.withEqualityScope(data.groupId());
			}
		}
		return bookmark;
	}

	private record ForkData(
		int groupId,
		BookmarkItemType type,
		Optional<ITypedIngredient<?>> displayIngredient,
		long amount,
		long multiplier,
		long factor,
		long chance,
		List<BookmarkIngredientKey> permutations,
		Optional<BookmarkIngredientKey> containerItem,
		long containerItemCraftingUses,
		Optional<BookmarkIngredientKey> brokenContainerItem
	) {
		private static ForkData create(BookmarkConfigEntry entry, IIngredientManager ingredientManager) {
			IBookmark bookmark = entry.bookmark();
			BookmarkItemMetadata metadata = entry.metadata();
			Optional<ITypedIngredient<?>> displayIngredient = bookmark instanceof RecipeBookmark<?, ?> recipeBookmark ?
				Optional.of(recipeBookmark.getRecipeOutput()) : Optional.empty();
			long amount = bookmark instanceof IngredientBookmark<?> ?
				BookmarkIngredientAmountResolver.getAmount(bookmark.getElement().getTypedIngredient(), ingredientManager) : 1;
			return new ForkData(
				metadata.groupId(),
				metadata.type(),
				displayIngredient,
				amount,
				metadata.multiplier(),
				metadata.factor(),
				metadata.chance(),
				List.copyOf(metadata.permutations()),
				Optional.ofNullable(metadata.containerItem()),
				metadata.containerItemCraftingUses(),
				Optional.ofNullable(metadata.brokenContainerItem())
			);
		}

		private static ForkData createDefault(IBookmark bookmark, IIngredientManager ingredientManager) {
			BookmarkItemType type = bookmark instanceof RecipeBookmark<?, ?> recipeBookmark ?
				BookmarkItemType.fromRecipeRole(recipeBookmark.getDisplayRole()) : BookmarkItemType.ITEM;
			Optional<ITypedIngredient<?>> displayIngredient = bookmark instanceof RecipeBookmark<?, ?> recipeBookmark ?
				Optional.of(recipeBookmark.getRecipeOutput()) : Optional.empty();
			long amount = bookmark instanceof IngredientBookmark<?> ?
				BookmarkIngredientAmountResolver.getAmount(bookmark.getElement().getTypedIngredient(), ingredientManager) : 1;
			return new ForkData(0, type, displayIngredient, amount, 1, 1, BookmarkItemMetadata.CHANCE_FULL, List.of(), Optional.empty(), 1, Optional.empty());
		}

		private boolean isDefaultIngredient(IBookmark bookmark) {
			return bookmark instanceof IngredientBookmark<?> &&
				groupId == BookmarkGroupManager.DEFAULT_GROUP_ID &&
				type == BookmarkItemType.ITEM &&
				amount == 1 &&
				multiplier == 1 &&
				factor == 1 &&
				chance == BookmarkItemMetadata.CHANCE_FULL &&
				permutations.isEmpty() &&
				containerItem.isEmpty() &&
				containerItemCraftingUses == 1 &&
				brokenContainerItem.isEmpty();
		}
	}
}
