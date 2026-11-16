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
 * The TO&amp;E table's shaders: the lit pass its slabs and counters are drawn with, the units' art laid on the
 * counters, and the additive hologram of what's going on with each unit over them. The field behind and the frame over
 * it are the war table's. Everything the hologram draws is smoothed over a pixel and kept at least a pixel or so wide,
 * so nothing breaks up or crawls as the camera moves.
 */
final class ToeShaders {
    private ToeShaders() {
    }

    /** The slabs, the units' counters and their art. u_model places each. */
    static final String PROP_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec3 a_normal;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          uniform mat4 u_model;
          out vec3 v_world;
          out vec3 v_normal;
          out vec2 v_uv;
          void main() {
              vec3 position = (u_model * vec4(a_position, 1.0)).xyz;
              v_world = position;
              v_normal = mat3(u_model) * a_normal;
              v_uv = a_texCoord0;
              gl_Position = u_projView * vec4(position, 1.0);
          }
          """;

    /**
     * Lit by a fixed key light and a cool rim, or, u_flat 1, a unit's art as it's painted, blended by its alpha times
     * u_alpha. The art can be drained of colour (u_saturation 0, a unit laid up) or shown as a hollow hologram of
     * itself (u_ghost 1, a unit nobody crews); u_dim darkens what the location filter leaves out.
     */
    static final String PROP_FRAGMENT = """
          #version 330 core
          in vec3 v_world;
          in vec3 v_normal;
          in vec2 v_uv;
          uniform sampler2D u_base;
          uniform vec4 u_baseFactor;
          uniform vec3 u_emissive;
          uniform float u_textured;
          uniform float u_flat;
          uniform float u_alpha;
          uniform float u_shine;
          uniform float u_saturation;
          uniform float u_ghost;
          uniform float u_dim;
          uniform float u_time;
          uniform vec3 u_camera;
          uniform vec3 u_keyDir;
          uniform vec3 u_rimDir;
          out vec4 fragColor;

