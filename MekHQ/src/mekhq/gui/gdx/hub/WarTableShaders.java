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
 * The war table's GLSL: the map laid over the board the game's renderer drew, the facilities and counters standing on
 * it, and the tracking drawn over it.
 */
final class WarTableShaders {
    private WarTableShaders() {
    }

    /** Shared by the lit passes: the sun and sky light, the (optional) fog and the filmic tone map. */
    private static final String LIGHTING = """
          uniform vec3 u_camera;
          uniform vec3 u_keyDir;
          uniform vec3 u_keyColor;
          uniform vec3 u_rimDir;
          uniform vec3 u_rimColor;
          uniform vec3 u_lamp;
          uniform vec3 u_lampColor;
          uniform vec3 u_fog;
          uniform float u_fogDensity;

          vec3 light(vec3 albedo, vec3 n, vec3 world, float shine) {
              vec3 v = normalize(u_camera - world);
              vec3 colour = albedo * u_keyColor * max(dot(n, -u_keyDir), 0.0);
              colour += albedo * u_rimColor * max(dot(n, -u_rimDir), 0.0);
              vec3 toLamp = u_lamp - world;
              float lampDistance = length(toLamp);
              vec3 l = toLamp / lampDistance;
              vec3 lamp = u_lampColor / (lampDistance * lampDistance + 0.4);
              colour += albedo * lamp * max(dot(n, l), 0.0);
              vec3 h = normalize(l + v);
              colour += lamp * shine * pow(max(dot(n, h), 0.0), 48.0);
              colour += albedo * mix(vec3(0.01, 0.012, 0.016), vec3(0.05, 0.06, 0.075), n.y * 0.5 + 0.5);
              return colour;
          }

          vec3 aces(vec3 x) {
              return clamp(x * (2.51 * x + 0.03) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
          }

          vec4 finish(vec3 colour, vec3 world) {
              float fog = 1.0 - exp(-u_fogDensity * length(u_camera - world));
              return vec4(pow(aces(mix(colour, u_fog, fog)), vec3(1.0 / 2.2)), 1.0);
          }
          """;

    /**
     * The map: the board the game's renderer drew of the sector, laid over the screen with its depth, so what's drawn
     * over it after sits in and behind its hills and buildings. Each pixel finds where it lies on the table, and so its
     * hex, from that depth (or where its ray meets the table's plane, off the board); u_cells holds each hex's state.
     */
    static final String COMPOSITE_VERTEX = """
          #version 330 core
          in vec3 a_position;
          out vec2 v_uv;
          void main() {
              v_uv = a_position.xy * 0.5 + 0.5;
              gl_Position = vec4(a_position.xy, 0.0, 1.0);
          }
          """;

