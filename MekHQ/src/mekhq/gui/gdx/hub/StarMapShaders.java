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

/**
 * The star map's GLSL: under everything the realms' territory, spread from their claims and drawn crisp at any scale,
 * blended over the dark; over it, drawn additively, the
 * systems as glowing points, rings marking the force's system and the one under the cursor or chosen, lines for the
 * jump range and the grid, and the force's route and the course previewed, travelled solid and dashed ahead.
 */
final class StarMapShaders {
    private StarMapShaders() {
    }

    /** A system: a_texCoord0 is (its size in light years, a phase for its twinkle). */
    static final String STAR_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec4 a_color;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          uniform vec3 u_camera;
          uniform float u_pixels;
          uniform float u_time;
          uniform float u_fade;
          out vec4 v_colour;
          out float v_point;
          void main() {
              float size = a_texCoord0.x;
              float phase = a_texCoord0.y;
              float twinkle = 0.9 + 0.1 * sin(u_time * (0.9 + fract(phase * 7.1)) + phase * 40.0);
              v_colour = vec4(a_color.rgb * twinkle * u_fade, a_color.a);
              gl_Position = u_projView * vec4(a_position, 1.0);
              gl_PointSize = clamp(size * u_pixels / max(length(u_camera - a_position), 0.1), 4.5 * size, 26.0);
              v_point = gl_PointSize;
          }
          """;

    /** A system's light: a hot core, wider on the smallest sprites so distant systems stay visible, and a halo. */
    static final String STAR_FRAGMENT = """
          #version 330 core
          in vec4 v_colour;
          in float v_point;
          out vec4 fragColor;
          void main() {
              float r = length(gl_PointCoord - 0.5) * 2.0;
              float small = clamp(4.0 / v_point, 0.0, 1.0);
              float core = smoothstep(0.32 + 0.38 * small, 0.0, r);
              float hot = smoothstep(0.18, 0.0, r) * 0.6;
              float halo = exp(-r * r * 5.0) * 0.45;
              float light = (core + halo) * v_colour.a;
              if (light < 0.004) {
                  discard;
              }
              fragColor = vec4(v_colour.rgb * light + vec3(hot * v_colour.a), 1.0);
          }
          """;

    /** A star of the deep sky: a_texCoord0 is (its brightness, a phase for its tint and twinkle). */
    static final String BACKDROP_STAR_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          uniform float u_fade;
          uniform float u_time;
          uniform float u_scale;
          out vec3 v_colour;
          void main() {
              float bright = a_texCoord0.x;
              float phase = a_texCoord0.y;
              vec3 tint = mix(vec3(0.70, 0.80, 1.0), vec3(1.0, 0.82, 0.62), smoothstep(0.7, 1.0, phase));
              float twinkle = 0.8 + 0.2 * sin(u_time * (0.6 + phase) + phase * 61.0);
              v_colour = tint * bright * twinkle * 1.1 * u_fade;
              gl_Position = u_projView * vec4(a_position, 1.0);
              gl_PointSize = (1.6 + bright * 2.8) * u_scale;
          }
          """;

    static final String BACKDROP_STAR_FRAGMENT = """
          #version 330 core
          in vec3 v_colour;
          out vec4 fragColor;
          void main() {
              float r = length(gl_PointCoord - 0.5) * 2.0;
              float light = exp(-r * r * 5.0);
              if (light < 0.01) {
                  discard;
              }
              fragColor = vec4(v_colour * light, 1.0);
          }
          """;

    /** The nebulae's plane, deep beneath the map: v_world is where on it, spinward and rimward. */
    static final String NEBULA_VERTEX = """
          #version 330 core
          in vec3 a_position;
          uniform mat4 u_projView;
          out vec2 v_world;
          void main() {
              v_world = a_position.xz;
              gl_Position = u_projView * vec4(a_position, 1.0);
          }
          """;

