package fr.iglee42.placeableingots.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.iglee42.placeableingots.IngotBlock;
import fr.iglee42.placeableingots.IngotBlockEntity;
import fr.iglee42.placeableingots.PlaceableIngots;
import fr.iglee42.placeableingots.client.RenderHelpers;
import fr.iglee42.placeableingots.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import fr.iglee42.placeableingots.Network;
import net.minecraftforge.registries.ForgeRegistries;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IngotBlockModel implements SimpleStaticBlockModel<IngotBlockModel> {

    public static final IngotBlockModel INSTANCE = new IngotBlockModel();

    private static final Map<Item,Integer> colorCache = new HashMap<>();

    @Override
    public TextureAtlasSprite render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, BlockState state, BlockEntity blockEntity) {

        ClientConfig.colorOverrides.forEach((it,color)->{
            colorCache.putIfAbsent(ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(it)),new Color(color).getRGB());
        });

        IngotBlockEntity ibe = (IngotBlockEntity) blockEntity;
        poseStack.pushPose();

        float pixelSize = 1/32f;
        float xSize = 14*pixelSize;
        float ySize = 4*pixelSize;
        float zSize = 6*pixelSize;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(BLOCKS_ATLAS).apply(new ResourceLocation(PlaceableIngots.MODID,"block/ingot"));

        // Snapshot items to avoid concurrent modification or mid-frame size changes with Embeddium/Sodium-like renderers
        List<ItemStack> items = List.copyOf(ibe.getIngots());
        boolean fallbackFirstOnly = items.isEmpty() && state.getValue(IngotBlock.COUNT) > 0;
        int renderCount = fallbackFirstOnly ? 1 : Math.min(items.size(), 64);
        for (int i = 0; i < renderCount; i++){

            poseStack.pushPose();
            int color;
            if (fallbackFirstOnly) {
                // Try to colorize using client hint first, then last known item id from BE
                Item item = null;
                if (Network.CLIENT_HINT.containsKey(blockEntity.getBlockPos())) {
                    item = ForgeRegistries.ITEMS.getValue(Network.CLIENT_HINT.get(blockEntity.getBlockPos()));
                }
                if (item == null && ibe.getLastItemId() != null) {
                    item = ForgeRegistries.ITEMS.getValue(ibe.getLastItemId());
                }
                if (item != null) {
                    if (!colorCache.containsKey(item)){
                        ItemStack stack = new ItemStack(item);
                        BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack,null,null,0);
                        Color currentColor = new Color(itemModel.getParticleIcon(ModelData.EMPTY).getPixelRGBA(0,0,0),true);
                        int currentX = 7;
                        int currentY = 7;
                        while (currentColor.getAlpha() < 255){
                            currentX++;
                            if (currentX >= itemModel.getParticleIcon(ModelData.EMPTY).contents().width()) {
                                currentX = 0;
                                currentY++;
                            }
                            int clColor = itemModel.getParticleIcon(ModelData.EMPTY).contents().getOriginalImage().getPixelRGBA(currentX,currentY);

                            int red = (clColor) & 0xFF;
                            int green = (clColor >> 8) & 0xFF;
                            int blue = (clColor >> 16) & 0xFF;
                            int alpha = (clColor >> 24) & 0xFF;

                            currentColor = new Color(red,green,blue,alpha);
                        }
                        color = currentColor.getRGB();
                        colorCache.put(item,color);
                    } else {
                        color = colorCache.get(item);
                    }
                } else {
                    color = 0xFFFFFFFF;
                }
            } else {
                if (i >= items.size()) {
                    // Safety: underlying list changed mid-render; skip gracefully
                    poseStack.popPose();
                    continue;
                }
                ItemStack stack = items.get(i);
                if (!colorCache.containsKey(stack.getItem())){
                    BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack,null,null,0);
                    Color currentColor = new Color(itemModel.getParticleIcon(ModelData.EMPTY).getPixelRGBA(0,0,0),true);
                    int currentX = 7;
                    int currentY = 7;
                    while (currentColor.getAlpha() < 255){
                        currentX++;
                        if (currentX >= itemModel.getParticleIcon(ModelData.EMPTY).contents().width()) {
                            currentX = 0;
                            currentY++;
                        }
                        int clColor = itemModel.getParticleIcon(ModelData.EMPTY).contents().getOriginalImage().getPixelRGBA(currentX,currentY);

                        int red = (clColor) & 0xFF;
                        int green = (clColor >> 8) & 0xFF;
                        int blue = (clColor >> 16) & 0xFF;
                        int alpha = (clColor >> 24) & 0xFF;

                        currentColor = new Color(red,green,blue,alpha);
                    }
                    color = currentColor.getRGB();
                    colorCache.put(stack.getItem(),color);
                } else {
                    color = colorCache.get(stack.getItem());
                }
            }
            int layer = Math.floorDiv(i , 8);
            int rowIndex = (i - layer*8)  % 4;
            int index = (i - layer*8) % 8;
            float paddingX = pixelSize + index > 4 ? xSize + 2*pixelSize : 0;
            float paddingY = layer * ySize;
            float paddingZ = pixelSize * (rowIndex+1) + rowIndex*(pixelSize+zSize);


            if (layer % 2 == 1){
                poseStack.translate(0.5f,0,0.5f);
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
                poseStack.translate(-0.5f,0,-0.5f);
            }

            poseStack.translate(pixelSize,0,0);

            RenderHelpers.renderTexturedCuboid(poseStack,buffer,sprite,packedLight,packedOverlay,paddingX,paddingY,paddingZ,paddingX + xSize,paddingY + ySize,paddingZ + zSize,true, color);




            poseStack.popPose();
        }

        poseStack.popPose();

        return sprite;
    }

    @Override
    public int faces(BlockState state) {
        // Return a safe upper bound so the buffer is always large enough,
        // avoiding under-allocation when BE data arrives a frame later
        return 64 * 6;
    }
}