    /**
     * Over the board: a fine hex grid, the fog greying what nobody has scouted and burning off what's newly scouted,
     * the line of light along the edge of what's known, the hex hovered and the one picked. Round it the command
     * display's dark field with the grid running on faintly, a vignette and the frame's corner marks. Until the board
     * is drawn the display shows the sector's outline alone; then a scan line paints the ground in, u_open from 0 to 1.
     */
    static final String COMPOSITE_FRAGMENT = """
          #version 330 core
          in vec2 v_uv;
          uniform sampler2D u_colour;
          uniform sampler2D u_depth;
          // The sector's hexes, one texel each: when it was scouted, whether it's water, whether it's there at all
          uniform sampler2D u_cells;
          uniform mat4 u_inverse;
          uniform float u_board;
          uniform float u_open;
          // The grid's origin and a hex's radius; its first column and row, and how many there are of each
          uniform vec3 u_grid;
          uniform vec4 u_cellBox;
          uniform float u_tableTop;
          uniform float u_clock;
          uniform float u_time;
          uniform float u_reveal;
          // The hexes hovered and picked, by column and row; far off the grid for none
          uniform vec2 u_hover;
          uniform vec2 u_selected;
          // Where the opening scan starts and ends, across the table
          uniform vec2 u_scan;
          uniform vec2 u_screen;
          out vec4 fragColor;

          const vec2 SIDES[6] = vec2[](vec2(0.8660254, 0.5), vec2(0.0, 1.0), vec2(-0.8660254, 0.5),
                vec2(-0.8660254, -0.5), vec2(0.0, -1.0), vec2(0.8660254, -0.5));
          const vec3 CYAN = vec3(0.42, 0.78, 0.95);
          const vec3 AMBER = vec3(1.0, 0.68, 0.25);

          vec4 cell(vec2 at) {
              vec2 c = at - u_cellBox.xy;
              if (c.x < 0.0 || c.y < 0.0 || c.x >= u_cellBox.z || c.y >= u_cellBox.w) {
                  return vec4(-1e9, 0.0, 0.0, 0.0);
              }
              return texelFetch(u_cells, ivec2(c + 0.5), 0);
          }

          // The hex across side k: odd columns sit half a row lower
          vec2 across(vec2 at, int k) {
              float odd = mod(at.x, 2.0);
              if (k == 1) {
                  return at + vec2(0.0, 1.0);
              }
              if (k == 4) {
                  return at - vec2(0.0, 1.0);
              }
              float dx = k == 0 || k == 5 ? 1.0 : -1.0;
              float dy = k == 0 || k == 2 ? odd : odd - 1.0;
              return at + vec2(dx, dy);
          }

          // The column and row of the hex round (x, z) on the table
          vec2 hexAt(vec2 p) {
              p = (p - u_grid.xy) / u_grid.z;
              vec3 cube = vec3(2.0 / 3.0 * p.x, -1.0 / 3.0 * p.x + 0.57735027 * p.y, 0.0);
              cube.z = -cube.x - cube.y;
              vec3 rounded = floor(cube + 0.5);
              vec3 off = abs(rounded - cube);
              if (off.x > off.y && off.x > off.z) {
                  rounded.x = -rounded.y - rounded.z;
              } else if (off.y > off.z) {
                  rounded.y = -rounded.x - rounded.z;
              }
              return vec2(rounded.x, rounded.y + (rounded.x - mod(rounded.x, 2.0)) / 2.0);
          }

          float seenBy(float at) {
              return clamp((u_clock - at) / u_reveal, 0.0, 1.0);
          }

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
              mat2 turn = mat2(0.8, -0.6, 0.6, 0.8);
              float sum = 0.0;
              float weight = 0.5;
              for (int i = 0; i < 4; i++) {
                  sum += weight * noise(p);
                  p = turn * p * 2.03 + 17.0;
                  weight *= 0.5;
              }
              return sum / 0.9375;
          }

          vec3 unproject(vec2 uv, float depth) {
              vec4 world = u_inverse * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
              return world.xyz / world.w;
          }

          void main() {
              float depth = texture(u_depth, v_uv).r;
              bool ground = u_board > 0.5 && depth < 1.0;
              vec3 world;
              bool plane = true;
              if (ground) {
                  world = unproject(v_uv, depth);
              } else {
                  vec3 near = unproject(v_uv, 0.0);
                  vec3 ray = unproject(v_uv, 1.0) - near;
                  float t = ray.y < -1e-6 ? (u_tableTop - near.y) / ray.y : -1.0;
                  plane = t > 0.0;
                  world = near + ray * max(t, 0.0);
              }
              vec2 at = hexAt(world.xz);
              float odd = mod(at.x, 2.0);
              vec2 centre = u_grid.xy + u_grid.z * vec2(1.5 * at.x, 1.7320508 * (at.y + odd * 0.5));
              vec2 local = (world.xz - centre) / u_grid.z;
              float pixel = clamp(length(fwidth(world.xz)) / u_grid.z / 0.8660254, 1e-4, 0.2);
              float inset[6];
              float edge = 1.0;
              for (int k = 0; k < 6; k++) {
                  inset[k] = (0.8660254 - dot(local, SIDES[k])) / 0.8660254;
                  edge = min(edge, inset[k]);
              }
              vec4 here = cell(at);
              bool present = here.z > 0.5;
              float width = max(0.012, pixel * 0.9);
              float line = 1.0 - smoothstep(width - pixel * 0.75, width + pixel * 0.75, edge);

              // The display's field: near-black slate, the grid running on faintly, fading away from the sector
              float away = length((world.xz - u_grid.xy) / u_grid.z);
              vec3 field = vec3(0.016, 0.02, 0.026) + vec3(0.01, 0.014, 0.018) * (1.0 - v_uv.y);
              vec3 colour = field;
              if (plane) {
                  colour += vec3(0.05, 0.075, 0.09) * line * (present ? 1.4 : 0.45);
                  if (present) {
                      colour += vec3(0.012, 0.02, 0.026);
                  }
              }

              // The ground, painted in behind the opening scan
              float front = mix(u_scan.x, u_scan.y, u_open);
              float painted = ground && present ? 1.0 - smoothstep(front - u_grid.z * 0.4, front, world.x) : 0.0;
              if (painted > 0.0) {
                  vec3 board = texture(u_colour, v_uv).rgb;
                  float seen = seenBy(here.x);
                  // Once scouted, a ragged front burns the fog off from the hex's middle
                  float clear = seen;
                  float ember = 0.0;
                  if (seen > 0.0 && seen < 1.0) {
                      float reach = seen * 1.55 - 0.1;
                      float ragged = length(local) + (fbm(world.xz / u_grid.z * 3.0) - 0.5) * 0.35;
                      clear = 1.0 - smoothstep(reach - 0.07, reach, ragged);
                      ember = exp(-pow((ragged - reach) * 12.0, 2.0)) * (1.0 - smoothstep(0.75, 1.0, seen));
                  }
                  // What nobody has scouted lies grey and dim under a slow drifting haze
                  float grey = dot(board, vec3(0.3, 0.55, 0.15));
                  vec3 veiled = vec3(grey) * vec3(0.6, 0.68, 0.78) * 0.42;
                  vec2 drift = world.xz / u_grid.z * 0.6 + vec2(u_time * 0.05, u_time * 0.03);
                  float mist = fbm(drift + fbm(drift * 0.7 - u_time * 0.03) * 1.5);
                  veiled += vec3(0.03, 0.045, 0.06) * smoothstep(0.3, 0.9, mist);
                  board = mix(veiled, board, clear);
                  // The grid: hairlines, darker than the ground, so they read without shouting
                  board = mix(board, board * 0.55, line * 0.6);
                  // The edge of what's known: a thin line along the sides that face the fog
                  float frontier = 0.0;
                  for (int k = 0; k < 6 && seen > 0.0; k++) {
                      vec4 next = cell(across(at, k));
                      float facing = seen * (1.0 - seenBy(next.x)) * next.z;
                      if (facing > 0.0) {
                          float core = 1.0 - smoothstep(width - pixel, width + pixel, inset[k]);
                          float halo = 1.0 - smoothstep(0.0, 0.12, inset[k]);
                          frontier = max(frontier, (core + halo * 0.2) * facing);
                      }
                  }
                  board += CYAN * frontier * 0.45 + CYAN * ember * 0.9;
                  float rim = 1.0 - smoothstep(0.0, 0.1 + pixel, edge);
                  if (at == u_hover) {
                      board += CYAN * (0.04 + rim * 0.55);
                  }
                  if (at == u_selected) {
                      board += AMBER * (0.03 + rim * (0.7 + 0.15 * sin(u_time * 4.0)));
                  }
                  colour = mix(colour, board, painted);
              }
              // The scan line itself, a thin trace across the sector as it paints
              if (present && u_open > 0.0 && u_open < 1.0) {
                  float trace = exp(-pow((world.x - front) / (u_grid.z * 0.06), 2.0));
                  colour += CYAN * trace * 0.5 * sin(u_open * 3.14159);
              }

              // The display's vignette, and its frame: corner marks and a hairline border
              vec2 centred = (v_uv - 0.5) * vec2(u_screen.x / u_screen.y, 1.0);
              colour *= mix(1.0, 0.45, smoothstep(0.45, 1.05, length(centred)));
              vec2 px = v_uv * u_screen;
              vec2 inside = min(px, u_screen - px) - 14.0;
              float border = 1.0 - smoothstep(0.0, 1.2, abs(min(inside.x, inside.y)));
              float corner = step(min(inside.x, inside.y), 1.5) * step(-1.5, min(inside.x, inside.y))
                    * step(max(inside.x, inside.y), 56.0);
              colour = mix(colour, vec3(0.5, 0.62, 0.68), max(border * 0.12, corner * 0.55));
              fragColor = vec4(colour, 1.0);
              gl_FragDepth = ground && painted > 0.5 ? depth : 1.0;
          }
          """;

