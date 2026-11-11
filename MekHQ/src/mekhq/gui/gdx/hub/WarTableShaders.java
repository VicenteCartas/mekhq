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
 * The war table's GLSL: the operations map's ground, scouted as the real thing and unscouted as a hologram, the
 * field behind it and the display's frame over it; the facilities, scatter and forces standing on it; and the tracking
 * drawn over it.
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
     * Shared by the map's passes: the sector's hexes, one texel each in u_cells (R when it was scouted on the view's
     * clock, G its flags: 1 water, 2 road, 4 city; B 1 + its plotted kind, 0 where there's no hex; A its surface's
     * layer), how to find a hex's neighbours and the hex under a point, and how far the opening scan and a hex's
     * scouting have turned its hologram into ground.
     */
    private static final String HEX = """
          uniform sampler2D u_cells;
          // The grid's origin and a hex's radius; its first column and row, and how many there are of each
          uniform vec3 u_grid;
          uniform vec4 u_cellBox;
          uniform float u_tableTop;
          uniform float u_clock;
          uniform float u_time;
          uniform float u_reveal;
          // How far the opening scan has gone, and where it starts and ends across the table
          uniform float u_open;
          uniform vec2 u_scan;

          const vec2 SIDES[6] = vec2[](vec2(0.8660254, 0.5), vec2(0.0, 1.0), vec2(-0.8660254, 0.5),
                vec2(-0.8660254, -0.5), vec2(0.0, -1.0), vec2(0.8660254, -0.5));
          const vec3 GREEN = vec3(0.22, 1.0, 0.5);

          vec4 cell(vec2 at) {
              vec2 c = at - u_cellBox.xy;
              if (c.x < 0.0 || c.y < 0.0 || c.x >= u_cellBox.z || c.y >= u_cellBox.w) {
                  return vec4(-1e9, 0.0, 0.0, 0.0);
              }
              return texelFetch(u_cells, ivec2(c + 0.5), 0);
          }

          bool flag(vec4 c, float bit) {
              return mod(floor(c.y / bit + 0.5), 2.0) > 0.5;
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

          vec2 centreOf(vec2 at) {
              return u_grid.xy + u_grid.z * vec2(1.5 * at.x, 1.7320508 * (at.y + mod(at.x, 2.0) * 0.5));
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

          // How far the opening scan has painted the ground in at x across the table
          float painted(float x) {
              float front = mix(u_scan.x, u_scan.y, u_open);
              return u_open >= 1.0 ? 1.0 : 1.0 - smoothstep(front - u_grid.z * 0.5, front, x);
          }

          // How solid what's at world is: 1 scouted and painted in, 0 a hologram
          float sight(vec3 world) {
              vec4 c = cell(hexAt(world.xz));
              return c.z < 0.5 ? 1.0 : seenBy(c.x) * painted(world.x);
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

          // A hairline every unit of v, a pixel or so wide however far off
          float hairline(float v, float width) {
              float w = max(fwidth(v), 1e-5);
              return 1.0 - clamp((abs(fract(v - 0.5) - 0.5) - width * w) / w, 0.0, 1.0);
          }

          // The hologram's wire mesh: triangles half a hex's apothem across, lined up with the hexes' sides
          float wires(vec2 p) {
              vec2 q = (p - u_grid.xy) / (u_grid.z * 0.4330127);
              float line = 0.0;
              for (int k = 0; k < 3; k++) {
                  line = max(line, hairline(dot(q, SIDES[k]), 0.15));
              }
              return line;
          }
          """;

    /**
     * The ground: the sector's relief (see WarTableGround), a hex's column and row in a_hex.xy, its skirt in z, the
     * light reaching it, shadowed and occluded, in w.
     */
    static final String TERRAIN_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec3 a_normal;
          in vec4 a_hex;
          uniform mat4 u_projView;
          uniform float u_flatten;
          uniform float u_base;
          out vec3 v_world;
          out vec3 v_normal;
          out vec3 v_relief;
          out float v_height;
          out float v_light;
          flat out vec3 v_hex;
          void main() {
              vec3 position = a_position;
              // The relief as it is, flattened or not, for the contours, the hill shade and the hologram
              v_relief = a_normal;
              v_height = a_position.y;
              // The plotted map lies flat on the table; the skirt's foot stays where it is
              position.y = mix(position.y, max(u_base, min(position.y, u_base + 0.0005)), u_flatten);
              v_world = position;
              v_normal = normalize(mix(a_normal, vec3(0.0, 1.0, 0.0), u_flatten * (1.0 - a_hex.z)));
              v_hex = a_hex.xyz;
              v_light = a_hex.w;
              gl_Position = u_projView * vec4(position, 1.0);
          }
          """;

    /**
     * Scouted ground as the real thing: each hex painted with its surface, blending into its neighbours' along a
     * ragged line, lit with its surface's relief; open water dark and moving; roads and city blocks; faint contour
     * lines and the hex grid as hairlines. Ground nobody has scouted is a green hologram: a wire mesh over the relief,
     * its contours glowing, a scan line drifting over it. A newly scouted hex burns from hologram to ground; the opening
     * scan turns the scouted hexes from hologram to ground across the table. The plotted map (u_plot) is flat colours
     * by kind of ground, contours and roads, as a printed map's. The hex hovered is rimmed cyan, the one picked amber.
     */
    static final String TERRAIN_FRAGMENT = """
          #version 330 core
          in vec3 v_world;
          in vec3 v_normal;
          in vec3 v_relief;
          in float v_height;
          in float v_light;
          flat in vec3 v_hex;
          uniform sampler2DArray u_albedo;
          uniform sampler2DArray u_normals;
          // How many hexes' radii a surface's picture covers
          uniform float u_tile;
          uniform float u_plot;
          // The hexes hovered and picked, by column and row; far off the grid for none
          uniform vec2 u_hover;
          uniform vec2 u_selected;
          out vec4 fragColor;
          """ + LIGHTING + HEX + """

          const vec3 CYAN = vec3(0.42, 0.78, 0.95);
          const vec3 AMBER = vec3(1.0, 0.68, 0.25);
          // The plotted map's colours by kind of ground: open, woods, hills, peaks, arid, snow, built, water, barren
          const vec3 PLOT[9] = vec3[](vec3(0.56, 0.6, 0.42), vec3(0.3, 0.44, 0.27), vec3(0.6, 0.53, 0.36),
                vec3(0.5, 0.42, 0.34), vec3(0.74, 0.64, 0.45), vec3(0.85, 0.88, 0.9), vec3(0.5, 0.5, 0.52),
                vec3(0.27, 0.43, 0.55), vec3(0.48, 0.44, 0.42));

          // Which way the ragged line between two hexes leans, the same seen from either
          float leaning(vec2 a, vec2 b) {
              return a.x < b.x || a.x == b.x && a.y < b.y ? 1.0 : -1.0;
          }

          vec3 plotted(vec4 c) {
              return PLOT[int(clamp(c.z - 1.0, 0.0, 8.0) + 0.5)];
          }

          // A surface's colour at uv: two scales at a turn to each other, so the picture's repeats don't line up, calmed
          // a little toward its average colour as it's seen from high above
          vec3 surface(float layer, vec2 uv, vec2 dx, vec2 dy) {
              const mat2 TURN = mat2(0.8, -0.6, 0.6, 0.8);
              vec3 near = textureGrad(u_albedo, vec3(uv, layer), dx, dy).rgb;
              vec3 far = textureGrad(u_albedo, vec3(TURN * uv * 0.43 + 0.37, layer), TURN * dx * 0.43,
                    TURN * dy * 0.43).rgb;
              return mix(mix(near, far, 0.4), textureLod(u_albedo, vec3(uv, layer), 9.0).rgb, 0.25);
          }

          // How much ink a printed map's symbol for a kind of ground puts at p, in hexes' radii
          float symbols(float kind, vec2 p, float pixel) {
              int k = int(clamp(kind, 0.0, 8.0) + 0.5);
              float aa = pixel * 1.2;
              if (k == 0 || k == 1 || k == 4 || k == 8) {
                  // Open ground's tufts, woods' round trees, arid stipple, barren speckle
                  float size = k == 1 ? 0.16 : k == 0 ? 0.13 : k == 4 ? 0.09 : 0.07;
                  float spot = k == 1 ? 0.036 : k == 0 ? 0.009 : k == 4 ? 0.011 : 0.007;
                  float odds = k == 1 ? 0.8 : k == 0 ? 0.3 : 0.55;
                  float ink = k == 1 ? 0.5 : k == 0 ? 0.3 : 0.45;
                  vec2 q = p / size;
                  vec2 id = floor(q);
                  vec2 f = fract(q) - 0.5 + (vec2(hash(id), hash(id + 7.0)) - 0.5) * 0.5;
                  float d = length(f) * size;
                  float mark = 1.0 - smoothstep(spot - aa, spot + aa, d);
                  if (k == 1) {
                      // A tree's crown printed as a ring round a paler middle
                      mark = mark * mix(1.0, 0.45, 1.0 - smoothstep(spot * 0.55 - aa, spot * 0.55 + aa, d));
                  }
                  return mark * ink * step(1.0 - odds, hash(id + 3.0));
              }
              if (k == 3) {
                  // Peaks: hachures in patches
                  return hairline(dot(p, vec2(0.7071, 0.7071)) * 14.0, 0.5) * smoothstep(0.45, 0.6, noise(p * 3.0))
                        * 0.45;
              }
              if (k == 5) {
                  // Snow and ice: short dashes
                  return hairline(p.y * 12.0, 0.4) * step(0.62, noise(vec2(p.x * 10.0, floor(p.y * 12.0 + 0.5))))
                        * 0.3;
              }
              if (k == 6) {
                  // Built-up: the street grid
                  return max(hairline(p.x / 0.19, 0.4), hairline(p.y / 0.19, 0.4)) * 0.3;
              }
              if (k == 7) {
                  // Water: broken wave lines
                  return hairline(p.y * 6.0 + sin(p.x * 10.0) * 0.12, 0.35) * step(0.5, noise(p * vec2(4.0, 1.5)))
                        * 0.35;
              }
              return 0.0;
          }

          void main() {
              vec2 at = v_hex.xy;
              vec4 here = cell(at);
              vec3 n = normalize(v_normal);
              vec3 toCamera = normalize(u_camera - v_world);
              vec3 relief = normalize(v_relief);
              float height = (v_height - u_tableTop) / u_grid.z;
              vec2 local = (v_world.xz - centreOf(at)) / u_grid.z;
              float pixel = clamp(length(fwidth(v_world.xz)) / u_grid.z / 0.8660254, 1e-4, 0.2);
              float inset[6];
              float edge = 1.0;
              for (int k = 0; k < 6; k++) {
                  inset[k] = (0.8660254 - dot(local, SIDES[k])) / 0.8660254;
                  edge = min(edge, inset[k]);
              }
              float width = max(0.008, pixel * 0.7);
              float hexLine = 1.0 - smoothstep(width - pixel * 0.75, width + pixel * 0.75, edge);

              // How solid: scouted, painted in by the opening scan, a newly scouted hex burning through its middle
              float seen = seenBy(here.x);
              float clear = seen;
              float ember = 0.0;
              if (seen > 0.0 && seen < 1.0) {
                  float reach = seen * 1.55 - 0.1;
                  float ragged = length(local) + (fbm(v_world.xz / u_grid.z * 3.0) - 0.5) * 0.35;
                  clear = 1.0 - smoothstep(reach - 0.07, reach, ragged);
                  ember = exp(-pow((ragged - reach) * 12.0, 2.0)) * (1.0 - smoothstep(0.75, 1.0, seen));
              }
              // Toward the unscouted ground the scouting fades out over the last stretch of the hex, ragged, meeting
              // the hologram halfway across the side, so the edge of what's known dissolves rather than cuts
              float field = clear;
              for (int k = 0; k < 6; k++) {
                  vec4 there = cell(across(at, k));
                  if (there.z > 0.5) {
                      field += (seenBy(there.x) - clear) * 0.5 * (1.0 - smoothstep(0.0, 0.55, inset[k]));
                  }
              }
              float tatter = field + (fbm(v_world.xz / u_grid.z * 3.4) - 0.5) * 0.55;
              clear = smoothstep(0.38, 0.62, tatter);
              float fringe = exp(-pow((tatter - 0.5) / 0.07, 2.0)) * step(0.001, field) * step(field, 0.999);
              float scan = painted(v_world.x);
              clear *= scan;

              // The hologram
              // Lines every 0.08 of a radius, clear of the plains' gentle rise and fall
              float contourStep = (height - 0.05) / 0.08;
              float fade = 1.0 - smoothstep(0.25, 0.6, fwidth(contourStep));
              float contour = hairline(contourStep, 0.2) * fade;
              float major = hairline(contourStep / 5.0, 0.4) * fade;
              float fresnel = pow(1.0 - max(dot(relief, toCamera), 0.0), 3.0);
              vec3 holo = vec3(0.002, 0.012, 0.008) + GREEN * 0.02 * clamp(height * 2.0 + 0.4, 0.0, 1.0);
              holo += GREEN * (wires(v_world.xz) * 0.045 + contour * 0.16 + major * 0.16 + fresnel * 0.12
                    + hexLine * 0.16);
              holo += GREEN * smoothstep(0.97, 1.0, fract(v_world.z / u_grid.z * 0.35 - u_time * 0.12)) * 0.06;
              holo *= 0.9 + 0.1 * noise(vec2(u_time * 7.0, at.x * 3.1 + at.y));

              if (v_hex.z > 0.5) {
                  // The skirt: the slab's dark edge, its lip lit; a hologram's glows faintly
                  vec3 slab = light(vec3(0.045, 0.05, 0.055), n, v_world, 0.0);
                  vec3 ghost = vec3(0.003, 0.016, 0.01) + GREEN * 0.05;
                  fragColor = finish(mix(ghost, slab, clear), v_world);
                  return;
              }

              // The ground: each hex's surface, its neighbours' leaning in along a ragged line
              vec2 uv = v_world.xz / (u_grid.z * u_tile);
              float ragged = (fbm(v_world.xz / u_grid.z * 2.6) - 0.5) * 0.36;
              vec2 uvX = dFdx(uv);
              vec2 uvY = dFdy(uv);
              vec3 albedo = surface(here.w, uv, uvX, uvY);
              vec3 bump = textureGrad(u_normals, vec3(uv, here.w), uvX, uvY).rgb;
              vec3 plot = plotted(here);
              vec2 sym = v_world.xz / u_grid.z;
              float ink = symbols(here.z - 1.0, sym, pixel);
              float water = flag(here, 1.0) ? 1.0 : 0.0;
              float total = 1.0;
              bool road = flag(here, 2.0);
              float paved = 0.0;
              float shoulder = 0.0;
              for (int k = 0; k < 6; k++) {
                  vec2 next = across(at, k);
                  vec4 there = cell(next);
                  if (there.z < 0.5) {
                      continue;
                  }
                  if (road && flag(there, 2.0)) {
                      float t = clamp(dot(local, SIDES[k]) / 0.8660254, 0.0, 1.0);
                      float d = length(local - SIDES[k] * 0.8660254 * t);
                      d += (noise(v_world.xz / u_grid.z * 9.0) - 0.5) * 0.012;
                      paved = max(paved, 1.0 - smoothstep(0.035 - pixel, 0.035 + pixel, d));
                      shoulder = max(shoulder, 1.0 - smoothstep(0.06 - pixel, 0.06 + pixel, d));
                  }
                  float share = 1.0 - smoothstep(-0.3, 0.3, inset[k] + ragged * leaning(at, next));
                  if (share < 0.004) {
                      continue;
                  }
                  float odds = share / max(1.0 - share, 0.02);
                  albedo += surface(there.w, uv, uvX, uvY) * odds;
                  bump += textureGrad(u_normals, vec3(uv, there.w), uvX, uvY).rgb * odds;
                  plot += plotted(there) * odds;
                  ink += symbols(there.z - 1.0, sym, pixel) * odds;
                  water += (flag(there, 1.0) ? 1.0 : 0.0) * odds;
                  total += odds;
              }
              albedo = pow(albedo / total, vec3(2.2));
              albedo *= mix(0.8, 1.15, fbm(v_world.xz / u_grid.z * 0.55));
              plot /= total;
              ink /= total;
              water /= total;
              if (flag(here, 4.0)) {
                  // A city's blocks: roofs of varied greys between darker streets
                  float turn = hash(at) * 1.57;
                  vec2 q = mat2(cos(turn), -sin(turn), sin(turn), cos(turn)) * local / 0.19;
                  vec2 inBlock = abs(fract(q) - 0.5);
                  float block = step(max(inBlock.x, inBlock.y), 0.36);
                  float roof = mix(0.75, 1.35, hash(floor(q) + at));
                  albedo *= mix(1.0, mix(0.5, roof, block), smoothstep(0.05, 0.3, edge));
              }
              vec3 b = bump / total * 2.0 - 1.0;
              vec3 tangent = normalize(vec3(1.0, 0.0, 0.0) - n * n.x);
              vec3 bitangent = cross(tangent, n);
              vec3 lit = normalize(tangent * b.x * 0.8 + bitangent * b.y * 0.8 + n * max(b.z, 0.2));
              vec3 colour = light(albedo, lit, v_world, 0.03);
              // The heights' shadows and the hollows' shade, baked into the relief
              colour *= mix(0.3, 1.0, v_light);

              // Open water: dark, deeper further from shore, long low swells rolling the light over it rather than
              // sparkling, a pale line of surf along the shore
              float shoreline = water + (fbm(v_world.xz / u_grid.z * 4.0) - 0.5) * 0.25;
              float wet = smoothstep(0.4, 0.6, shoreline);
              if (wet > 0.0) {
                  vec2 p = v_world.xz / u_grid.z;
                  float warp = fbm(p * 0.8) * 3.0;
                  vec2 slope = vec2(0.0);
                  const vec3 WAVES[3] = vec3[](vec3(0.9, 0.436, 3.1), vec3(-0.5, 0.866, 4.7), vec3(0.2, -0.98, 7.3));
                  for (int i = 0; i < 3; i++) {
                      vec2 d = WAVES[i].xy;
                      slope += d * cos(dot(p, d) * WAVES[i].z + u_time * (0.5 + 0.15 * float(i)) + warp)
                            * (0.04 / (1.0 + float(i)));
                  }
                  vec3 wn = normalize(vec3(-slope.x, 1.0, -slope.y));
                  float deep = clamp(-height / 0.09, 0.0, 1.0);
                  vec3 sea = mix(vec3(0.035, 0.1, 0.11), vec3(0.01, 0.04, 0.06), deep) * (0.6 + 0.4 * u_keyColor.g);
                  float fres = 0.02 + 0.98 * pow(1.0 - max(dot(wn, toCamera), 0.0), 5.0);
                  vec3 sky = mix(vec3(0.05, 0.075, 0.095), vec3(0.11, 0.15, 0.19),
                        clamp(reflect(-toCamera, wn).y, 0.0, 1.0));
                  float sheen = pow(max(dot(wn, normalize(toCamera - u_keyDir)), 0.0), 60.0) * 0.3;
                  vec3 surf = mix(sea, sky, fres) + u_keyColor * sheen;
                  float foam = exp(-pow((shoreline - 0.47) / 0.035, 2.0)) * (0.7 + 0.3 * sin(u_time * 0.8 + p.x * 5.0));
                  surf = mix(surf, vec3(0.22, 0.25, 0.25), foam * 0.45);
                  colour = mix(colour, surf, wet);
              }
              // Roads: a pale dirt shoulder, a dark metalled core
              colour = mix(colour, light(vec3(0.3, 0.27, 0.22), n, v_world, 0.0), shoulder * (1.0 - wet));
              colour = mix(colour, light(vec3(0.07, 0.068, 0.065), n, v_world, 0.05), paved * (1.0 - wet));
              // Contours and the grid as hairlines, darker than the ground, so they read without shouting
              colour *= 1.0 - (contour * 0.12 + major * 0.12) * (1.0 - wet);
              colour = mix(colour, colour * 0.45 + vec3(0.004, 0.008, 0.006), hexLine * 0.75);

              // The plotted map: flat colours, a little hill shade, contours and roads printed over
              float shade = 0.78 + 0.4 * max(dot(relief, normalize(vec3(-0.6, 0.7, -0.35))), 0.0);
              vec3 print = mix(plot, PLOT[7], wet) * shade;
              // Printed on paper: a faint grain, and the symbols for each kind of ground, fading as they'd blur
              print *= (0.95 + 0.05 * noise(v_world.xz / u_grid.z * 60.0)) * mix(0.8, 1.0, v_light);
              ink = mix(ink, symbols(7.0, sym, pixel), wet);
              print *= 1.0 - ink * (1.0 - smoothstep(0.02, 0.05, pixel));
              print *= 1.0 - (contour * 0.15 + major * 0.25) * (1.0 - wet);
              print = mix(print, vec3(0.42, 0.14, 0.08), max(paved, shoulder * 0.6) * (1.0 - wet));
              print = mix(print, vec3(0.08, 0.09, 0.1), hexLine * 0.6);
              colour = mix(colour, pow(print, vec3(2.2)) * 0.7, u_plot);

              colour = mix(holo, colour, clear);
              // The edge of what's known: a faint glow where the ground dissolves into the hologram
              colour += GREEN * (fringe * 0.12 + ember * 0.8) * scan;
              float rim = 1.0 - smoothstep(0.0, 0.1 + pixel, edge);
              if (at == u_hover) {
                  colour += CYAN * (0.03 + rim * 0.5);
              }
              if (at == u_selected) {
                  colour += AMBER * (0.03 + rim * (0.65 + 0.15 * sin(u_time * 4.0)));
              }
              // The opening scan's line itself, a thin trace across the sector as it paints
              if (u_open > 0.0 && u_open < 1.0) {
                  float front = mix(u_scan.x, u_scan.y, u_open);
                  colour += GREEN * exp(-pow((v_world.x - front) / (u_grid.z * 0.05), 2.0)) * 0.6
                        * sin(u_open * 3.14159);
              }
              fragColor = finish(colour, v_world);
          }
          """;

    /** A quad over the whole screen, for the passes drawn behind and over everything. */
    static final String SCREEN_VERTEX = """
          #version 330 core
          in vec3 a_position;
          out vec2 v_uv;
          void main() {
              v_uv = a_position.xy * 0.5 + 0.5;
              gl_Position = vec4(a_position.xy, 0.0, 1.0);
          }
          """;

    /**
     * Behind the map, the command display's field: near-black slate where the slab of the sector sits, the hex grid
     * running on faintly under it, fading away from it.
     */
    static final String FIELD_FRAGMENT = """
          #version 330 core
          in vec2 v_uv;
          uniform mat4 u_inverse;
          // The grid's origin and a hex's radius, and the field's height
          uniform vec3 u_grid;
          uniform float u_floor;
          out vec4 fragColor;

          vec3 unproject(vec2 uv, float depth) {
              vec4 world = u_inverse * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
              return world.xyz / world.w;
          }

          void main() {
              vec3 colour = vec3(0.014, 0.018, 0.024) + vec3(0.01, 0.013, 0.017) * (1.0 - v_uv.y);
              vec3 near = unproject(v_uv, 0.0);
              vec3 ray = unproject(v_uv, 1.0) - near;
              float t = ray.y < -1e-6 ? (u_floor - near.y) / ray.y : -1.0;
              if (t > 0.0) {
                  vec2 p = (near + ray * t).xz;
                  vec2 q = (p - u_grid.xy) / (u_grid.z * 0.8660254);
                  float line = 0.0;
                  for (int k = 0; k < 3; k++) {
                      float a = 0.5235988 + 1.0471976 * float(k);
                      float v = dot(q, vec2(cos(a), sin(a))) * 0.5 + 0.5;
                      float w = max(fwidth(v), 1e-5);
                      line = max(line, 1.0 - clamp((abs(fract(v - 0.5) - 0.5) - 0.3 * w) / w, 0.0, 1.0));
                  }
                  float away = length((p - u_grid.xy) / u_grid.z);
                  colour += vec3(0.03, 0.05, 0.06) * line * 0.5 / (1.0 + away * 0.04);
              }
              fragColor = vec4(colour, 1.0);
          }
          """;

    /**
     * Over everything, the display's vignette and its frame: corner marks and a hairline border. Blended as
     * premultiplied: what's under it darkens by the vignette, the frame laid over.
     */
    static final String OVERLAY_FRAGMENT = """
          #version 330 core
          in vec2 v_uv;
          uniform vec2 u_screen;
          out vec4 fragColor;
          void main() {
              vec2 centred = (v_uv - 0.5) * vec2(u_screen.x / u_screen.y, 1.0);
              float vignette = mix(1.0, 0.45, smoothstep(0.45, 1.05, length(centred)));
              vec2 px = v_uv * u_screen;
              vec2 inside = min(px, u_screen - px) - 14.0;
              float border = 1.0 - smoothstep(0.0, 1.2, abs(min(inside.x, inside.y)));
              float corner = step(min(inside.x, inside.y), 1.5) * step(-1.5, min(inside.x, inside.y))
                    * step(max(inside.x, inside.y), 56.0);
              float frame = max(border * 0.12, corner * 0.55);
              fragColor = vec4(vec3(0.5, 0.62, 0.68) * frame, 1.0 - vignette * (1.0 - frame));
          }
          """;

    /**
     * The models standing on the map: the facilities' buildings, the small things scattered over the ground and the
     * forces' standees. u_model places a standee (identity for batches, already placed); u_drop lifts one.
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
     * u_detail mapped over them from three sides when u_detailScale is set, as the board's rocks are. What stands where
     * nobody has scouted is part of the hologram, a green glass of its shape, unless u_solid (the forces' standees).
     * u_plot hides all but the standees, as the plotted map shows none of it.
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
          // Nothing past this, across the table, shows yet: the opening scan hasn't painted it in
          uniform float u_front;
          uniform float u_solid;
          out vec4 fragColor;
          """ + LIGHTING + HEX + """

          void main() {
              if (v_world.x > u_front) {
                  discard;
              }
              float real = u_solid > 0.5 ? 1.0 : sight(v_world);
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
              if (real < 1.0) {
                  float fresnel = pow(1.0 - abs(dot(n, normalize(u_camera - v_world))), 2.0);
                  float bands = smoothstep(0.8, 1.0, fract((v_world.y - u_tableTop) / u_grid.z * 10.0 - u_time * 0.4));
                  vec3 ghost = vec3(0.004, 0.02, 0.012) + GREEN * (0.06 + fresnel * 0.5 + bands * 0.1);
                  colour = mix(ghost, colour, real);
              }
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
