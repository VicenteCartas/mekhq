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
     * light, a hemisphere ambient and a faked reflection, then fog, filmic tone mapping and sRGB. {@code u_floor} turns
     * on the deck's plating, the table's contact shadow and its shadow from the hologram's light.
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
              if (u_floor > 0.5) {
                  // Deck plates: a seam every metre, a bolt row every half
                  vec2 cell = abs(fract(v_world.xz) - 0.5);
                  float seam = smoothstep(0.494, 0.499, max(cell.x, cell.y));
                  albedo *= 1.0 - 0.55 * seam;
                  rough = mix(rough, 0.9, seam);
                  float centre = length(v_world.xz);
                  occlusion = mix(0.35, 1.0, smoothstep(u_tableRadius * 0.35, u_tableRadius * 1.25, centre));
                  holoShadow = smoothstep(u_tableRadius * 0.75, u_tableRadius * 1.6, centre);
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
              vec3 ambient = mix(vec3(0.004, 0.005, 0.007), vec3(0.022, 0.03, 0.042), n.y * 0.5 + 0.5);
              colour += ambient * albedo * (1.0 - metal * 0.6) * occlusion;
              vec3 r = reflect(-v, n);
              vec3 f0 = mix(vec3(0.04), albedo, metal);
              vec3 environment = mix(vec3(0.004), vec3(0.03, 0.045, 0.06), smoothstep(-0.3, 0.9, r.y));
              environment += u_holoColor * 0.05 * pow(max(dot(r, normalize(toHolo)), 0.0), 8.0) * holoShadow;
              colour += environment * f0 * (1.0 - rough * 0.7) * occlusion;
              colour *= occlusion;
              colour += pow(texture(u_glow, v_uv).rgb, vec3(2.2)) * u_emissive * u_glowPulse;
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
          out vec4 v_colour;
          void main() {
              vec4 world = u_model * vec4(a_position, 1.0);
              float size = a_texCoord0.x;
              float phase = a_texCoord0.y;
              float twinkle = 0.82 + 0.18 * sin(u_time * (1.3 + fract(phase * 7.1)) + phase * 40.0);
              // A scan band climbs the hologram, brightening what it passes
              float band = exp(-pow((fract(u_time * 0.2) * 0.12 - 0.06 - (world.y - u_model[3].y)) * 70.0, 2.0));
              v_colour = vec4(a_color.rgb * (twinkle + band * 1.5), a_color.a);
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
     * The projection's layers, drawn additively: {@code u_mode} 0 the disc the map stands on (rings, spokes and a
     * sweeping arc, uv its plane in -1..1), 1 the beam rising from the emitter (uv.y its height, 0 at the lens).
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
          out vec4 fragColor;
          const float PI = 3.14159265;
          float line(float value, float width) {
              float d = abs(fract(value + 0.5) - 0.5);
              return smoothstep(width, 0.0, d / fwidth(value));
          }
          void main() {
              float light;
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
              } else {
                  float h = v_uv.y;
                  float scan = 0.75 + 0.25 * sin(v_world.y * 220.0 - u_time * 6.0);
                  // Seen from above the cone stacks over the map's centre, so it fades as the view turns downward.
                  float above = smoothstep(0.45, 0.9, abs(normalize(u_camera - v_world).y));
                  light = ((1.0 - h) * (1.0 - h) * 0.2 * scan + exp(-h * 30.0) * 0.15) * mix(1.0, 0.3, above);
              }
              fragColor = vec4(u_colour * light * u_intensity, 1.0);
          }
          """;
}