    /**
     * The models standing on the map: the facilities' buildings and the forces' counters. u_model places a counter
     * (identity for batches, already placed); u_drop lifts a counter as it's set down or taken off.
     */
    static final String PROP_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec3 a_normal;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          uniform mat4 u_model;
          uniform float u_drop;
          out vec3 v_world;
          out vec3 v_normal;
          out vec2 v_uv;
          void main() {
              vec3 position = (u_model * vec4(a_position, 1.0)).xyz;
              position.y += u_drop;
              v_world = position;
              v_normal = mat3(u_model) * a_normal;
              v_uv = a_texCoord0;
              gl_Position = u_projView * vec4(position, 1.0);
          }
          """;

    /**
     * Lit as glTF's metallic-roughness materials roughly are; masked ones cut out below u_cutoff. Untextured parts take
     * u_detail mapped over them from three sides when u_detailScale is set, as the board's rocks are; u_dim greys
     * what stands where nobody has scouted, partly while its hex is being scouted.
     */
    static final String PROP_FRAGMENT = """
          #version 330 core
          in vec3 v_world;
          in vec3 v_normal;
          in vec2 v_uv;
          uniform sampler2D u_base;
          uniform sampler2D u_detail;
          uniform vec4 u_baseFactor;
          uniform vec3 u_emissive;
          uniform float u_metallic;
          uniform float u_roughness;
          uniform float u_cutoff;
          uniform float u_textured;
          uniform float u_detailScale;
          uniform float u_dim;
          // Nothing past this, across the table, shows yet: the opening scan hasn't painted it in
          uniform float u_front;
          out vec4 fragColor;
          """ + LIGHTING + """