          vec3 aces(vec3 x) {
              return clamp(x * (2.51 * x + 0.03) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
          }

          void main() {
              vec3 n = normalize(v_normal);
              if (!gl_FrontFacing) {
                  n = -n;
              }
              vec4 base = u_baseFactor;
              float alpha = 1.0;
              if (u_textured > 0.5) {
                  vec4 texel = texture(u_base, v_uv);
                  alpha = texel.a * u_alpha;
                  if (alpha < 0.01) {
                      discard;
                  }
                  base.rgb *= pow(texel.rgb, vec3(2.2));
              }
              vec3 colour = base.rgb;
              if (u_flat < 0.5) {
                  vec3 v = normalize(u_camera - v_world);
                  colour = base.rgb * vec3(1.45, 1.4, 1.32) * max(dot(n, -u_keyDir), 0.0);
                  colour += base.rgb * vec3(0.24, 0.28, 0.36) * max(dot(n, -u_rimDir), 0.0);
                  colour += base.rgb * mix(vec3(0.02, 0.024, 0.03), vec3(0.08, 0.09, 0.11), n.y * 0.5 + 0.5);
                  vec3 h = normalize(-u_keyDir + v);
                  colour += vec3(u_shine) * pow(max(dot(n, h), 0.0), 40.0);
              }
              float grey = dot(colour, vec3(0.299, 0.587, 0.114));
              colour = mix(vec3(grey) * vec3(0.8, 0.85, 0.9), colour, u_saturation);
              if (u_ghost > 0.0) {
                  // Scan lines across the table, faded to their average where they'd be finer than the pixels
                  float k = v_world.z * 10.0 - u_time * 0.6;
                  float w = max(fwidth(k), 1e-4);
                  float lines = smoothstep(0.8 - w, 0.8 + w, abs(fract(k) - 0.5) * 2.0);
                  lines = mix(lines, 0.2, smoothstep(0.15, 0.4, w));
                  vec3 hollow = vec3(0.01, 0.02, 0.03) + vec3(0.25, 0.6, 0.85) * (0.15 + grey * 0.7 + lines * 0.2);
                  colour = mix(colour, hollow, u_ghost);
              }
              colour = (colour + u_emissive) * u_dim;
              colour = u_flat < 0.5 ? aces(colour) : clamp(colour, 0.0, 1.0);
              fragColor = vec4(pow(colour, vec3(1.0 / 2.2)), alpha);
          }
          """;

    /**
     * The hologram over the table, drawn additively, every piece of it a quad flat on the table with uv from -1 to 1
     * (a line's runs along it in uv.x and from 0 to 1 across it in uv.y). a_kind is what a vertex belongs to and a
     * parameter for it. The hexagons are flat-topped, as the units' counters, and measured in corner radii.
     * <ul>
     *     <li>0: a line; the parameter is how fast its dashes run, 0 for a solid line.</li>
     *     <li>1: a ring round a unit's counter; the parameter is its style: 0 steady, 1 pulsing, 2 dashed and turning
     *     (work under way), 3 picked, 4 pointed at, 5 dashed and still (an empty place).</li>
     *     <li>3: a deployed unit's empty counter, pinging outward.</li>
     *     <li>4: chevrons running round a counter (a unit on its way somewhere).</li>
     *     <li>5: a unit's tile, far out; the parameter is its style: 0 filled, 1 hollow (no crew), 2 hatched (laid
     *     up), 3 dashed (deployed).</li>
     *     <li>6: a laid-up unit's cover hatched over its counter; the parameter pulses it while it's being laid up or
     *     brought back.</li>
     *     <li>7: a pip; the parameter is its shape: 0 a diamond (where a unit is), 1 a crescent (a tired crew), 2 a
     *     hollow square (a unit in none of its formation's slots).</li>
     *     <li>8: a run of a folded formation's readiness strip, uv.x counting its units across, each parted from the
     *     next by a pixel while there's room.</li>
     *     <li>9: the top of the formation a carried load would be dropped into, washed with light.</li>
     *     <li>10: a slot nobody fills, a still dashed ring; the parameter is the weight class it calls for, 0 any, 1
     *     ultra light, a dot, then 2 light to 6 super heavy, one bar to five.</li>
     * </ul>
     */
    static final String HOLO_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec2 a_texCoord0;
          in vec4 a_color;
          in vec2 a_kind;
          uniform mat4 u_projView;
          out vec2 v_uv;
          out vec4 v_colour;
          out vec2 v_kind;
          void main() {
              v_uv = a_texCoord0;
              v_colour = a_color;
              v_kind = a_kind;
              gl_Position = u_projView * vec4(a_position, 1.0);
          }
          """;

    static final String HOLO_FRAGMENT = """
          #version 330 core
          in vec2 v_uv;
          in vec4 v_colour;
          in vec2 v_kind;
          uniform float u_time;
          out vec4 fragColor;

          const float PI = 3.14159265;

          // How far out p is, in corner radii of a flat-topped hexagon about the middle
          float hexRadius(vec2 p) {
              vec2 q = abs(p);
              return max(q.y, q.x * 0.8660254 + q.y * 0.5) / 0.8660254;
          }

          // The band of r from inner to outer, at least a pixel and a half wide, its edges smoothed over a pixel
          float band(float r, float inner, float outer) {
              float w = max(fwidth(r), 1e-5);
              outer = max(outer, inner + 1.5 * w);
              return smoothstep(inner - w, inner + w, r) * (1.0 - smoothstep(outer - w, outer + w, r));
          }

          float within(float r, float edge) {
              float w = max(fwidth(r), 1e-5);
              return 1.0 - smoothstep(edge - w, edge + w, r);
          }

          // Alternating on and off as k counts up, smoothed over a pixel, evened out where finer than the pixels
          float dashes(float k) {
              float w = max(fwidth(k), 1e-5);
              float on = smoothstep(0.5 - w, 0.5 + w, abs(fract(k) - 0.5) * 2.0);
              return mix(on, 0.5, smoothstep(0.15, 0.4, w));
          }

          // How far round the middle p is, 0 to 1 clockwise from straight up the screen
          float turn(vec2 p) {
              return fract(atan(p.x, p.y) / (2.0 * PI) + 1.0);
          }

          void main() {
              int kind = int(v_kind.x + 0.5);
              int style = int(v_kind.y + 0.5);
              vec2 p = v_uv;
              float light = 0.0;
              if (kind == 0) {
                  // A core a pixel and a half across whatever the zoom, and a faint glow about it
                  float d = abs(p.y - 0.5) / max(fwidth(p.y), 1e-5);
                  float core = 1.0 - smoothstep(0.5, 1.5, d);
                  float glow = exp(-d * 0.7) * 0.3;
                  float dash = v_kind.y == 0.0 ? 1.0 : mix(0.35, 1.2, dashes(p.x * 2.0 - u_time * v_kind.y));
                  light = (core * 1.1 + glow) * dash;
              } else if (kind == 1) {
                  float r = hexRadius(p);
                  if (style == 3) {
                      light = band(r, 0.9, 0.99) * mix(0.5, 1.5, dashes(turn(p) * 12.0 - u_time * 0.5));
                  } else if (style == 4) {
                      light = band(r, 0.91, 0.96) * 0.9;
                  } else {
                      float ring = band(r, 0.8, 0.87);
                      if (style == 1) {
                          ring *= 0.6 + 0.4 * sin(u_time * 3.2);
                      } else if (style == 2) {
                          ring *= mix(0.15, 1.2, dashes(turn(p) * 9.0 - u_time * 0.35));
                      } else if (style == 5) {
                          ring *= mix(0.1, 1.0, dashes(turn(p) * 12.0));
                      }
                      light = ring;
                  }
              } else if (kind == 3) {
                  float r = hexRadius(p);
                  float t = fract(u_time * 0.4);
                  float front = mix(0.8, 1.0, t);
                  light = band(r, 0.8, 0.86) * mix(0.15, 0.9, dashes(turn(p) * 12.0))
                        + band(r, front - 0.04, front) * (1.0 - t) * 1.1;
              } else if (kind == 4) {
                  float r = length(p);
                  float across = (r - 0.88) / 0.11;
                  float slot = fract(turn(p) * 8.0 - u_time * 0.5);
                  float shape = abs(slot - 0.5 - abs(across - 0.5) * 0.4) - 0.08;
                  float w = max(fwidth(shape), 1e-5);
                  light = band(r, 0.88, 0.99) * (1.0 - smoothstep(-w, w, shape)) * 1.4;
              } else if (kind == 5) {
                  float r = hexRadius(p);
                  float fill = within(r, 0.8);
                  float rim = band(r, 0.72, 0.8);
                  if (style == 0) {
                      light = fill * 0.45 + rim * 0.55;
                  } else if (style == 1) {
                      light = fill * 0.05 + rim * 0.9;
                  } else if (style == 2) {
                      light = fill * mix(0.05, 0.5, dashes((p.x - p.y) * 2.5)) + rim * 0.5;
                  } else {
                      light = fill * 0.06 + rim * mix(0.1, 0.9, dashes(turn(p) * 12.0));
                  }
              } else if (kind == 6) {
                  float r = hexRadius(p);
                  float pulse = v_kind.y > 0.5 ? 0.55 + 0.45 * sin(u_time * 3.0) : 1.0;
                  light = within(r, 0.8) * mix(0.03, 0.32, dashes((p.x - p.y) * 2.5)) * pulse;
              } else if (kind == 7) {
                  if (style == 1) {
                      light = within(length(p), 0.85) * (1.0 - within(length(p - vec2(0.4, 0.3)), 0.68)) * 1.2;
                  } else if (style == 2) {
                      float box = max(abs(p.x), abs(p.y));
                      light = band(box, 0.5, 0.8) * 1.2;
                  } else {
                      light = within(abs(p.x) + abs(p.y), 0.85) * 1.2;
                  }
              } else if (kind == 8) {
                  float w = max(fwidth(p.x), 1e-5);
                  float edge = abs(fract(p.x + 0.5) - 0.5) / w;
                  light = (w < 0.25 ? smoothstep(0.4, 1.2, edge) : 1.0) * 0.8;
              } else if (kind == 9) {
                  light = 0.1 + 0.02 * sin(u_time * 4.0);
              } else if (kind == 10) {
                  float r = hexRadius(p);
                  light = band(r, 0.8, 0.86) * mix(0.1, 1.0, dashes(turn(p) * 12.0)) + within(r, 0.8) * 0.05;
                  if (style >= 1) {
                      // The weight class in bars across the slot's lower half, side by side about the middle
                      float bars = style == 1 ? 1.0 : float(style - 1);
                      float across = p.x / 0.16 + bars * 0.5;
                      float wx = max(fwidth(across), 1e-5);
                      float inside = smoothstep(-wx, wx, across) * (1.0 - smoothstep(bars - wx, bars + wx, across));
                      float cell = fract(across);
                      float bar = smoothstep(0.22 - wx, 0.22 + wx, cell) * (1.0 - smoothstep(0.78 - wx, 0.78 + wx, cell));
                      float tall = style == 1 ? 0.04 : 0.1;
                      float wy = max(fwidth(p.y), 1e-5);
                      light += bar * inside * (1.0 - smoothstep(tall - wy, tall + wy, abs(p.y + 0.42))) * 0.9;
                  }
              }
              fragColor = vec4(v_colour.rgb * light * v_colour.a, 1.0);
          }
          """;
}
