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
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import de.javagl.jgltf.model.v2.MaterialModelV2.AlphaMode;
import jakarta.annotation.Nullable;

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
    private static final Pattern LEVEL = Pattern.compile("-lod(\\d+)$");

    /**
     * One material's factors and maps; a missing map is a neutral 1x1 texture. {@code textured} says whether it has a
     * base colour map; {@code cutoff} is the alpha below which a masked material shows nothing, 0 for opaque ones.
     */
    private record Surface(float[] baseFactor, float[] emissive, float metallic, float roughness, Texture base,
          Texture normal, Texture metalRough, Texture glow, boolean textured, float cutoff) { }

    /** A mesh and its material; a loaded prop keeps its vertices and indices to {@link #batch} copies of it. */
    private record Part(Mesh mesh, Surface surface, String material, @Nullable float[] vertices,
          @Nullable short[] indices) { }

    private final List<Part> parts = new ArrayList<>();
    private final List<Texture> textures = new ArrayList<>();
    private final Vector3 anchor = new Vector3();
    private final Vector3 min = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
    private final Vector3 max = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
    private @Nullable Path folder;
    private @Nullable Map<String, Texture> shared;

    private GlbProp() {
    }

    /** GPU thread: the prop in {@code file}. */
    static GlbProp load(Path file) {
        return load(file, -1, null);
    }

    /**
     * GPU thread: the prop in {@code file}. Given a {@code level} of 0 or more, it takes only the coarsest level of
     * detail up to that one (a plant's last is a flat card), and leaves out the helpers the board keeps beside the
     * meshes (cutters, stands, building volumes); given a negative one, all of the file.
     * Textures the file references by path go into {@code shared} when given, for its owner to dispose, so props
     * sharing a texture load it once.
     */
    static GlbProp load(Path file, int level, @Nullable Map<String, Texture> shared) {
        GltfModel model;
        try {
            model = new GltfModelReader().read(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read " + file + ": " + e.getMessage(), e);
        }
        GlbProp prop = new GlbProp();
        prop.folder = file.toAbsolutePath().getParent();
        prop.shared = shared;
        try {
            Texture white = prop.solid(255, 255, 255);
            Texture flat = prop.solid(128, 128, 255);
            Map<TextureModel, Texture> loaded = new IdentityHashMap<>();
            List<NodeModel> roots = model.getSceneModels().get(0).getNodeModels();
            for (NodeModel root : level >= 0 ? coarsest(roots, level) : roots) {
                prop.add(root, loaded, white, flat);
            }
        } catch (RuntimeException e) {
            prop.dispose();
            throw e;
        }
        return prop;
    }

    /** The roots of the coarsest level of detail up to {@code limit}, or all of them if none is named. */
    private static List<NodeModel> coarsest(List<NodeModel> roots, int limit) {
        List<NodeModel> meshes = new ArrayList<>();
        for (NodeModel root : roots) {
            String name = root.getName() == null ? "" : root.getName();
            if (!name.equals("cut") && !name.equals("stands") && !name.equals("spores")
                  && !name.startsWith("building_volume")) {
                meshes.add(root);
            }
        }
        int chosen = -1;
        for (NodeModel root : meshes) {
            int level = level(root);
            if (level <= limit && level > chosen) {
                chosen = level;
            }
        }
        if (chosen < 0) {
            return meshes;
        }
        List<NodeModel> result = new ArrayList<>();
        for (NodeModel root : meshes) {
            if (level(root) == chosen) {
                result.add(root);
            }
        }
        return result;
    }

    private static int level(NodeModel root) {
        Matcher matcher = LEVEL.matcher(root.getName() == null ? "" : root.getName());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    /** How many vertices the prop's meshes hold, together. */
    int vertexCount() {
        return parts.stream().mapToInt(part -> part.mesh().getNumVertices()).sum();
    }

    /** The meshes of the parts made of material {@code material}, for a view to draw its own way. */
    List<Mesh> meshes(String material) {
        return parts.stream().filter(part -> part.material().equals(material)).map(Part::mesh).toList();
    }

    /** Where a hologram stands, in the prop's Y-up metres. */
    Vector3 anchor() {
        return anchor;
    }

    /** The corner of the prop's bounds with the least coordinates, in its Y-up metres. */
    Vector3 min() {
        return min;
    }

    /** The corner of the prop's bounds with the greatest coordinates, in its Y-up metres. */
    Vector3 max() {
        return max;
    }

    /**
     * GPU thread: one copy of this prop for each of {@code placements}, its transforms, merged into as few meshes as
     * will hold them so they draw at once. It shares this prop's materials, so it must be disposed before it.
     */
    GlbProp batch(List<Matrix4> placements) {
        GlbProp batch = new GlbProp();
        if (placements.isEmpty()) {
            return batch;
        }
        Vector3 v = new Vector3();
        for (Part part : parts) {
            float[] source = part.vertices();
            short[] sourceIndices = part.indices();
            if (source == null || sourceIndices == null) {
                continue;
            }
            int count = source.length / STRIDE;
            int perMesh = Math.max(1, 65_535 / Math.max(1, count));
            for (int first = 0; first < placements.size(); first += perMesh) {
                List<Matrix4> chunk = placements.subList(first, Math.min(placements.size(), first + perMesh));
                float[] vertices = new float[chunk.size() * source.length];
                short[] indices = new short[chunk.size() * sourceIndices.length];
                for (int copy = 0; copy < chunk.size(); copy++) {
                    Matrix4 transform = chunk.get(copy);
                    int base = copy * source.length;
                    for (int i = 0; i < count; i++) {
                        int at = i * STRIDE;
                        v.set(source[at], source[at + 1], source[at + 2]).mul(transform);
                        vertices[base + at] = v.x;
                        vertices[base + at + 1] = v.y;
                        vertices[base + at + 2] = v.z;
                        v.set(source[at + 3], source[at + 4], source[at + 5]).rot(transform).nor();
                        vertices[base + at + 3] = v.x;
                        vertices[base + at + 4] = v.y;
                        vertices[base + at + 5] = v.z;
                        vertices[base + at + 6] = source[at + 6];
                        vertices[base + at + 7] = source[at + 7];
                    }
                    for (int i = 0; i < sourceIndices.length; i++) {
                        indices[copy * sourceIndices.length + i] = (short) (copy * count + (sourceIndices[i] & 0xFFFF));
                    }
                }
                Mesh mesh = new Mesh(true, chunk.size() * count, indices.length, new VertexAttributes(
                      VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)));
                mesh.setVertices(vertices);
                mesh.setIndices(indices);
                batch.parts.add(new Part(mesh, part.surface(), part.material(), null, null));
            }
        }
        return batch;
    }

    /**
     * Draws every part with {@code shader}, which samples texture units 0 to 3. {@code glowScale} scales the emission
     * of the materials it names; the rest glow as authored.
     */
    void draw(ShaderProgram shader, Map<String, Float> glowScale) {
        uniform(shader, "u_base", 0);
        uniform(shader, "u_normalMap", 1);
        uniform(shader, "u_metalRough", 2);
        uniform(shader, "u_glow", 3);
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
            // Only the war table's shader masks and details untextured parts; the holotable's has neither
            int cutoff = shader.fetchUniformLocation("u_cutoff", false);
            if (cutoff >= 0) {
                shader.setUniformf(cutoff, surface.cutoff());
            }
            int textured = shader.fetchUniformLocation("u_textured", false);
            if (textured >= 0) {
                shader.setUniformf(textured, surface.textured() ? 1 : 0);
            }
            part.mesh().render(shader, GL20.GL_TRIANGLES);
        }
    }

    private static void uniform(ShaderProgram shader, String name, int unit) {
        int location = shader.fetchUniformLocation(name, false);
        if (location >= 0) {
            shader.setUniformi(location, unit);
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
                    min.set(Math.min(min.x, v.x), Math.min(min.y, v.y), Math.min(min.z, v.z));
                    max.set(Math.max(max.x, v.x), Math.max(max.y, v.y), Math.max(max.z, v.z));
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
                      material == null ? "" : String.valueOf(material.getName()), vertices, indices));
            }
        }
        for (NodeModel child : node.getChildren()) {
            add(child, loaded, white, flat);
        }
    }

    private Surface surface(MaterialModelV2 material, Map<TextureModel, Texture> loaded, Texture white, Texture flat) {
        if (material == null) {
            return new Surface(new float[] { 1, 1, 1, 1 }, new float[3], 0, 1, white, flat, white, white, false, 0);
        }
        float cutoff = material.getAlphaMode() == AlphaMode.MASK ? material.getAlphaCutoff() : 0;
        return new Surface(material.getBaseColorFactor(), material.getEmissiveFactor(), material.getMetallicFactor(),
              material.getRoughnessFactor(), texture(material.getBaseColorTexture(), loaded, white, cutoff > 0),
              texture(material.getNormalTexture(), loaded, flat, false),
              texture(material.getMetallicRoughnessTexture(), loaded, white, false),
              texture(material.getEmissiveTexture(), loaded, white, false), material.getBaseColorTexture() != null,
              cutoff);
    }

    /** The texture for {@code source}; a {@code cutout}'s clear texels take its leaves' colour (see {@link #bleed}). */
    private Texture texture(TextureModel source, Map<TextureModel, Texture> loaded, Texture fallback,
          boolean cutout) {
        if (source == null) {
            return fallback;
        }
        String uri = source.getImageModel().getUri();
        String key = shared == null || folder == null || uri == null || uri.startsWith("data:") ? null
              : folder.resolve(uri).normalize().toString();
        if (key != null && shared.containsKey(key)) {
            return shared.get(key);
        }
        return loaded.computeIfAbsent(source, ignored -> {
            var data = source.getImageModel().getImageData().duplicate();
            byte[] encoded = new byte[data.remaining()];
            data.get(encoded);
            Pixmap pixels = new Pixmap(encoded, 0, encoded.length);
            if (cutout) {
                pixels = bleed(pixels);
            }
            try {
                Texture texture = new Texture(pixels, true);
                if (key == null) {
                    textures.add(texture);
                } else {
                    shared.put(key, texture);
                }
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                texture.setAnisotropicFilter(8);
                return texture;
            } finally {
                pixels.dispose();
            }
        });
    }

    /**
     * Gives a cutout's clear texels the mean colour of its opaque ones. Their own colour is whatever the painter left
     * (black on some, tan on others), and mip levels average it into the leaves' edges, darkening or bleaching a far
     * canopy. Returns the RGBA8888 pixmap it filled, disposing {@code pixels} if it had to convert them.
     */
    private static Pixmap bleed(Pixmap pixels) {
        Pixmap rgba = pixels;
        if (pixels.getFormat() != Pixmap.Format.RGBA8888) {
            rgba = new Pixmap(pixels.getWidth(), pixels.getHeight(), Pixmap.Format.RGBA8888);
            rgba.setBlending(Pixmap.Blending.None);
            rgba.drawPixmap(pixels, 0, 0);
            pixels.dispose();
        }
        ByteBuffer bytes = rgba.getPixels();
        long red = 0;
        long green = 0;
        long blue = 0;
        long opaque = 0;
        for (int i = 0; i + 3 < bytes.limit(); i += 4) {
            if ((bytes.get(i + 3) & 0xFF) >= 128) {
                red += bytes.get(i) & 0xFF;
                green += bytes.get(i + 1) & 0xFF;
                blue += bytes.get(i + 2) & 0xFF;
                opaque++;
            }
        }
        if (opaque > 0) {
            for (int i = 0; i + 3 < bytes.limit(); i += 4) {
                if ((bytes.get(i + 3) & 0xFF) < 8) {
                    bytes.put(i, (byte) (red / opaque));
                    bytes.put(i + 1, (byte) (green / opaque));
                    bytes.put(i + 2, (byte) (blue / opaque));
                }
            }
        }
        return rgba;
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