    /**
     * Dim drifting clouds of violet and teal gas, veined with dust, over a warm glow that grows coreward (-z), where
     * the galaxy's heart lies.
     */
    static final String NEBULA_FRAGMENT = """
          #version 330 core
          in vec2 v_world;
          uniform float u_fade;
          uniform float u_time;
          out vec4 fragColor;
          float hash(vec2 p) {
              return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
          }
          float noise(vec2 p) {
              vec2 i = floor(p);
              vec2 f = fract(p);
              vec2 u = f * f * (3.0 - 2.0 * f);
              return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
                         mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
          }
          float fbm(vec2 p) {
              float v = 0.0;
              float a = 0.5;
              for (int i = 0; i < 5; i++) {
                  v += a * noise(p);
                  p = p * 2.03 + vec2(17.1, 9.2);
                  a *= 0.5;
              }
              return v;
          }
          void main() {
              vec2 p = v_world / 2400.0;
              vec2 warp = vec2(fbm(p + vec2(0.0, u_time * 0.003)), fbm(p + vec2(5.2, 1.3 - u_time * 0.002)));
              float n = fbm(p * 1.4 + warp * 1.7);
              float clouds = smoothstep(0.42, 0.9, n);
              float dust = smoothstep(0.55, 0.8, fbm(p * 3.3 + warp * 2.0));
              vec3 violet = vec3(0.16, 0.06, 0.30);
              vec3 teal = vec3(0.03, 0.16, 0.24);
              vec3 gas = mix(teal, violet, smoothstep(0.35, 0.65, warp.x)) * clouds * (1.0 - dust * 0.6);
              float core = smoothstep(3000.0, -11000.0, v_world.y);
              vec3 heart = vec3(1.0, 0.72, 0.45) * core * core * 0.35 * (0.7 + 0.3 * n);
              float edge = 1.0 - smoothstep(7000.0, 11500.0, length(v_world));
              fragColor = vec4((gas + heart) * 0.36 * edge * u_fade, 1.0);
          }
          """;

    /**
     * A ribbon of a stroke: a_normal is the segment's other end, a_texCoord0 is (which side, -1 or 1; its width in
     * pixels; light years along its line; its flow), its height times u_lift. The ribbon is turned to face the screen, its edges the width
     * apart there however far the segment lies.
     */
    static final String STROKE_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec3 a_normal;
          in vec4 a_color;
          in vec4 a_texCoord0;
          uniform mat4 u_projView;
          uniform vec2 u_viewport;
          uniform float u_scale;
          uniform float u_lift;
          out vec4 v_colour;
          out float v_side;
          out float v_along;
          out float v_flow;
          void main() {
              vec3 lift = vec3(1.0, u_lift, 1.0);
              vec4 here = u_projView * vec4(a_position * lift, 1.0);
              vec4 there = u_projView * vec4(a_normal * lift, 1.0);
              vec2 a = here.xy / max(here.w, 1e-4) * u_viewport * 0.5;
              vec2 b = there.xy / max(there.w, 1e-4) * u_viewport * 0.5;
              vec2 dir = b - a;
              float len = length(dir);
              dir = len > 1e-4 ? dir / len : vec2(1.0, 0.0);
              vec2 normal = vec2(-dir.y, dir.x);
              float side = a_texCoord0.x;
              float width = a_texCoord0.y * u_scale;
              here.xy += normal * side * width * 0.5 / (u_viewport * 0.5) * here.w;
              gl_Position = here;
              v_colour = a_color;
              v_side = side;
              v_along = a_texCoord0.z;
              v_flow = a_texCoord0.w;
          }
          """;

    /** A stroke's light: a bright core in a soft glow, pulsed or dashed along it as its flow says. */
    static final String STROKE_FRAGMENT = """
          #version 330 core
          in vec4 v_colour;
          in float v_side;
          in float v_along;
          in float v_flow;
          uniform float u_fade;
          uniform float u_time;
          uniform float u_reveal;
          out vec4 fragColor;
          void main() {
              if (v_along > u_reveal) {
                  discard;
              }
              float d = abs(v_side);
              float core = 1.0 - smoothstep(0.18, 0.42, d);
              float glow = exp(-d * d * 5.0) * 0.45;
              float light = core * 0.75 + glow;
              if (v_flow > 0.0) {
                  float p = fract((v_along - u_time * 14.0) / v_flow);
                  float pulse = exp(-pow((p - 0.5) * 9.0, 2.0));
                  light *= 0.35 + 1.8 * pulse;
              } else if (v_flow < 0.0) {
                  float p = fract((v_along - u_time * -v_flow * 0.4) / -v_flow);
                  light *= 0.15 + 0.85 * smoothstep(0.0, 0.08, p) * (1.0 - smoothstep(0.5, 0.58, p));
              }
              float front = (u_reveal - v_along) / 5.0;
              light *= 1.0 + 3.0 * exp(-front * front);
              light *= v_colour.a * u_fade;
              if (light < 0.003) {
                  discard;
              }
              fragColor = vec4(v_colour.rgb * light, 1.0);
          }
          """;

    /** A marker ring: a_texCoord0 is (its size in pixels, how much it pulses, 10 more a diamond, 20 a square). */
    static final String RING_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec4 a_color;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          uniform float u_time;
          out vec4 v_colour;
          flat out int v_shape;
          void main() {
              v_colour = a_color;
              v_shape = int(a_texCoord0.y / 10.0);
              float pulse = mod(a_texCoord0.y, 10.0);
              gl_Position = u_projView * vec4(a_position, 1.0);
              gl_PointSize = a_texCoord0.x * (1.0 + 0.12 * pulse * sin(u_time * 3.5));
          }
          """;

