/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package fr.iglee42.placeableingots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;


public final class RenderHelpers
{
    public static void renderTexturedCuboid(PoseStack poseStack, VertexConsumer buffer, TextureAtlasSprite sprite, int packedLight, int packedOverlay, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, boolean doShade,int color)
    {
        renderTexturedCuboid(poseStack, buffer, sprite, packedLight, packedOverlay, minX, minY, minZ, maxX, maxY, maxZ, 16f * (maxX - minX), 16f * (maxY - minY), 16f * (maxZ - minZ), doShade,color);
    }

    /**
     * Renders a fully textured, solid cuboid described by the shape (minX, minY, minZ) x (maxX, maxY, maxZ).
     * (xPixels, yPixels, zPixels) represent pixel widths for each side, which are used for texture (u, v) purposes.
     */
    public static void renderTexturedCuboid(PoseStack poseStack, VertexConsumer buffer, TextureAtlasSprite sprite, int packedLight, int packedOverlay, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float xPixels, float yPixels, float zPixels, boolean doShade,int color)
    {
        renderTexturedQuads(poseStack, buffer, sprite, packedLight, packedOverlay, getXVertices(minX, minY, minZ, maxX, maxY, maxZ), zPixels, yPixels, 1, 0, 0, doShade,color);
        renderTexturedQuads(poseStack, buffer, sprite, packedLight, packedOverlay, getYVertices(minX, minY, minZ, maxX, maxY, maxZ), zPixels, xPixels, 0, 1, 0, doShade,color);
        renderTexturedQuads(poseStack, buffer, sprite, packedLight, packedOverlay, getZVertices(minX, minY, minZ, maxX, maxY, maxZ), xPixels, yPixels, 0, 0, 1, doShade,color);
    }


    /**
     * Renders a single textured quad, either by itself or as part of a larger cuboid construction.
     * {@code vertices} must be a set of vertices, usually obtained through {@link #getXVertices(float, float, float, float, float, float)}, {@link #getYVertices(float, float, float, float, float, float)}, or {@link #getZVertices(float, float, float, float, float, float)}. Parameters are (x, y, z, u, v, normalSign) for each vertex.
     * (normalX, normalY, normalZ) are the normal vectors (positive), for the quad. For example, for an X quad, this will be (1, 0, 0).
     *
     * @param vertices The vertices.
     * @param uSize    The horizontal (u) texture size of the quad, in pixels.
     * @param vSize    The vertical (v) texture size of the quad, in pixels.
     */
    public static void renderTexturedQuads(PoseStack poseStack, VertexConsumer buffer, TextureAtlasSprite sprite, int packedLight, int packedOverlay, float[][] vertices, float uSize, float vSize, float normalX, float normalY, float normalZ, boolean doShade,int color)
    {
        for (float[] v : vertices)
        {
            renderTexturedVertex(poseStack, buffer, packedLight, packedOverlay, v[0], v[1], v[2], sprite.getU(v[3] * uSize), sprite.getV(v[4] * vSize), v[5] * normalX, v[5] * normalY, v[5] * normalZ, doShade,color);
        }
    }


    public static void renderTexturedVertex(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ, boolean doShade,int color)
    {
        final float shade = doShade ? getShade(normalX, normalY, normalZ) : 1f;
        buffer.vertex(poseStack.last().pose(), x, y, z)
                .color(shade, shade, shade, 1f)
                .uv(u, v)
                .uv2(packedLight)
                .color(color)
                .overlayCoords(packedOverlay)
                .normal(poseStack.last().normal(), normalX, normalY, normalZ)
                .endVertex();
    }

    public static float getShade(float normalX, float normalY, float normalZ)
    {
        return getShadeForStep(Math.round(normalX), Math.round(normalY), Math.round(normalZ));
    }

    public static float getShadeForStep(int normalX, int normalY, int normalZ)
    {
        if (normalY == 1) return 1f;
        if (normalY == -1) return 0.5f;
        if (normalZ != 0) return 0.8f;
        if (normalX != 0) return 0.6f;
        return 1f;
    }

    public static float[][] getXVertices(float minX, float minY, float minZ, float maxX, float maxY, float maxZ)
    {
        return new float[][] {
                {minX, minY, minZ, 2, 1, 1}, // +X
                {minX, minY, maxZ, 3, 1, 1},
                {minX + 1/32f, maxY, maxZ - 1/32f, 3, 0, 1},
                {minX + 1/32f, maxY, minZ + 1/32f, 2, 0, 1},

                {maxX, minY, maxZ, 2, 0, -1}, // -X
                {maxX, minY, minZ, 1, 0, -1},
                {maxX - 1/32f, maxY, minZ + 1/32f, 1, 1, -1},
                {maxX - 1/32f, maxY, maxZ - 1/32f, 2, 1, -1}
        };
    }

    /**
     * <pre>
     *  Q------Q.  ^ y
     *  |`.    | `.|
     *  |  `Q--+---Q--> x = maxY
     *  |   |  |   |
     *  P---+--P.  |
     *   `. |    `.|
     *     `P------P = minY
     * </pre>
     *
     * @return A collection of vertices for the positive and negative Y outward faces of the above trapezoidal cuboid, defined by the plane P, and the plane Q, minY, and maxY.
     */

    public static float[][] getYVertices(float minX, float minY, float minZ, float maxX, float maxY, float maxZ)
    {
        return new float[][] {
                {minX + 1/32f, maxY, minZ + 1/32f, 0, 1, 1}, // +Y
                {minX + 1/32f, maxY, maxZ - 1/32f, 1, 1, 1},
                {maxX - 1/32f, maxY, maxZ - 1/32f, 1, 0, 1},
                {maxX - 1/32f, maxY, minZ + 1/32f, 0, 0, 1},

                {minX, minY, maxZ, 1, 1, -1}, // -Y (V = 0 → 1)
                {minX, minY, minZ, 0, 1, -1}, // -Y (V = 0 → 1)
                {maxX, minY, minZ, 0, 0, -1}, // -Y (V = 1 → 0)
                {maxX, minY, maxZ, 1, 0, -1}  // -Y (V = 1 → 0)

        };
    }

    /**
     * <pre>
     *  O------O.  ^ y
     *  |`.    | `.|
     *  |  `P--+---P--> x
     *  |   |  |   |
     *  O---+--O.  |
     *   `. |    `.|
     *     `P------P
     * </pre>
     *
     * @return A collection of vertices for two parallel faces of a cube, facing outwards, defined by (minX, minY, minZ) x (maxX, maxY, maxZ). Or the faces O and P in the above art
     */

    public static float[][] getZVertices(float minX, float minY, float minZ, float maxX, float maxY, float maxZ)
    {
        return new float[][] {
                {maxX , minY, minZ, 0, 5, 1}, // +Z
                {minX , minY, minZ, 1, 5, 1},
                {minX + 1/32f , maxY, minZ + 1/32f, 1, 4, 1},
                {maxX - 1/32f , maxY, minZ + 1/32f, 0, 4, 1},

                {minX, minY, maxZ, 1, 5, -1}, // -Z
                {maxX, minY, maxZ, 0, 5, -1},
                {maxX - 1/32f, maxY, maxZ -1/32f, 0, 6, -1},
                {minX + 1/32f, maxY, maxZ -1/32f, 1, 6, -1}
        };
    }


    public static void setShaderColor(int color)
    {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = ((color) & 0xFF) / 255f;

        RenderSystem.setShaderColor(r, g, b, a);
    }

}