          void main() {
              if (v_world.x > u_front) {
                  discard;
              }
              vec3 n = normalize(v_normal);
              vec4 base = u_baseFactor;
              if (u_textured > 0.5) {
                  vec4 texel = texture(u_base, v_uv);
                  base *= vec4(pow(texel.rgb, vec3(2.2)), texel.a);
              } else if (u_detailScale > 0.0) {
                  vec3 w = pow(abs(n), vec3(4.0));
                  w /= w.x + w.y + w.z;
                  vec3 p = v_world * u_detailScale;
                  vec3 detail = texture(u_detail, p.zy).rgb * w.x + texture(u_detail, p.xz).rgb * w.y
                        + texture(u_detail, p.xy).rgb * w.z;
                  base.rgb *= pow(detail, vec3(2.2)) * 1.6;
              }
              if (base.a < u_cutoff) {
                  discard;
              }
              if (!gl_FrontFacing) {
                  n = -n;
              }
              if (u_cutoff > 0.0) {
                  // Foliage cards stand on edge to an overhead lamp: light a canopy as the crown it stands for
                  n = normalize(mix(n, vec3(0.0, 1.0, 0.0), 0.5));
              }
              float shine = u_cutoff > 0.0 ? 0.0 : mix(0.04, 0.6, u_metallic) * (1.0 - 0.85 * u_roughness);
              vec3 albedo = base.rgb * (1.0 - 0.6 * u_metallic);
              vec3 colour = light(albedo, n, v_world, shine) + u_emissive;
              float grey = dot(colour, vec3(0.3, 0.55, 0.15));
              colour = mix(colour, mix(colour, vec3(grey) * vec3(0.55, 0.68, 0.95), 0.75) * 0.4, u_dim);
              fragColor = finish(colour, v_world);
          }
          """;

    /**
     * The hologram over the table, drawn additively. a_kind is what a vertex belongs to and a parameter for it; a
     * billboard's corner is in a_texCoord0, its centre in a_position.
     * <ul>
     *     <li>0: a supply link's ribbon, flowing along uv.x towards its source; the parameter is its speed.</li>
     *     <li>1: a ring flat on the table; the parameter is how much of it is lit, a scenario's time left.</li>
     *     <li>2: a beacon's pillar of light, turned to face the camera about the vertical.</li>
     *     <li>3: a point of interest's diamond, facing the camera.</li>
     *     <li>4: a cut road's cross, flat on the table.</li>
     *     <li>5: the projector's sweep across the table as it opens.</li>
     *     <li>6: a hex a carried force may go onto; the parameter is whether it's held over it.</li>
     *     <li>7: a shock ring spreading over the table; the parameter is its age, 0 to 1.</li>
     *     <li>8: a new scenario's strike, a beam driven down onto its hex, facing the camera about the vertical; the
     *     parameter is its age, 0 to 1.</li>
     * </ul>
     */
    static final String HOLO_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec2 a_texCoord0;
          in vec4 a_color;
          in vec2 a_kind;
          uniform mat4 u_projView;
          uniform vec3 u_camera;
          uniform float u_sweep;
          out vec2 v_uv;
          out vec4 v_colour;
          out vec2 v_kind;
          out vec3 v_world;
          void main() {
              vec3 position = a_position;
              int kind = int(a_kind.x + 0.5);
              if (kind == 2) {
                  vec3 toCamera = u_camera - a_position;
                  vec3 right = normalize(vec3(toCamera.z, 0.0, -toCamera.x));
                  position += right * a_texCoord0.x * 0.012 + vec3(0.0, a_texCoord0.y * a_kind.y, 0.0);
              } else if (kind == 3) {
                  vec3 forward = normalize(u_camera - a_position);
                  vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), forward));
                  vec3 up = cross(forward, right);
                  position += (right * a_texCoord0.x + up * a_texCoord0.y) * a_kind.y;
              } else if (kind == 5) {
                  position.x += u_sweep;
              } else if (kind == 8) {
                  vec3 toCamera = u_camera - a_position;
                  vec3 right = normalize(vec3(toCamera.z, 0.0, -toCamera.x));
                  float width = mix(0.045, 0.006, sqrt(a_kind.y));
                  position += right * a_texCoord0.x * width + vec3(0.0, a_texCoord0.y * 1.6, 0.0);
              }
              v_uv = a_texCoord0;
              v_colour = a_color;
              v_kind = a_kind;
              v_world = position;
              gl_Position = u_projView * vec4(position, 1.0);
          }
          """;