    static final String RING_FRAGMENT = """
          #version 330 core
          in vec4 v_colour;
          flat in int v_shape;
          uniform float u_fade;
          out vec4 fragColor;
          void main() {
              vec2 p = abs(gl_PointCoord - 0.5) * 2.0;
              float r = v_shape == 1 ? (p.x + p.y) * 0.9 : v_shape == 2 ? max(p.x, p.y) * 1.05 : length(p);
              float ring = smoothstep(0.12, 0.0, abs(r - 0.78));
              float glow = exp(-pow((r - 0.78) * 6.0, 2.0)) * 0.35;
              float light = (ring + glow) * v_colour.a * u_fade;
              if (light < 0.004) {
                  discard;
              }
              fragColor = vec4(v_colour.rgb * light, 1.0);
          }
          """;

    /** A line, fading out with its distance from u_focus beyond u_reach light years, all of it dimmed by u_fade. */
    static final String LINE_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec4 a_color;
          uniform mat4 u_projView;
          out vec4 v_colour;
          out vec3 v_world;
          void main() {
              v_colour = a_color;
              v_world = a_position;
              gl_Position = u_projView * vec4(a_position, 1.0);
          }
          """;

    static final String LINE_FRAGMENT = """
          #version 330 core
          in vec4 v_colour;
          in vec3 v_world;
          uniform vec3 u_focus;
          uniform float u_reach;
          uniform float u_fade;
          out vec4 fragColor;
          void main() {
              float reach = 1.0 - smoothstep(u_reach * 0.5, u_reach, length(v_world.xz - u_focus.xz));
              fragColor = vec4(v_colour.rgb * v_colour.a * reach * u_fade, 1.0);
          }
          """;

    /**
     * A route: a_texCoord0.x is how many light years along it the vertex lies. The first u_done light years, those
     * travelled, are solid; the rest are dashes u_dash light years long flowing towards the destination.
     */
    static final String ROUTE_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec4 a_color;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          out vec4 v_colour;
          out float v_along;
          void main() {
              v_colour = a_color;
              v_along = a_texCoord0.x;
              gl_Position = u_projView * vec4(a_position, 1.0);
          }
          """;

