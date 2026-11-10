/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MekHQ was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package mekhq.gui.gdx.hub;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import de.javagl.jgltf.model.AccessorByteData;
import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AccessorShortData;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.v2.MaterialModelV2;

/**
 * A static, textured prop read from a glTF binary (GLB): every mesh of its first scene baked into world space (glTF's
 * Y-up metres), with its metallic-roughness material. {@link HoloTableView}'s prop shader draws it.
 *
 * <p>The node extras {@code mmHoloAnchor} mark where a hologram stands on the prop, in Blender's Z-up axes as Blender
 * writes custom properties.</p>
 */
final class GlbProp implements Disposable {
    /** Position, normal and texture coordinates. */
    private static final int STRIDE = 8;

    /** One material's factors and maps; a missing map is a neutral 1x1 texture. */
    private record Surface(float[] baseFactor, float[] emissive, float metallic, float roughness, Texture base,
          Texture normal, Texture metalRough, Texture glow) { }

    private record Part(Mesh mesh, Surface surface, String material) { }

    private final List<Part> parts = new ArrayList<>();
    private final List<Texture> textures = new ArrayList<>();
    private final Vector3 anchor = new Vector3();

    private GlbProp() {
    }

    /** GPU thread: the prop in {@code file}. */
    static GlbProp load(Path file) {
        GltfModel model;
        try {
            model = new GltfModelReader().read(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read " + file + ": " + e.getMessage(), e);
        }
        GlbProp prop = new GlbProp();
        try {
            Texture white = prop.solid(255, 255, 255);
            Texture flat = prop.solid(128, 128, 255);
            Map<TextureModel, Texture> loaded = new IdentityHashMap<>();
            for (NodeModel root : model.getSceneModels().get(0).getNodeModels()) {
                prop.add(root, loaded, white, flat);
            }
        } catch (RuntimeException e) {
            prop.dispose();
            throw e;
        }
        return prop;
    }

    /** Where a hologram stands, in the prop's Y-up metres. */
    Vector3 anchor() {
        return anchor;
    }

    /**
     * Draws every part with {@code shader}, which samples texture units 0 to 3. {@code glowScale} scales the emission
     * of the materials it names; the rest glow as authored.
     */
    void draw(ShaderProgram shader, Map<String, Float> glowScale) {
        shader.setUniformi("u_base", 0);
        shader.setUniformi("u_normalMap", 1);
        shader.setUniformi("u_metalRough", 2);
        shader.setUniformi("u_glow", 3);
        for (Part part : parts) {
            Surface surface = part.surface();
            surface.glow().bind(3);
            surface.metalRough().bind(2);
            surface.normal().bind(1);
            surface.base().bind(0);
            float[] base = surface.baseFactor();
            shader.setUniformf("u_baseFactor", base[0], base[1], base[2], base[3]);
            float[] glow = surface.emissive();
            float scale = glowScale.getOrDefault(part.material(), 1f);
            shader.setUniformf("u_emissive", glow[0] * scale, glow[1] * scale, glow[2] * scale);
            shader.setUniformf("u_metallic", surface.metallic());
            shader.setUniformf("u_roughness", surface.roughness());
            part.mesh().render(shader, GL20.GL_TRIANGLES);
        }
    }

    @Override
    public void dispose() {
        parts.forEach(part -> part.mesh().dispose());
        parts.clear();
        textures.forEach(Texture::dispose);
        textures.clear();
    }

    private void add(NodeModel node, Map<TextureModel, Texture> loaded, Texture white, Texture flat) {
        if (node.getExtras() instanceof Map<?, ?> extras && extras.get("mmHoloAnchor") instanceof List<?> point
              && point.size() == 3) {
            anchor.set(number(point.get(0)), number(point.get(2)), -number(point.get(1)));
        }
        Matrix4 world = new Matrix4(node.computeGlobalTransform(null));
        Matrix4 normals = new Matrix4(world).inv().tra();
        for (var mesh : node.getMeshModels()) {
            for (var primitive : mesh.getMeshPrimitiveModels()) {
                if (primitive.getMode() != GL20.GL_TRIANGLES) {
                    continue;
                }
                var attributes = primitive.getAttributes();
                AccessorModel positions = attributes.get("POSITION");
                AccessorModel normalData = attributes.get("NORMAL");
                if (positions == null || normalData == null || positions.getCount() > 65535) {
                    throw new IllegalArgumentException("Each part needs positions and normals, and at most 65535 vertices");
                }
                int count = positions.getCount();
                float[] p = floats(positions, 3);
                float[] n = floats(normalData, 3);
                float[] uv = attributes.containsKey("TEXCOORD_0") ? floats(attributes.get("TEXCOORD_0"), 2) : null;
                float[] vertices = new float[count * STRIDE];
                Vector3 v = new Vector3();
                for (int i = 0; i < count; i++) {
                    v.set(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).mul(world);
                    vertices[i * STRIDE] = v.x;
                    vertices[i * STRIDE + 1] = v.y;
                    vertices[i * STRIDE + 2] = v.z;
                    v.set(n[i * 3], n[i * 3 + 1], n[i * 3 + 2]).rot(normals).nor();
                    vertices[i * STRIDE + 3] = v.x;
                    vertices[i * STRIDE + 4] = v.y;
                    vertices[i * STRIDE + 5] = v.z;
                    vertices[i * STRIDE + 6] = uv == null ? 0 : uv[i * 2];
                    vertices[i * STRIDE + 7] = uv == null ? 0 : uv[i * 2 + 1];
                }
                short[] indices = indices(primitive.getIndices(), count);
                Mesh part = new Mesh(true, count, indices.length, new VertexAttributes(VertexAttribute.Position(),
                      VertexAttribute.Normal(), VertexAttribute.TexCoords(0)));
                part.setVertices(vertices);
                part.setIndices(indices);
                MaterialModelV2 material = (MaterialModelV2) primitive.getMaterialModel();
                parts.add(new Part(part, surface(material, loaded, white, flat),
                      material == null ? "" : String.valueOf(material.getName())));
            }
        }
        for (NodeModel child : node.getChildren()) {
            add(child, loaded, white, flat);
        }
    }

    private Surface surface(MaterialModelV2 material, Map<TextureModel, Texture> loaded, Texture white, Texture flat) {
        if (material == null) {
            return new Surface(new float[] { 1, 1, 1, 1 }, new float[3], 0, 1, white, flat, white, white);
        }
        return new Surface(material.getBaseColorFactor(), material.getEmissiveFactor(), material.getMetallicFactor(),
              material.getRoughnessFactor(), texture(material.getBaseColorTexture(), loaded, white),
              texture(material.getNormalTexture(), loaded, flat),
              texture(material.getMetallicRoughnessTexture(), loaded, white),
              texture(material.getEmissiveTexture(), loaded, white));
    }

    private Texture texture(TextureModel source, Map<TextureModel, Texture> loaded, Texture fallback) {
        if (source == null) {
            return fallback;
        }
        return loaded.computeIfAbsent(source, ignored -> {
            var data = source.getImageModel().getImageData().duplicate();
            byte[] encoded = new byte[data.remaining()];
            data.get(encoded);
            Pixmap pixels = new Pixmap(encoded, 0, encoded.length);
            try {
                Texture texture = new Texture(pixels, true);
                textures.add(texture);
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                texture.setAnisotropicFilter(8);
                return texture;
            } finally {
                pixels.dispose();
            }
        });
    }

    private Texture solid(int r, int g, int b) {
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.drawPixel(0, 0, (r << 24) | (g << 16) | (b << 8) | 0xFF);
        Texture texture = new Texture(pixel);
        pixel.dispose();
        textures.add(texture);
        return texture;
    }

    private static float number(Object value) {
        return value instanceof Number number ? number.floatValue() : 0;
    }

    private static float[] floats(AccessorModel accessor, int size) {
        AccessorData data = accessor.getAccessorData();
        if (!(data instanceof AccessorFloatData values) || data.getNumComponentsPerElement() != size) {
            throw new IllegalArgumentException("Expected float vertex data");
        }
        float[] result = new float[accessor.getCount() * size];
        for (int i = 0; i < accessor.getCount(); i++) {
            for (int j = 0; j < size; j++) {
                result[i * size + j] = values.get(i, j);
            }
        }
        return result;
    }

    private static short[] indices(AccessorModel accessor, int vertices) {
        if (accessor == null) {
            short[] result = new short[vertices];
            for (int i = 0; i < vertices; i++) {
                result[i] = (short) i;
            }
            return result;
        }
        AccessorData data = accessor.getAccessorData();
        short[] result = new short[accessor.getCount()];
        for (int i = 0; i < result.length; i++) {
            long index = switch (data) {
                case AccessorByteData values -> values.getInt(i, 0);
                case AccessorShortData values -> values.getInt(i, 0);
                case AccessorIntData values -> values.getLong(i, 0);
                default -> throw new IllegalArgumentException("Unsupported index data");
            };
            if (index < 0 || index >= vertices) {
                throw new IllegalArgumentException("Index out of range");
            }
            result[i] = (short) index;
        }
        return result;
    }
}
