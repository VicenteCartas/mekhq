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

/** The holotable room's GLSL: the lit props and floor, and the hologram's additive layers. */
final class HoloShaders {
    private HoloShaders() {
    }

    static final String PROP_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec3 a_normal;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          out vec3 v_world;
          out vec3 v_normal;
          out vec2 v_uv;
          void main() {
              v_world = a_position;
              v_normal = a_normal;
              v_uv = a_texCoord0;
              gl_Position = u_projView * vec4(a_position, 1.0);
          }
          """;

    /**
     * Metallic-roughness shading under the room's lights: a dim key, two coloured rims and the hologram's own flickering
     * light, the stations' own lights, a hemisphere ambient and a faked reflection, then fog, filmic tone mapping and
     * sRGB. {@code u_floor} 1 turns on the deck's plating, the props' contact shadows and the table's shadow from the
     * hologram's light; 2 the wall's plating and its trim lights.
     */
    static final String PROP_FRAGMENT = """
          #version 330 core
          in vec3 v_world;
          in vec3 v_normal;
          in vec2 v_uv;
          uniform sampler2D u_base;
          uniform sampler2D u_normalMap;
          uniform sampler2D u_metalRough;
          uniform sampler2D u_glow;
          uniform vec4 u_baseFactor;
          uniform vec3 u_emissive;
          uniform float u_metallic;
          uniform float u_roughness;
          uniform vec3 u_camera;
          uniform vec3 u_lightDir[3];
          uniform vec3 u_lightColor[3];
          uniform vec3 u_holoLight;
          uniform vec3 u_holoColor;
          uniform vec3 u_fog;
          uniform float u_fogDensity;
          uniform float u_glowPulse;
          uniform float u_floor;
          uniform float u_tableRadius;
          uniform vec3 u_pointLight[2];
          uniform vec3 u_pointColor[2];
          uniform vec4 u_blockers[2];
          out vec4 fragColor;

          const float PI = 3.14159265;

          mat3 cotangentFrame(vec3 n, vec3 p, vec2 uv) {
              vec3 dp1 = dFdx(p);
              vec3 dp2 = dFdy(p);
              vec2 duv1 = dFdx(uv);
              vec2 duv2 = dFdy(uv);
              vec3 dp2perp = cross(dp2, n);
              vec3 dp1perp = cross(n, dp1);
              vec3 t = dp2perp * duv1.x + dp1perp * duv2.x;
              vec3 b = dp2perp * duv1.y + dp1perp * duv2.y;
              float invmax = inversesqrt(max(max(dot(t, t), dot(b, b)), 1e-20));
              return mat3(t * invmax, b * invmax, n);
          }

          vec3 brdf(vec3 l, vec3 radiance, vec3 n, vec3 v, vec3 albedo, float metal, float rough) {
              vec3 h = normalize(l + v);
              float nl = max(dot(n, l), 0.0);
              float nv = max(dot(n, v), 1e-4);
              float nh = max(dot(n, h), 0.0);
              float vh = max(dot(v, h), 0.0);
              float a = rough * rough;
              float a2 = a * a;
              float d = nh * nh * (a2 - 1.0) + 1.0;
              float ndf = a2 / (PI * d * d);
              float k = (rough + 1.0) * (rough + 1.0) / 8.0;
              float g = nv / (nv * (1.0 - k) + k) * nl / (nl * (1.0 - k) + k);
              vec3 f0 = mix(vec3(0.04), albedo, metal);
              vec3 f = f0 + (1.0 - f0) * pow(1.0 - vh, 5.0);
              vec3 specular = ndf * g * f / (4.0 * nv * max(nl, 1e-4) + 1e-4);
              vec3 diffuse = (1.0 - f) * (1.0 - metal) * albedo / PI;
              return (diffuse + specular) * radiance * nl;
          }