    static final String ROUTE_FRAGMENT = """
          #version 330 core
          in vec4 v_colour;
          in float v_along;
          uniform float u_time;
          uniform float u_done;
          uniform float u_dash;
          uniform float u_fade;
          out vec4 fragColor;
          void main() {
              float light = 1.0;
              if (v_along > u_done) {
                  float phase = fract((v_along - u_time * u_dash * 0.6) / (u_dash * 2.0));
                  light = 0.25 + 0.75 * smoothstep(0.42, 0.5, phase) * (1.0 - smoothstep(0.92, 1.0, phase));
              }
              fragColor = vec4(v_colour.rgb * v_colour.a * light * u_fade, 1.0);
          }
          """;

    /** The territory's plane: v_map is where on the galactic plane, in light years spinward and coreward. */
    static final String TERRITORY_VERTEX = """
          #version 330 core
          in vec3 a_position;
          uniform mat4 u_projView;
          out vec2 v_map;
          void main() {
              v_map = vec2(a_position.x, -a_position.z);
              gl_Position = u_projView * vec4(a_position, 1.0);
          }
          """;

    /**
     * The realms' territory, turning from one date's land (A) into another's (B) as u_blend runs 0 to 1. Each texel of
     * a land holds who holds it, how far it lies from their edge in sixteenths of a light year, and who disputes it and
     * how strongly. A realm's reach, how far inside its ground a point lies (negative outside), is interpolated from
     * the texels round it in both lands, so borders sweep across space from date to date; the point is the realm's
     * whose reach is greatest, and its distance from the border is where that realm's and the next one's reaches
     * meet. Ground fills faintly in its realm's colour and glows towards a crisp border drawn a pixel and a half each
     * side in each side's colour; unclaimed space glows faintly just outside. Disputed ground is hatched in the
     * disputing realm's colour. u_palette holds each realm's colour, sRGB, 256 to a row, realm 1 first, its
     * alpha how strongly the realm shows: independents only faintly.
     */
    static final String TERRITORY_FRAGMENT = """
          #version 330 core
          in vec2 v_map;
          uniform isampler2D u_landA;
          uniform isampler2D u_landB;
          uniform vec2 u_originA;
          uniform vec2 u_originB;
          uniform float u_texelA;
          uniform float u_texelB;
          uniform ivec2 u_sizeA;
          uniform ivec2 u_sizeB;
          uniform float u_blend;
          uniform sampler2D u_palette;
          uniform float u_alpha;
          uniform float u_time;
          uniform int u_spot;
          uniform float u_spotIn;
          out vec4 fragColor;
          const float SCALE = 16.0;
          const int FAR = 32000;
          ivec4 texels[8];
          float weights[8];
          void gather(isampler2D land, vec2 origin, float texel, ivec2 size, float share, int base) {
              vec2 uv = (v_map - origin) / texel - 0.5;
              ivec2 i0 = ivec2(floor(uv));
              vec2 f = uv - vec2(i0);
              for (int k = 0; k < 4; k++) {
                  ivec2 o = ivec2(k & 1, k >> 1);
                  ivec2 at = i0 + o;
                  ivec4 t = ivec4(0, FAR, 0, 0);
                  if (share > 0.0 && all(greaterThanEqual(at, ivec2(0))) && all(lessThan(at, size))) {
                      t = texelFetch(land, at, 0);
                  }
                  texels[base + k] = t;
                  weights[base + k] = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y) * share;
              }
          }
          float reach(int realm) {
              float sum = 0.0;
              for (int k = 0; k < 8; k++) {
                  float s = float(texels[k].y) / SCALE;
                  sum += weights[k] * (texels[k].x == realm ? s : -s);
              }
              return sum;
          }
          vec3 colour(int realm) {
              int id = realm - 1;
              return pow(texelFetch(u_palette, ivec2(id & 255, id >> 8), 0).rgb, vec3(2.2));
          }
          float strength(int realm) {
              int id = realm - 1;
              return texelFetch(u_palette, ivec2(id & 255, id >> 8), 0).a;
          }
          void main() {
              gather(u_landA, u_originA, u_texelA, u_sizeA, 1.0 - u_blend, 0);
              gather(u_landB, u_originB, u_texelB, u_sizeB, u_blend, 4);
              int a = -1;
              float sa = -1e9;
              int b = -1;
              float sb = -1e9;
              float dispute = 0.0;
              int partner = 0;
              float partnerWeight = 0.0;
              for (int k = 0; k < 8; k++) {
                  if (weights[k] <= 0.0) {
                      continue;
                  }
                  dispute += weights[k] * float(texels[k].w) / 32767.0;
                  if (texels[k].z > 0 && weights[k] > partnerWeight) {
                      partner = texels[k].z;
                      partnerWeight = weights[k];
                  }
                  int realm = texels[k].x;
                  if (realm == a || realm == b) {
                      continue;
                  }
                  float s = reach(realm);
                  if (s > sa) {
                      b = a;
                      sb = sa;
                      a = realm;
                      sa = s;
                  } else if (s > sb) {
                      b = realm;
                      sb = s;
                  }
              }
              int edge = a > 0 ? a : b;
              if (edge <= 0) {
                  discard;
              }
              // Light years inside a's ground from its border with b
              float depth = b < 0 ? sa : (sa - sb) * 0.5;
              float lightYears = max(length(fwidth(v_map)) * 0.7071, 1e-4);
              vec4 result;
              if (a > 0) {
                  vec3 hue = colour(a);
                  vec3 fill = hue;
                  if (partner > 0 && partner != a && dispute > 0.01) {
                      float hatch = abs(fract(dot(v_map, vec2(0.8660254, 0.5)) / 5.0) - 0.5) * 5.0;
                      float stripe = 1.0 - smoothstep(0.6, 0.6 + lightYears, hatch);
                      // Hatching finer than the pixels would shimmer; far out the dispute only tints
                      stripe *= 1.0 - smoothstep(0.5, 1.5, lightYears);
                      fill = mix(hue, colour(partner), smoothstep(0.0, 0.7, dispute) * max(0.3, 0.75 * stripe));
                  }
                  float drift = 0.85 + 0.15 * sin(dot(v_map, vec2(0.011, 0.007)) * 6.2832 + u_time * 0.4);
                  float glow = exp(-depth / 3.0) * 0.28 + exp(-depth / 16.0) * 0.14;
                  float lit = a == u_spot ? u_spotIn : 0.0;
                  // The realm in the spotlight fills with light; the rest of space draws back from it
                  float shade = u_spot > 0 && a != u_spot ? 1.0 - 0.45 * u_spotIn : 1.0;
                  result = vec4(fill * (1.0 + 0.25 * lit), ((0.07 + 0.13 * lit) * drift + glow * (1.0 + lit)) * shade);
              } else {
                  result = vec4(colour(b), exp(-depth / 5.0) * 0.08);
              }
              float line = 1.0 - smoothstep(0.6, 1.5, depth / lightYears);
              result.a *= strength(a > 0 ? a : b);
              float edgeLit = edge == u_spot ? u_spotIn : 0.0;
              float edgeShade = u_spot > 0 && edge != u_spot ? 1.0 - 0.4 * u_spotIn : 1.0;
              line = 1.0 - smoothstep(0.6 + 0.9 * edgeLit, 1.5 + 1.2 * edgeLit, depth / lightYears);
              result = mix(result, vec4(colour(edge) * (1.4 + 0.8 * edgeLit) + 0.05,
                    0.9 * max(strength(edge), edgeLit) * edgeShade), line);
              result.a *= u_alpha;
              if (result.a < 0.002) {
                  discard;
              }
              fragColor = result;
          }
          """;
}
