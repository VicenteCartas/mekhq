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
 * The star map's GLSL, drawn additively over the dark: the systems as glowing points, rings marking the force's
 * system and the one under the cursor or chosen, and lines for the route, the jump range and the grid.
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
          out vec4 v_colour;
          void main() {
              float size = a_texCoord0.x;
              float phase = a_texCoord0.y;
              float twinkle = 0.9 + 0.1 * sin(u_time * (0.9 + fract(phase * 7.1)) + phase * 40.0);
              v_colour = vec4(a_color.rgb * twinkle, a_color.a);
              gl_Position = u_projView * vec4(a_position, 1.0);
              gl_PointSize = clamp(size * u_pixels / max(length(u_camera - a_position), 0.1), 2.5 * size, 26.0);
          }
          """;

    /** A marker ring: a_texCoord0 is (its size in pixels, how much it pulses). */
    static final String RING_VERTEX = """
          #version 330 core
          in vec3 a_position;
          in vec4 a_color;
          in vec2 a_texCoord0;
          uniform mat4 u_projView;
          uniform float u_time;
          out vec4 v_colour;
          void main() {
              v_colour = a_color;
              gl_Position = u_projView * vec4(a_position, 1.0);
              gl_PointSize = a_texCoord0.x * (1.0 + 0.12 * a_texCoord0.y * sin(u_time * 3.5));
          }
          """;

    static final String RING_FRAGMENT = """
          #version 330 core
          in vec4 v_colour;
          out vec4 fragColor;
          void main() {
              float r = length(gl_PointCoord - 0.5) * 2.0;
              float ring = smoothstep(0.12, 0.0, abs(r - 0.78));
              float glow = exp(-pow((r - 0.78) * 6.0, 2.0)) * 0.35;
              float light = (ring + glow) * v_colour.a;
              if (light < 0.004) {
                  discard;
              }
              fragColor = vec4(v_colour.rgb * light, 1.0);
          }
          """;

    /** A line, fading out with its distance from u_focus beyond u_fade light years. */
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
          uniform float u_fade;
          out vec4 fragColor;
          void main() {
              float fade = 1.0 - smoothstep(u_fade * 0.5, u_fade, length(v_world.xz - u_focus.xz));
              fragColor = vec4(v_colour.rgb * v_colour.a * fade, 1.0);
          }
          """;
}
