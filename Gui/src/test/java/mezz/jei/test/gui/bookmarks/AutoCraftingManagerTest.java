package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.AutoCraftingManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AutoCraftingManagerTest {
	private static final ResourceLocation CRAFTING = new ResourceLocation("minecraft", "crafting");
	private static final ResourceLocation C_RECIPE = new ResourceLocation("test", "c");
	private static final ResourceLocation E_RECIPE = new ResourceLocation("test", "e");
	private static final ResourceLocation WET_PROCESSOR = new ResourceLocation("test", "wetware_processor");
	private static final ResourceLocation WET_ASSEMBLY = new ResourceLocation("test", "wetware_processor_assembly");
	private static final ResourceLocation WET_COMPUTER = new ResourceLocation("test", "wetware_processor_computer");
	private static final ResourceLocation WET_MAINFRAME = new ResourceLocation("test", "wetware_processor_mainframe");

	@Test
	public void shiftCraftAllDoesNotCraftAnotherTargetWhenMaterialsRemain() {
		TestInventory inventory = chainInventory();
		RecipeChainMath math = chainMath();
		math.expandRootDemandForCraftAll(inventory.snapshot());

		AutoCraftingManager.Result result = AutoCraftingManager.run(
			math,
			List.of(),
			inventory::snapshot,
			inventory::craft
		);

		Assertions.assertTrue(result.completed());
		Assertions.assertEquals(1, inventory.amount(key("e")));
		Assertions.assertEquals(List.of(
			new Craft(C_RECIPE, 2),
			new Craft(E_RECIPE, 1)
		), inventory.crafted);
	}

	@Test
	public void controlShiftCraftMissingDoesNotCraftAnotherTargetWhenMaterialsRemain() {
		TestInventory inventory = chainInventory();

		AutoCraftingManager.Result result = AutoCraftingManager.run(
			chainMath(),
			List.of(),
			inventory::snapshot,
			inventory::craft
		);

		Assertions.assertTrue(result.completed());
		Assertions.assertEquals(1, inventory.amount(key("e")));
		Assertions.assertEquals(List.of(
			new Craft(C_RECIPE, 2),
			new Craft(E_RECIPE, 1)
		), inventory.crafted);
	}

	@Test
	public void wetwareStyleChainCraftsEachRecipeExactlyOncePerDispatch() {
		// mclo.gs/3jufJAL 复现: 4 层 KJS 链 (8 crystal -> 8 wp -> 8 wa -> 8 wc -> 8 wm)。
		// 1.20.1 平台差异: 类型 uid 是 "item_stack", 输出快照 Count:8b / 输入快照 Count:1b。
		// 修复前中间产物无法连接到生产配方, 每层被当作独立目标反复合成;
		// 迁移 1.21.1 计算逻辑并适配后, 每层只合成一次, 中间产物全部被上层消耗。
		RecipeChainMath math = RecipeChainMath.of(List.of(
			input(0, result(WET_PROCESSOR, key8("wetware_processor"), 8, 1)),
			input(1, ingredient(WET_PROCESSOR, key1("crystal_processor_mainframe"), 8)),
			input(2, result(WET_ASSEMBLY, key8("wetware_processor_assembly"), 8, 1)),
			input(3, ingredient(WET_ASSEMBLY, key1("wetware_processor"), 8)),
			input(4, result(WET_COMPUTER, key8("wetware_processor_computer"), 8, 1)),
			input(5, ingredient(WET_COMPUTER, key1("wetware_processor_assembly"), 8)),
			input(6, result(WET_MAINFRAME, key8("wetware_processor_mainframe"), 8, 1)),
			input(7, ingredient(WET_MAINFRAME, key1("wetware_processor_computer"), 8))
		), Set.of());
		math.createMasterRoot();

		TestInventory inventory = new TestInventory();
		inventory.add(key1("crystal_processor_mainframe"), 64);

		// 模拟 BookmarkAutoCraftingBridge: 每次 AutoCraftingManager.run 只派发一个合成包,
		// 服务端 ack + 客户端库存同步后再派发下一个。
		int guard = 0;
		while (guard++ < 12) {
			java.util.concurrent.atomic.AtomicBoolean crafted = new java.util.concurrent.atomic.AtomicBoolean(false);
			AutoCraftingManager.Result result = AutoCraftingManager.run(
				math,
				List.of(),
				inventory::snapshot,
				(recipeUid, multiplier) -> {
					if (crafted.get()) {
						return false;
					}
					boolean ok = inventory.craft(recipeUid, multiplier);
					if (ok) {
						crafted.set(true);
					}
					return ok;
				},
				crafted::get
			);
			if (!result.processed()) {
				break;
			}
		}

		Assertions.assertEquals(List.of(
			new Craft(WET_PROCESSOR, 1),
			new Craft(WET_ASSEMBLY, 1),
			new Craft(WET_COMPUTER, 1),
			new Craft(WET_MAINFRAME, 1)
		), inventory.crafted);
		Assertions.assertEquals(8, inventory.amount(key1("wetware_processor_mainframe")));
		Assertions.assertEquals(0, inventory.amount(key1("wetware_processor")));
		Assertions.assertEquals(0, inventory.amount(key1("wetware_processor_assembly")));
		Assertions.assertEquals(0, inventory.amount(key1("wetware_processor_computer")));
	}

	@Test
	public void shiftCraftAllCraftsSetQuantityWhenInventoryHasFullBatch() {
		// Bug6 回归: 背包已有最终产物满批(e=1)时, shift+C 仍必须合成设定数量。
		// 旧逻辑把需求撑到 availableAmount, 链算出 0 需求 -> 完全无法再合成。
		TestInventory inventory = chainInventory();
		inventory.add(key("e"), 1);
		RecipeChainMath math = chainMath();
		math.expandRootDemandForCraftAll(inventory.snapshot());

		AutoCraftingManager.Result result = AutoCraftingManager.run(
			math,
			List.of(),
			inventory::snapshot,
			inventory::craft
		);

		Assertions.assertTrue(result.completed());
		Assertions.assertEquals(2, inventory.amount(key("e")));
		Assertions.assertEquals(List.of(
			new Craft(C_RECIPE, 2),
			new Craft(E_RECIPE, 1)
		), inventory.crafted);
	}

	private static RecipeChainMath chainMath() {
		return RecipeChainMath.of(List.of(
			input(0, result(C_RECIPE, key("c"), 1, 1)),
			input(1, ingredient(C_RECIPE, key("a"), 2)),
			input(2, ingredient(C_RECIPE, key("b"), 3)),
			input(3, result(E_RECIPE, key("e"), 1, 1)),
			input(4, ingredient(E_RECIPE, key("c"), 3)),
			input(5, ingredient(E_RECIPE, key("d"), 4))
		), Set.of());
	}

	private static TestInventory chainInventory() {
		TestInventory inventory = new TestInventory();
		inventory.add(key("a"), 20);
		inventory.add(key("b"), 30);
		inventory.add(key("c"), 1);
		inventory.add(key("d"), 8);
		return inventory;
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	private static BookmarkItemMetadata item(BookmarkIngredientKey key, long amount) {
		return metadata(null, BookmarkItemType.ITEM, key, amount, 1);
	}

	private static BookmarkItemMetadata result(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor, long multiplier) {
		return metadata(recipeUid, BookmarkItemType.RESULT, key, factor, multiplier);
	}

	private static BookmarkItemMetadata ingredient(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor) {
		return metadata(recipeUid, BookmarkItemType.INGREDIENT, key, factor, 1);
	}

	private static BookmarkItemMetadata metadata(ResourceLocation recipeUid, BookmarkItemType type, BookmarkIngredientKey key, long factor, long multiplier) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			multiplier,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(key)
		);
	}

	private static BookmarkIngredientKey key(String uid) {
		return new BookmarkIngredientKey("test:item", uid, null);
	}

	/**
	 * 1.20.1 平台: 实时类型 uid 是短形式 "item_stack", 库存快照 Count 归一化为 1。
	 */
	private static BookmarkIngredientKey key1(String uid) {
		return new BookmarkIngredientKey("item_stack", uid, "{Count:1b,id:\"" + uid + "\"}");
	}

	/**
	 * 1.20.1 平台: 配方输出快照保留产出 Count(8b), 与输入/库存快照(1b)不同。
	 */
	private static BookmarkIngredientKey key8(String uid) {
		return new BookmarkIngredientKey("item_stack", uid, "{Count:8b,id:\"" + uid + "\"}");
	}

	private record Craft(ResourceLocation recipeUid, int multiplier) {
	}

	private static final class TestInventory {
		private final Map<BookmarkIngredientKey, Long> amounts = new LinkedHashMap<>();
		private final List<Craft> crafted = new ArrayList<>();

		void add(BookmarkIngredientKey key, long amount) {
			amounts.merge(key, amount, Long::sum);
		}

		long amount(BookmarkIngredientKey key) {
			return amounts.getOrDefault(key, 0L);
		}

		List<RecipeChainInput> snapshot() {
			List<RecipeChainInput> inputs = new ArrayList<>();
			int index = 100;
			for (Map.Entry<BookmarkIngredientKey, Long> entry : amounts.entrySet()) {
				if (entry.getValue() > 0) {
					inputs.add(input(index++, item(entry.getKey(), entry.getValue())));
				}
			}
			return inputs;
		}

		boolean craft(ResourceLocation recipeUid, int multiplier) {
			if (C_RECIPE.equals(recipeUid) && take(key("a"), 2L * multiplier) && take(key("b"), 3L * multiplier)) {
				add(key("c"), multiplier);
				crafted.add(new Craft(recipeUid, multiplier));
				return true;
			}
			if (E_RECIPE.equals(recipeUid) && take(key("c"), 3L * multiplier) && take(key("d"), 4L * multiplier)) {
				add(key("e"), multiplier);
				crafted.add(new Craft(recipeUid, multiplier));
				return true;
			}
			if (WET_PROCESSOR.equals(recipeUid) && take(key1("crystal_processor_mainframe"), 8L * multiplier)) {
				add(key1("wetware_processor"), 8L * multiplier);
				crafted.add(new Craft(recipeUid, multiplier));
				return true;
			}
			if (WET_ASSEMBLY.equals(recipeUid) && take(key1("wetware_processor"), 8L * multiplier)) {
				add(key1("wetware_processor_assembly"), 8L * multiplier);
				crafted.add(new Craft(recipeUid, multiplier));
				return true;
			}
			if (WET_COMPUTER.equals(recipeUid) && take(key1("wetware_processor_assembly"), 8L * multiplier)) {
				add(key1("wetware_processor_computer"), 8L * multiplier);
				crafted.add(new Craft(recipeUid, multiplier));
				return true;
			}
			if (WET_MAINFRAME.equals(recipeUid) && take(key1("wetware_processor_computer"), 8L * multiplier)) {
				add(key1("wetware_processor_mainframe"), 8L * multiplier);
				crafted.add(new Craft(recipeUid, multiplier));
				return true;
			}
			return false;
		}

		private boolean take(BookmarkIngredientKey key, long amount) {
			long current = amount(key);
			if (current < amount) {
				return false;
			}
			add(key, -amount);
			return true;
		}
	}
}