    static final String HOLO_FRAGMENT = """
          #version 330 core
          in vec2 v_uv;
          in vec4 v_colour;
          in vec2 v_kind;
          in vec3 v_world;
          uniform float u_time;
          uniform float u_intensity;
          out vec4 fragColor;

          const float PI = 3.14159265;

          void main() {
              int kind = int(v_kind.x + 0.5);
              float light = 0.0;
              if (kind == 0) {
                  float across = 1.0 - abs(v_uv.y * 2.0 - 1.0);
                  float core = pow(across, 3.0);
                  float dash = smoothstep(0.55, 0.9, fract(v_uv.x * 9.0 - u_time * v_kind.y));
                  light = core * (0.35 + dash * 1.2);
              } else if (kind == 1) {
                  float r = length(v_uv);
                  float band = smoothstep(0.72, 0.78, r) * (1.0 - smoothstep(0.9, 0.96, r));
                  float turn = fract(atan(v_uv.x, -v_uv.y) / (2.0 * PI) + 0.5);
                  float lit = turn < v_kind.y ? 1.0 : 0.22;
                  float sweep = pow(fract(turn - u_time * 0.35), 12.0) * 0.8;
                  float inner = exp(-r * r * 6.0) * 0.18;
                  light = band * (lit + sweep) + inner;
              } else if (kind == 2) {
                  float across = 1.0 - abs(v_uv.x);
                  float height = clamp(v_uv.y, 0.0, 1.0);
                  float bands = 0.7 + 0.3 * sin(height * 40.0 - u_time * 6.0);
                  light = pow(across, 2.5) * (1.0 - height) * bands * 1.4;
              } else if (kind == 3) {
                  float d = abs(v_uv.x) + abs(v_uv.y);
                  float edge = smoothstep(0.6, 0.72, d) * (1.0 - smoothstep(0.86, 0.95, d));
                  float fill = (1.0 - smoothstep(0.0, 0.7, d)) * 0.25;
                  light = (edge + fill) * (0.8 + 0.2 * sin(u_time * 3.0));
              } else if (kind == 4) {
                  float a = abs(v_uv.x - v_uv.y);
                  float b = abs(v_uv.x + v_uv.y);
                  float bar = 1.0 - smoothstep(0.08, 0.2, min(a, b));
                  float inside = 1.0 - smoothstep(0.75, 0.95, max(abs(v_uv.x), abs(v_uv.y)));
                  light = bar * inside * (0.6 + 0.4 * step(0.5, fract(u_time * 1.6)));
              } else if (kind == 6) {
                  // A hex a carried force may go onto: its outline breathes, and fills when the force is over it
                  vec2 p = abs(v_uv);
                  float d = max(p.y, p.x * 0.866 + p.y * 0.5) / 0.866;
                  float hot = v_kind.y;
                  float edge = smoothstep(0.78, 0.86, d) * (1.0 - smoothstep(0.9, 0.97, d));
                  float fill = (1.0 - smoothstep(0.86, 0.9, d)) * (0.06 + hot * 0.22);
                  float pulse = 0.65 + 0.35 * sin(u_time * (4.0 + hot * 6.0) - d * 6.0);
                  light = edge * pulse * (1.0 + hot) + fill;
              } else if (kind == 7) {
                  // A landing's shock ring, spreading and fading
                  float age = v_kind.y;
                  float r = length(v_uv);
                  float at = mix(0.2, 1.0, sqrt(age));
                  float band = exp(-pow((r - at) * 14.0, 2.0));
                  light = band * (1.0 - age) * (1.0 - smoothstep(0.95, 1.0, r)) * 1.6;
              } else if (kind == 8) {
                  // Driven down from above in the first moments, its head blazing, then thinning and fading
                  float age = v_kind.y;
                  float reached = 1.0 - clamp(age / 0.14, 0.0, 1.0);
                  float lit = smoothstep(reached - 0.03, reached, v_uv.y);
                  float across = 1.0 - abs(v_uv.x);
                  float beam = pow(across, 3.0) * 1.4 + pow(across, 30.0) * 4.0;
                  float head = exp(-pow((v_uv.y - reached) * 25.0, 2.0)) * 4.0 * step(0.001, reached);
                  light = (beam * (1.0 - v_uv.y * 0.7) * lit + head * across) * pow(1.0 - age, 1.6);
              } else {
                  float across = 1.0 - abs(v_uv.x);
                  light = pow(across, 6.0) * 1.6 + pow(across, 40.0) * 3.0;
              }
              // A scan line climbs the hologram every few seconds
              float scan = 1.0 + 0.6 * exp(-pow((fract(u_time * 0.25) - fract(v_world.y * 4.0)) * 30.0, 2.0));
              fragColor = vec4(v_colour.rgb * light * v_colour.a * u_intensity * scan, 1.0);
          }
          """;
}