          vec3 aces(vec3 x) {
              return clamp(x * (2.51 * x + 0.03) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
          }

          void main() {
              vec3 albedo = pow(texture(u_base, v_uv).rgb, vec3(2.2)) * u_baseFactor.rgb;
              vec4 surface = texture(u_metalRough, v_uv);
              float rough = clamp(surface.g * u_roughness, 0.05, 1.0);
              float metal = clamp(surface.b * u_metallic, 0.0, 1.0);
              vec3 n = normalize(v_normal);
              if (!gl_FrontFacing) {
                  n = -n;
              }
              vec3 mapped = texture(u_normalMap, v_uv).xyz * 2.0 - 1.0;
              n = normalize(cotangentFrame(n, v_world, v_uv) * mapped);
              vec3 v = normalize(u_camera - v_world);
              float occlusion = 1.0;
              float holoShadow = 1.0;
              float trim = 0.0;
              if (u_floor > 1.5) {
                  // Wall plates: a seam every 1.6 m along, every 1.15 m up; trim lights near the floor and the top
                  float along = atan(v_world.x, v_world.z) * length(v_world.xz);
                  vec2 cell = abs(fract(vec2(along / 1.6, v_world.y / 1.15)) - 0.5);
                  float seam = smoothstep(0.49, 0.497, max(cell.x, cell.y));
                  albedo *= 1.0 - 0.5 * seam;
                  rough = mix(rough, 0.9, seam);
                  occlusion = mix(0.45, 1.0, smoothstep(0.0, 0.7, v_world.y));
                  trim = exp(-pow((v_world.y - 2.9) * 60.0, 2.0)) + exp(-pow((v_world.y - 0.12) * 80.0, 2.0)) * 0.6;
              } else if (u_floor > 0.5) {
                  // Deck plates: a seam every metre, a bolt row every half
                  vec2 cell = abs(fract(v_world.xz) - 0.5);
                  float seam = smoothstep(0.494, 0.499, max(cell.x, cell.y));
                  albedo *= 1.0 - 0.55 * seam;
                  rough = mix(rough, 0.9, seam);
                  float centre = length(v_world.xz);
                  occlusion = mix(0.35, 1.0, smoothstep(u_tableRadius * 0.35, u_tableRadius * 1.25, centre));
                  holoShadow = smoothstep(u_tableRadius * 0.75, u_tableRadius * 1.6, centre);
                  for (int i = 0; i < 2; i++) {
                      vec4 b = u_blockers[i];
                      float apart = length(v_world.xz - b.xy);
                      occlusion *= mix(1.0 - b.w, 1.0, smoothstep(b.z * 0.3, b.z * 1.15, apart));
                  }
              }
              vec3 colour = vec3(0.0);
              for (int i = 0; i < 3; i++) {
                  colour += brdf(normalize(-u_lightDir[i]), u_lightColor[i], n, v, albedo, metal, rough);
              }
              vec3 toHolo = u_holoLight - v_world;
              float holoDistance = length(toHolo);
              // The projector doesn't light its own lens: the light fades out over the emitter and its ring.
              holoShadow *= smoothstep(0.09, 0.2, length(v_world.xz - u_holoLight.xz));
              vec3 holo = u_holoColor / (holoDistance * holoDistance + 0.35) * holoShadow;
              colour += brdf(toHolo / holoDistance, holo, n, v, albedo, metal, rough);
              for (int i = 0; i < 2; i++) {
                  vec3 toLight = u_pointLight[i] - v_world;
                  float apart = length(toLight);
                  colour += brdf(toLight / apart, u_pointColor[i] / (apart * apart + 0.25), n, v, albedo, metal, rough);
              }
              vec3 ambient = mix(vec3(0.004, 0.005, 0.007), vec3(0.022, 0.03, 0.042), n.y * 0.5 + 0.5);
              colour += ambient * albedo * (1.0 - metal * 0.6) * occlusion;
              vec3 r = reflect(-v, n);
              vec3 f0 = mix(vec3(0.04), albedo, metal);
              vec3 environment = mix(vec3(0.004), vec3(0.03, 0.045, 0.06), smoothstep(-0.3, 0.9, r.y));
              environment += u_holoColor * 0.05 * pow(max(dot(r, normalize(toHolo)), 0.0), 8.0) * holoShadow;
              colour += environment * f0 * (1.0 - rough * 0.7) * occlusion;
              colour *= occlusion;
              colour += pow(texture(u_glow, v_uv).rgb, vec3(2.2)) * u_emissive * u_glowPulse;
              colour += vec3(0.05, 0.16, 0.24) * trim;
              float fog = 1.0 - exp(-u_fogDensity * length(u_camera - v_world));
              colour = mix(colour, u_fog, fog);
              fragColor = vec4(pow(aces(colour), vec3(1.0 / 2.2)), 1.0);
          }
          """;

    static final String STAR_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec4 a_color;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          uniform mat4 u_model;
          uniform vec3 u_camera;
          uniform float u_pixels;
          uniform float u_time;
          uniform float u_intensity;
          out vec4 v_colour;
          void main() {
              vec4 world = u_model * vec4(a_position, 1.0);
              float size = a_texCoord0.x;
              float phase = a_texCoord0.y;
              float twinkle = 0.82 + 0.18 * sin(u_time * (1.3 + fract(phase * 7.1)) + phase * 40.0);
              // A scan band climbs the hologram, brightening what it passes
              float band = exp(-pow((fract(u_time * 0.2) * 0.12 - 0.06 - (world.y - u_model[3].y)) * 70.0, 2.0));
              v_colour = vec4(a_color.rgb * (twinkle + band * 1.5) * u_intensity, a_color.a);
              gl_Position = u_projView * world;
              gl_PointSize = clamp(size * u_pixels / max(length(u_camera - world.xyz), 0.1), 1.5, 40.0);
          }
          """;

    static final String STAR_FRAGMENT = """
          #version 330 core
          in vec4 v_colour;
          out vec4 fragColor;
          void main() {
              float r = length(gl_PointCoord - 0.5) * 2.0;
              float core = smoothstep(0.35, 0.0, r);
              float halo = exp(-r * r * 5.0) * 0.45;
              float light = (core + halo) * v_colour.a;
              if (light < 0.004) {
                  discard;
              }
              fragColor = vec4(v_colour.rgb * light, 1.0);
          }
          """;

    static final String HOLO_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          uniform mat4 u_model;
          out vec2 v_uv;
          out vec3 v_world;
          void main() {
              v_uv = a_texCoord0;
              vec4 world = u_model * vec4(a_position, 1.0);
              v_world = world.xyz;
              gl_Position = u_projView * world;
          }
          """;

    /**
     * The projections' layers, drawn additively: {@code u_mode} 0 the disc the map stands on (rings, spokes and a
     * sweeping arc, uv its plane in -1..1), 1 the beam rising from the emitter (uv.y its height, 0 at the lens); then the
     * stations', hazed by the room's air: 2 a layer {@code u_layer} of {@code u_layers} of the operations table's
     * hologram (uv the plate's metres, {@code u_half} its half size), its hexes and objectives on the first and a contour
     * of its relief on each above; 3 a screen {@code u_layer} of the comms console (uv 0 to 1, v down), a trace of the
     * traffic or, at {@code u_alert}, a blinking call; 4 a status board (uv 0 to 1, {@code u_half} its half size) with
     * the text in {@code u_text}.
     */
    static final String HOLO_FRAGMENT = """
          #version 330 core
          in vec2 v_uv;
          in vec3 v_world;
          uniform vec3 u_colour;
          uniform float u_time;
          uniform int u_mode;
          uniform float u_intensity;
          uniform vec3 u_camera;
          uniform vec3 u_accent;
          uniform vec2 u_half;
          uniform float u_layer;
          uniform float u_layers;
          uniform float u_alert;
          uniform float u_haze;
          uniform sampler2D u_text;
          out vec4 fragColor;
          const float PI = 3.14159265;
          float line(float value, float width) {
              float d = abs(fract(value + 0.5) - 0.5);
              return smoothstep(width, 0.0, d / fwidth(value));
          }
          float random(vec2 p) {
              return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
          }
          float noise(vec2 p) {
              vec2 i = floor(p);
              vec2 f = fract(p);
              f = f * f * (3.0 - 2.0 * f);
              return mix(mix(random(i), random(i + vec2(1.0, 0.0)), f.x),
                    mix(random(i + vec2(0.0, 1.0)), random(i + vec2(1.0, 1.0)), f.x), f.y);
          }
          /** A sector's relief, 0 to 1, over the plate's metres. */
          float relief(vec2 p) {
              float h = noise(p * 2.2 + 3.1) * 0.6 + noise(p * 5.1 + 7.7) * 0.28 + noise(p * 11.0) * 0.12;
              return clamp((h - 0.2) / 0.65, 0.0, 1.0);
          }
          /** How far inside its flat-topped hex {@code p} is, in hex widths: 0 on an edge, 0.5 at the centre. */
          float hexEdge(vec2 p) {
              p = p.yx;
              const vec2 s = vec2(1.0, 1.7320508);
              vec4 c = floor(vec4(p, p - vec2(0.5, 1.0)) / s.xyxy) + 0.5;
              vec4 h = vec4(p - c.xy * s, p - (c.zw + 0.5) * s);
              vec2 q = abs(dot(h.xy, h.xy) < dot(h.zw, h.zw) ? h.xy : h.zw);
              return 0.5 - max(dot(q, s * 0.5), q.x);
          }
          /** The frame of a panel {@code u_half} in half size at {@code p}, in metres: a hairline and corner brackets. */
          float frame(vec2 p, float pixel) {
              vec2 edge = u_half - abs(p);
              float inner = min(edge.x, edge.y);
              float hairline = 1.0 - smoothstep(0.0, pixel * 1.5, inner);
              vec2 corner = step(u_half - 0.12, abs(p));
              float bracket = (1.0 - smoothstep(0.008, 0.008 + pixel * 1.5, inner)) * corner.x * corner.y;
              return hairline * 0.55 + bracket * 0.9;
          }
          void main() {
              float light = 0.0;
              vec3 rgb;
              if (u_mode == 0) {
                  float r = length(v_uv);
                  if (r > 1.02) {
                      discard;
                  }
                  float angle = atan(v_uv.y, v_uv.x);
                  float rings = line(r * 6.0, 1.2) * 0.22;
                  float spokes = line(angle / (2.0 * PI) * 12.0, 1.0) * 0.1 * smoothstep(0.15, 0.3, r);
                  float rim = exp(-pow((r - 1.0) * 40.0, 2.0)) * 0.9 + exp(-pow((r - 0.97) * 120.0, 2.0)) * 0.4;
                  float ticks = line(angle / (2.0 * PI) * 72.0, 1.0) * step(0.93, r) * step(r, 0.99) * 0.5;
                  float sweep = fract((angle / (2.0 * PI)) - u_time * 0.12);
                  float arc = pow(sweep, 10.0) * 0.35 * smoothstep(1.0, 0.2, r);
                  float fill = 0.035 * smoothstep(1.0, 0.0, r);
                  light = (rings + spokes + rim + ticks + arc) * smoothstep(1.02, 0.98, r) + fill;
              } else if (u_mode == 1) {
                  float h = v_uv.y;
                  float scan = 0.75 + 0.25 * sin(v_world.y * 220.0 - u_time * 6.0);
                  // Seen from above the cone stacks over the map's centre, so it fades as the view turns downward.
                  float above = smoothstep(0.45, 0.9, abs(normalize(u_camera - v_world).y));
                  light = ((1.0 - h) * (1.0 - h) * 0.2 * scan + exp(-h * 30.0) * 0.15) * mix(1.0, 0.3, above);
              }
              rgb = u_colour * light;
              if (u_mode == 2) {
                  vec2 p = v_uv;
                  vec2 edge = u_half - abs(p);
                  float inside = step(0.0, min(edge.x, edge.y));
                  float pixel = length(fwidth(p));
                  float h = relief(p);
                  float sweepX = mix(-u_half.x, u_half.x, fract(u_time * 0.12));
                  float behind = p.x - sweepX;
                  float sweep = exp(-pow(behind * 60.0, 2.0)) + step(behind, 0.0) * exp(behind * 5.0) * 0.2;
                  if (u_layer < 0.5) {
                      float size = 0.07;
                      float grid = 1.0 - smoothstep(0.0, pixel / size * 1.3, hexEdge(p / size));
                      light = grid * (0.12 + h * 0.22) + frame(p, pixel) + sweep * 0.3 + 0.04 + h * 0.05;
                      float pips = 0.0;
                      vec2 sites[3] = vec2[3](vec2(-0.42, 0.16), vec2(0.28, -0.22), vec2(0.55, 0.3));
                      for (int i = 0; i < 3; i++) {
                          vec2 q = abs(p - sites[i]);
                          float d = q.x + q.y;
                          pips += 1.0 - smoothstep(0.0, pixel * 1.5, abs(d - 0.035));
                          pips += 1.0 - smoothstep(0.012, 0.012 + pixel, d);
                      }
                      float pulse = 0.6 + 0.4 * sin(u_time * 5.0);
                      rgb = u_colour * light + mix(u_colour, u_accent * (1.0 + pulse), u_alert) * pips * 0.7;
                  } else {
                      float level = u_layer / u_layers;
                      // Bounded: flat where the relief clamps, fwidth is 0 and smoothstep undefined
                      float contour = 1.0 - smoothstep(0.0, clamp(fwidth(h) * 1.5, 0.003, 0.03), abs(h - level));
                      rgb = u_colour * contour * (0.5 - level * 0.25) * (1.0 + sweep * 0.8);
                  }
                  rgb *= inside;
              } else if (u_mode == 3) {
                  vec2 uv = vec2(v_uv.x, 1.0 - v_uv.y);
                  float scan = 0.8 + 0.2 * sin(uv.y * 300.0 - u_time * 10.0);
                  float bezel = smoothstep(0.0, 0.06, uv.x) * smoothstep(1.0, 0.94, uv.x)
                        * smoothstep(0.0, 0.06, uv.y) * smoothstep(1.0, 0.94, uv.y);
                  float seed = u_layer * 1.7;
                  float wave = 0.58 + 0.16 * sin(uv.x * 17.0 + u_time * (2.0 + seed) + seed * 3.0)
                        * sin(uv.x * 4.3 - u_time * 1.1 + seed);
                  float trace = exp(-pow((uv.y - wave) * 45.0, 2.0));
                  float grid = max(line(uv.x * 8.0, 1.0), line(uv.y * 6.0, 1.0)) * 0.12;
                  float tick = floor(u_time * 0.8 + seed);
                  float row = floor(uv.y * 9.0);
                  float text = step(uv.y, 0.32) * step(0.35, fract(uv.y * 9.0)) * step(fract(uv.x * 28.0), 0.7)
                        * step(random(vec2(floor(uv.x * 28.0), row) + tick), 0.6)
                        * step(uv.x, 0.35 + 0.55 * random(vec2(row, tick)));
                  float idle = (trace * 0.9 + grid + text * 0.35 + 0.05) * scan * bezel;
                  float blink = step(0.45, fract(u_time * 1.3));
                  float band = step(abs(uv.y - 0.58), 0.13) * step(abs(uv.x - 0.5), 0.38);
                  float stripes = step(0.5, fract((uv.x + uv.y) * 10.0 - u_time * 1.5)) * (1.0 - band)
                        * step(abs(uv.y - 0.58), 0.19) * step(abs(uv.x - 0.5), 0.44);
                  float call = (band * (0.3 + 0.4 * blink) + stripes * 0.25 + text * 0.4 + 0.05) * scan * bezel;
                  rgb = mix(u_colour * idle, u_accent * call, u_alert);
              } else if (u_mode == 4) {
                  vec2 p = (v_uv - 0.5) * u_half * 2.0;
                  float pixel = length(fwidth(p));
                  float text = texture(u_text, v_uv).r;
                  float scan = 0.85 + 0.15 * sin(v_uv.y * 380.0 - u_time * 6.0);
                  float divider = (1.0 - smoothstep(0.0, pixel * 1.5, abs(v_uv.y - 0.79) * u_half.y * 2.0))
                        * step(0.06, v_uv.x) * step(v_uv.x, 0.42);
                  float roll = exp(-pow((fract(v_uv.y + u_time * 0.07) - 0.5) * 10.0, 2.0)) * 0.05;
                  light = (text * 1.3 + frame(p, pixel) * 0.6 + divider * 0.4 + 0.03 + roll) * scan;
                  rgb = u_colour * light;
              }
              if (u_mode >= 2) {
                  rgb *= exp(-u_haze * length(u_camera - v_world));
              }
              fragColor = vec4(rgb * u_intensity, 1.0);
          }
          """;

    /** A full-screen fill in display space: the room fading into the star map's dark as the camera dives. */
    static final String VEIL_VERTEX = """
          #version 330 core
          in vec2 a_position;
          void main() {
              gl_Position = vec4(a_position, 0.0, 1.0);
          }
          """;

    static final String VEIL_FRAGMENT = """
          #version 330 core
          uniform vec4 u_colour;
          out vec4 fragColor;
          void main() {
              fragColor = u_colour;
          }
          """;
}
