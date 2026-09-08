package mezz.jei.common.platform;

import com.mojang.serialization.Codec;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface IPlatformFluidHelperInternal<T> extends IPlatformFluidHelper<T> {

	Optional<TextureAtlasSprite> getStillFluidSprite(T ingredient);

	Component getDisplayName(T ingredient);

	int getColorTint(T ingredient);

	long getAmount(T ingredient);

	ResourceLocation getFluidId(T ingredient);

	Set<ResourceLocation> getFluidTags(T ingredient);
	boolean isEmpty(T ingredient);

	DataComponentPatch getComponentsPatch(T ingredient);

	void getTooltip(List<Component> tooltip, T ingredient, TooltipFlag tooltipFlag);

	T copy(T ingredient);

	T copyWithAmount(T ingredient, long amount);

	T normalize(T ingredient);

	Optional<T> getContainedFluid(ITypedIngredient<?> ingredient);

	Codec<T> getCodec();
}
