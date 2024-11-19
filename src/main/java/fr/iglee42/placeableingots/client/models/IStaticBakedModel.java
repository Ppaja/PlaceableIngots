/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package fr.iglee42.placeableingots.client.models;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simple implementation of a {@link BakedModel} which delegates to a {@link StaticModelData} provided by the {@link ModelData} mechanism.
 */
public interface IStaticBakedModel extends IDynamicBakedModel
{

    @SuppressWarnings("deprecation") public static final ResourceLocation BLOCKS_ATLAS = TextureAtlas.LOCATION_BLOCKS;
    @Override
    @NotNull
    default List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data, @Nullable RenderType renderType)
    {
        final StaticModelData property = data.get(StaticModelData.PROPERTY);
        if (property != null && side == null)
        {
            return property.quads();
        }
        return List.of();
    }

    @Override
    default TextureAtlasSprite getParticleIcon(ModelData data)
    {
        final StaticModelData property = data.get(StaticModelData.PROPERTY);
        if (property != null)
        {
            return property.particleIcon();
        }
        return Minecraft.getInstance().getTextureAtlas(BLOCKS_ATLAS).apply(MissingTextureAtlasSprite.getLocation());
    }

    @Override
    default TextureAtlasSprite getParticleIcon()
    {
        return Minecraft.getInstance().getTextureAtlas(BLOCKS_ATLAS).apply(MissingTextureAtlasSprite.getLocation());
    }

    @Override
    default boolean useAmbientOcclusion()
    {
        return true;
    }

    @Override
    default boolean isGui3d()
    {
        return false;
    }

    @Override
    default boolean usesBlockLight()
    {
        return true;
    }

    @Override
    default boolean isCustomRenderer()
    {
        return false;
    }

    @Override
    default ItemOverrides getOverrides()
    {
        return ItemOverrides.EMPTY;
    }

    record StaticModelData(List<BakedQuad> quads, TextureAtlasSprite particleIcon)
    {
        public static final ModelProperty<StaticModelData> PROPERTY = new ModelProperty<>();
    }
}