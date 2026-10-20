#!/usr/bin/env python3
"""
generate_osrs_guild_glb.py — OSRS Grand Exchange Career Guild 3D World Generator
Generates high-fidelity low-poly glTF binary (.glb) for Google Filament 120Hz engine.

Target: Google Pixel 10 Pro Fold (Tensor G5)
Lookout Architecture: Mr. Popo's Lookout Floating Sky Citadel (DBZ x RuneScape)
  - Cylindrical white marble platform deck with red & white checkered floor tiles
  - Architectural cross-hatching details on masonry and plazas
  - Mr. Popo's Lookout Palace / Temple with golden dome cupolas and colonnade
  - 12 Planet Namek organic towers replacing perimeter trees
  - 7 Sacred Dragon Balls replacing corner light fixtures
  - In-World 3D signage on all 4 Guild buildings
  - Static 3D blue & white cloud clusters in the surrounding sky
  - Dynamic Nodes for 120Hz continuous flight/sparring:
      * Nimbus: Flying Nimbus (Kinto'un)
      * SaiyanGoku: Flying Super Saiyan Goku with golden ki aura
      * SaiyanVegeta: Flying Super Saiyan Vegeta with royal armor & ki aura
      * Beerus: Lord Beerus God of Destruction with purple Hakai aura
"""

import struct
import json
import math

class MultiNodeGLBBuilder:
    def __init__(self):
        self.materials = []
        # node_name -> list of primitives (mat_idx, positions, normals, indices)
        self.nodes = {}
        self.node_order = []
        self.current_node = "OSRS_GrandExchangeTown"
        self._ensure_node(self.current_node)

    def set_node(self, node_name):
        self._ensure_node(node_name)
        self.current_node = node_name

    def _ensure_node(self, node_name):
        if node_name not in self.nodes:
            self.nodes[node_name] = []
            self.node_order.append(node_name)

    def add_material(self, name, base_color, metallic=0.1, roughness=0.8, emissive=0.0):
        idx = len(self.materials)
        r, g, b = base_color[0], base_color[1], base_color[2]
        mat = {
            "name": name,
            "pbrMetallicRoughness": {
                "baseColorFactor": list(base_color),
                "metallicFactor": metallic,
                "roughnessFactor": roughness
            },
            "doubleSided": True  # Eliminates all backface-culling transparency artifacts
        }
        if emissive > 0.0:
            mat["emissiveFactor"] = [
                round(min(1.0, r * emissive), 3),
                round(min(1.0, g * emissive), 3),
                round(min(1.0, b * emissive), 3)
            ]
        self.materials.append(mat)
        return idx

    def _add_primitive(self, mat_idx, positions, normals, indices):
        self.nodes[self.current_node].append((mat_idx, positions, normals, indices))

    def add_box(self, mat_idx, cx, cy, cz, sx, sy, sz):
        hx, hy, hz = sx / 2.0, sy / 2.0, sz / 2.0
        faces = [
            ((0, 1, 0), [(-hx, hy, -hz), (hx, hy, -hz), (hx, hy, hz), (-hx, hy, hz)]),  # Top (+Y)
            ((0, -1, 0), [(-hx, -hy, hz), (hx, -hy, hz), (hx, -hy, -hz), (-hx, -hy, -hz)]), # Bottom (-Y)
            ((0, 0, 1), [(-hx, -hy, hz), (hx, -hy, hz), (hx, hy, hz), (-hx, hy, hz)]),  # Front (+Z)
            ((0, 0, -1), [(hx, -hy, -hz), (-hx, -hy, -hz), (-hx, hy, -hz), (hx, hy, -hz)]), # Back (-Z)
            ((-1, 0, 0), [(-hx, -hy, -hz), (-hx, -hy, hz), (-hx, hy, hz), (-hx, hy, -hz)]), # Left (-X)
            ((1, 0, 0), [(hx, -hy, hz), (hx, -hy, -hz), (hx, hy, -hz), (hx, hy, hz)]),   # Right (+X)
        ]
        positions = []
        normals = []
        indices = []
        base_v = 0
        for norm, verts in faces:
            for vx, vy, vz in verts:
                positions.append((cx + vx, cy + vy, cz + vz))
                normals.append(norm)
            indices.extend([base_v, base_v + 1, base_v + 2, base_v, base_v + 2, base_v + 3])
            base_v += 4
        self._add_primitive(mat_idx, positions, normals, indices)

    def add_cylinder(self, mat_idx, cx, cy, cz, r, h, segments=12):
        positions = []
        normals = []
        indices = []
        hy = h / 2.0
        base_v = 0
        for i in range(segments):
            a1 = 2 * math.pi * i / segments
            a2 = 2 * math.pi * (i + 1) / segments
            x1, z1 = r * math.cos(a1), r * math.sin(a1)
            x2, z2 = r * math.cos(a2), r * math.sin(a2)
            n1 = (math.cos(a1), 0.0, math.sin(a1))
            n2 = (math.cos(a2), 0.0, math.sin(a2))

            positions.extend([(cx + x1, cy - hy, cz + z1), (cx + x2, cy - hy, cz + z2),
                              (cx + x2, cy + hy, cz + z2), (cx + x1, cy + hy, cz + z1)])
            normals.extend([n1, n2, n2, n1])
            indices.extend([base_v, base_v + 1, base_v + 2, base_v, base_v + 2, base_v + 3])
            base_v += 4

        # Top cap (+Y)
        top_center = len(positions)
        positions.append((cx, cy + hy, cz))
        normals.append((0, 1, 0))
        for i in range(segments):
            a = 2 * math.pi * i / segments
            positions.append((cx + r * math.cos(a), cy + hy, cz + r * math.sin(a)))
            normals.append((0, 1, 0))
        for i in range(segments):
            next_i = (i + 1) % segments
            indices.extend([top_center, top_center + 1 + i, top_center + 1 + next_i])

        # Bottom cap (-Y)
        bot_center = len(positions)
        positions.append((cx, cy - hy, cz))
        normals.append((0, -1, 0))
        for i in range(segments):
            a = 2 * math.pi * i / segments
            positions.append((cx + r * math.cos(a), cy - hy, cz + r * math.sin(a)))
            normals.append((0, -1, 0))
        for i in range(segments):
            next_i = (i + 1) % segments
            indices.extend([bot_center, bot_center + 1 + next_i, bot_center + 1 + i])

        self._add_primitive(mat_idx, positions, normals, indices)

    def add_cone(self, mat_idx, cx, cy, cz, r, h, segments=10):
        positions = []
        normals = []
        indices = []
        hy = h / 2.0
        apex = (cx, cy + hy, cz)
        base_v = 0
        for i in range(segments):
            a1 = 2 * math.pi * i / segments
            a2 = 2 * math.pi * (i + 1) / segments
            x1, z1 = cx + r * math.cos(a1), cz + r * math.sin(a1)
            x2, z2 = cx + r * math.cos(a2), cz + r * math.sin(a2)
            nx = math.cos((a1 + a2)/2)
            nz = math.sin((a1 + a2)/2)
            positions.extend([(x1, cy - hy, z1), (x2, cy - hy, z2), apex])
            normals.extend([(nx, 0.5, nz), (nx, 0.5, nz), (0, 1, 0)])
            indices.extend([base_v, base_v + 1, base_v + 2])
            base_v += 3

        bot_center = len(positions)
        positions.append((cx, cy - hy, cz))
        normals.append((0, -1, 0))
        for i in range(segments):
            a = 2 * math.pi * i / segments
            positions.append((cx + r * math.cos(a), cy - hy, cz + r * math.sin(a)))
            normals.append((0, -1, 0))
        for i in range(segments):
            next_i = (i + 1) % segments
            indices.extend([bot_center, bot_center + 1 + next_i, bot_center + 1 + i])

        self._add_primitive(mat_idx, positions, normals, indices)

    def add_sphere(self, mat_idx, cx, cy, cz, r, lat_segs=8, lon_segs=12):
        positions = []
        normals = []
        indices = []
        for i in range(lat_segs + 1):
            theta = -math.pi / 2.0 + math.pi * (i / lat_segs)
            y = cy + r * math.sin(theta)
            ring_r = r * math.cos(theta)
            ny = math.sin(theta)
            cos_t = math.cos(theta)
            for j in range(lon_segs):
                phi = 2.0 * math.pi * (j / lon_segs)
                nx = cos_t * math.cos(phi)
                nz = cos_t * math.sin(phi)
                positions.append((cx + ring_r * math.cos(phi), y, cz + ring_r * math.sin(phi)))
                normals.append((nx, ny, nz))
        for i in range(lat_segs):
            for j in range(lon_segs):
                curr = i * lon_segs + j
                nxt = i * lon_segs + ((j + 1) % lon_segs)
                above = (i + 1) * lon_segs + j
                above_nxt = (i + 1) * lon_segs + ((j + 1) % lon_segs)
                indices.extend([curr, above, nxt])
                indices.extend([nxt, above, above_nxt])
        self._add_primitive(mat_idx, positions, normals, indices)

    def add_hemisphere_bottom(self, mat_idx, cx, cy, cz, r, depth, lat_segs=10, lon_segs=20):
        positions = []
        normals = []
        indices = []
        for i in range(lat_segs + 1):
            u = i / lat_segs
            theta = (math.pi / 2.0) * u
            y = cy - depth * math.sin(theta)
            ring_r = r * math.cos(theta)
            for j in range(lon_segs):
                phi = 2.0 * math.pi * (j / lon_segs)
                x = cx + ring_r * math.cos(phi)
                z = cz + ring_r * math.sin(phi)
                nx = math.cos(theta) * math.cos(phi)
                ny = -math.sin(theta)
                nz = math.cos(theta) * math.sin(phi)
                positions.append((x, y, z))
                normals.append((nx, ny, nz))

        for i in range(lat_segs):
            for j in range(lon_segs):
                curr = i * lon_segs + j
                nxt = i * lon_segs + ((j + 1) % lon_segs)
                below = (i + 1) * lon_segs + j
                below_nxt = (i + 1) * lon_segs + ((j + 1) % lon_segs)
                indices.extend([curr, below, nxt])
                indices.extend([nxt, below, below_nxt])
        self._add_primitive(mat_idx, positions, normals, indices)

    def add_cloud(self, mat_idx, cx, cy, cz, scale=1.0):
        s = scale
        self.add_sphere(mat_idx, cx, cy, cz, 1.4 * s, lat_segs=6, lon_segs=8)
        self.add_sphere(mat_idx, cx + 1.1 * s, cy - 0.2 * s, cz, 1.0 * s, lat_segs=6, lon_segs=8)
        self.add_sphere(mat_idx, cx - 1.1 * s, cy - 0.2 * s, cz, 1.0 * s, lat_segs=6, lon_segs=8)
        self.add_sphere(mat_idx, cx + 0.4 * s, cy + 0.5 * s, cz + 0.3 * s, 0.95 * s, lat_segs=6, lon_segs=8)
        self.add_sphere(mat_idx, cx - 0.5 * s, cy + 0.4 * s, cz - 0.3 * s, 0.90 * s, lat_segs=6, lon_segs=8)
        self.add_sphere(mat_idx, cx, cy - 0.3 * s, cz + 0.6 * s, 0.85 * s, lat_segs=5, lon_segs=7)

    def add_nimbus(self, mat_gold, mat_aura, cx, cy, cz, scale=1.0):
        s = scale
        # Main puffy cloud cushion with flat/dished seating top surface (top at cy + 0.60 * s)
        self.add_sphere(mat_gold, cx, cy - 0.25 * s, cz, 0.85 * s, lat_segs=7, lon_segs=10)
        self.add_sphere(mat_gold, cx + 0.55 * s, cy - 0.35 * s, cz + 0.15 * s, 0.70 * s, lat_segs=6, lon_segs=8)
        self.add_sphere(mat_gold, cx - 0.55 * s, cy - 0.35 * s, cz + 0.15 * s, 0.70 * s, lat_segs=6, lon_segs=8)
        self.add_sphere(mat_gold, cx, cy - 0.30 * s, cz + 0.55 * s, 0.65 * s, lat_segs=6, lon_segs=8)
        # Trailing tail wisps
        self.add_sphere(mat_aura, cx, cy - 0.30 * s, cz - 0.75 * s, 0.48 * s, lat_segs=5, lon_segs=7)
        self.add_sphere(mat_aura, cx, cy - 0.38 * s, cz - 1.30 * s, 0.32 * s, lat_segs=5, lon_segs=6)

    def add_spirit_bomb(self, mat_core, mat_aura, mat_ring, radius=1.6):
        """Constructs Goku's celestial Spirit Bomb (Genki Dama) with glowing core, outer shell, and energy rings"""
        # 1. Inner luminous core
        self.add_sphere(mat_core, 0.0, 0.0, 0.0, radius * 0.75, lat_segs=8, lon_segs=12)
        # 2. Celestial radiant energy shell
        self.add_sphere(mat_aura, 0.0, 0.0, 0.0, radius * 1.05, lat_segs=8, lon_segs=12)
        # 3. Horizontal equatorial energy ring
        self.add_cylinder(mat_ring, 0.0, 0.0, 0.0, radius * 1.40, 0.08 * radius, segments=16)
        # 4. Diagonal energy ring
        positions = []
        normals = []
        indices = []
        r = radius * 1.45
        th = 0.06 * radius
        base_v = 0
        segs = 16
        for i in range(segs):
            a1 = 2 * math.pi * i / segs
            a2 = 2 * math.pi * (i + 1) / segs
            y1, z1 = r * math.cos(a1), r * math.sin(a1)
            y2, z2 = r * math.cos(a2), r * math.sin(a2)
            positions.extend([
                (-th, y1, z1), (th, y1, z1),
                (th, y2, z2), (-th, y2, z2)
            ])
            normals.extend([(1, 0, 0), (1, 0, 0), (1, 0, 0), (1, 0, 0)])
            indices.extend([base_v, base_v + 1, base_v + 2, base_v, base_v + 2, base_v + 3])
            base_v += 4
        self._add_primitive(mat_ring, positions, normals, indices)

    def add_dragon_ball(self, mat_ball, mat_star, mat_gold, cx, cy, cz, stars=4):
        # Sleek circular gold ring socket resting directly on ground
        self.add_cylinder(mat_gold, cx, cy + 0.04, cz, 0.45, 0.08, segments=12)
        # 3D Dragon Ball resting directly on the ground within the land, not floating
        ball_y = cy + 0.38
        self.add_sphere(mat_ball, cx, ball_y, cz, 0.35, lat_segs=8, lon_segs=10)
        star_positions = []
        if stars == 1:
            star_positions = [(0, 0)]
        elif stars == 2:
            star_positions = [(-0.08, 0), (0.08, 0)]
        elif stars == 3:
            star_positions = [(0, 0.08), (-0.08, -0.06), (0.08, -0.06)]
        elif stars == 4:
            star_positions = [(-0.08, 0.08), (0.08, 0.08), (-0.08, -0.08), (0.08, -0.08)]
        elif stars == 5:
            star_positions = [(0, 0.10), (-0.10, 0.02), (0.10, 0.02), (-0.06, -0.09), (0.06, -0.09)]
        elif stars == 6:
            star_positions = [(-0.08, 0.10), (0.08, 0.10), (-0.10, 0), (0.10, 0), (-0.08, -0.10), (0.08, -0.10)]
        else:
            star_positions = [(0, 0), (0, 0.12), (0.11, 0.05), (0.08, -0.10), (-0.08, -0.10), (-0.11, 0.05), (0, -0.12)]

        for sx, sy in star_positions:
            self.add_box(mat_star, cx + sx, ball_y + sy, cz + 0.34, 0.04, 0.04, 0.02)

    def add_building_sign(self, mat_board, mat_gold, mat_emblem, cx, cy, cz, w, h, emblem_type="sword"):
        self.add_box(mat_board, cx, cy, cz, w, h, 0.15)
        self.add_box(mat_gold,  cx, cy, cz + 0.07, w + 0.08, h + 0.08, 0.04)
        if emblem_type == "scroll":
            self.add_box(mat_emblem, cx, cy, cz + 0.11, w * 0.5, h * 0.6, 0.06)
            self.add_cylinder(mat_gold, cx, cy + h * 0.32, cz + 0.11, 0.06, w * 0.55, segments=6)
        elif emblem_type == "tome":
            self.add_box(mat_emblem, cx - w * 0.14, cy, cz + 0.11, w * 0.28, h * 0.6, 0.05)
            self.add_box(mat_emblem, cx + w * 0.14, cy, cz + 0.11, w * 0.28, h * 0.6, 0.05)
            self.add_cone(mat_gold,  cx, cy + h * 0.35, cz + 0.13, 0.06, h * 0.7, segments=4)
        elif emblem_type == "rune":
            self.add_cylinder(mat_emblem, cx, cy, cz + 0.11, min(w, h) * 0.32, 0.06, segments=8)
            self.add_cone(mat_gold, cx, cy, cz + 0.13, min(w, h) * 0.18, 0.12, segments=4)
        elif emblem_type == "courier":
            self.add_box(mat_emblem, cx, cy, cz + 0.11, w * 0.5, h * 0.5, 0.07)
            self.add_box(mat_gold,   cx, cy, cz + 0.13, w * 0.52, 0.05, 0.08)
            self.add_box(mat_gold,   cx, cy, cz + 0.13, 0.05, h * 0.52, 0.08)

    def add_namek_tower(self, m_stone, m_plaster, m_teal, m_crystal, cx, cy, cz):
        """Constructs an organic Planet Namek Tower with dome pod and Porunga emerald crystal"""
        # 1. Sturdy organic footing
        self.add_cylinder(m_stone, cx, cy + 0.25, cz, 0.90, 0.50, segments=12)
        # 2. Organic ivory/teal tower shaft
        self.add_cylinder(m_plaster, cx, cy + 1.85, cz, 0.62, 2.70, segments=12)
        # 3. Decorative middle ring
        self.add_cylinder(m_teal, cx, cy + 2.40, cz, 0.82, 0.22, segments=12)
        # 4. Upper living pod / observation dome
        pod_y = cy + 4.10
        self.add_sphere(m_plaster, cx, pod_y, cz, 1.25, lat_segs=8, lon_segs=12)
        # 5. Balcony ring around the pod
        self.add_cylinder(m_teal, cx, pod_y - 0.20, cz, 1.45, 0.18, segments=12)
        # 6. Oval viewing portholes
        for pang in [0, math.pi * 0.5, math.pi, math.pi * 1.5]:
            px = cx + 1.26 * math.cos(pang)
            pz = cz + 1.26 * math.sin(pang)
            self.add_box(m_stone, px, pod_y + 0.15, pz, 0.22, 0.38, 0.22)
        # 7. Namekian conical roof spire
        self.add_cone(m_teal, cx, pod_y + 1.85, cz, 0.55, 1.50, segments=8)
        # 8. Porunga Emerald Ki Crystal glowing pinnacle
        self.add_sphere(m_crystal, cx, pod_y + 2.75, cz, 0.30, lat_segs=6, lon_segs=8)

    def add_beam_z(self, mat_core, mat_aura, length=8.0, r_core=0.20, r_aura=0.42, segments=12):
        """Constructs a high-energy Ki beam along +Z from z=0 to z=length with core, aura, and leading blast head"""
        for (m, r) in [(mat_core, r_core), (mat_aura, r_aura)]:
            positions = []
            normals = []
            indices = []
            base_v = 0
            for i in range(segments):
                a1 = 2 * math.pi * i / segments
                a2 = 2 * math.pi * (i + 1) / segments
                x1, y1 = r * math.cos(a1), r * math.sin(a1)
                x2, y2 = r * math.cos(a2), r * math.sin(a2)
                nx1, ny1 = math.cos(a1), math.sin(a1)
                nx2, ny2 = math.cos(a2), math.sin(a2)

                # Quad along +Z
                positions.extend([
                    (x1, y1, 0.0), (x2, y2, 0.0),
                    (x2, y2, length), (x1, y1, length)
                ])
                normals.extend([
                    (nx1, ny1, 0.0), (nx2, ny2, 0.0),
                    (nx2, ny2, 0.0), (nx1, ny1, 0.0)
                ])
                indices.extend([base_v, base_v + 1, base_v + 2, base_v, base_v + 2, base_v + 3])
                base_v += 4

            # Front cap at length (+Z)
            cap_center = len(positions)
            positions.append((0.0, 0.0, length))
            normals.append((0.0, 0.0, 1.0))
            for i in range(segments):
                a = 2 * math.pi * i / segments
                positions.append((r * math.cos(a), r * math.sin(a), length))
                normals.append((0.0, 0.0, 1.0))
            for i in range(segments):
                nxt = (i + 1) % segments
                indices.extend([cap_center, cap_center + 1 + i, cap_center + 1 + nxt])

            self._add_primitive(m, positions, normals, indices)

        # Leading flared energy blast sphere at z=length
        self.add_sphere(mat_aura, 0.0, 0.0, length, r_aura * 1.6, lat_segs=6, lon_segs=8)
        self.add_sphere(mat_core, 0.0, 0.0, length, r_core * 1.5, lat_segs=5, lon_segs=6)

    def add_ki_clash_burst(self, mat_core, mat_ring1, mat_ring2, radius=1.0):
        """Constructs an explosive collision burst sphere with expanding shockwave rings and radial spikes"""
        self.add_sphere(mat_core, 0.0, 0.0, 0.0, radius * 0.85, lat_segs=8, lon_segs=10)
        self.add_cylinder(mat_ring1, 0.0, 0.0, 0.0, radius * 1.6, 0.12, segments=12)
        # Cross shockwave ring rotated
        positions = []
        normals = []
        indices = []
        r = radius * 1.8
        th = 0.12
        base_v = 0
        segs = 12
        for i in range(segs):
            a1 = 2 * math.pi * i / segs
            a2 = 2 * math.pi * (i + 1) / segs
            y1, z1 = r * math.cos(a1), r * math.sin(a1)
            y2, z2 = r * math.cos(a2), r * math.sin(a2)
            positions.extend([
                (-th, y1, z1), (th, y1, z1),
                (th, y2, z2), (-th, y2, z2)
            ])
            normals.extend([(1, 0, 0), (1, 0, 0), (1, 0, 0), (1, 0, 0)])
            indices.extend([base_v, base_v + 1, base_v + 2, base_v, base_v + 2, base_v + 3])
            base_v += 4
        self._add_primitive(mat_ring2, positions, normals, indices)

        # Radiating explosive spike cones
        for (dx, dy, dz) in [
            (1.4, 0, 0), (-1.4, 0, 0), (0, 1.4, 0), (0, -1.4, 0),
            (0, 0, 1.4), (0, 0, -1.4), (1.0, 1.0, 0), (-1.0, -1.0, 0)
        ]:
            self.add_cone(mat_core, dx * radius * 0.6, dy * radius * 0.6, dz * radius * 0.6, 0.22 * radius, 0.7 * radius, segments=4)

    def build_glb(self):
        bin_data = bytearray()
        buffer_views = []
        accessors = []
        meshes = []
        nodes = [{"name": "Root", "children": list(range(1, len(self.node_order) + 1))}]

        for node_idx, node_name in enumerate(self.node_order, start=1):
            prims_for_node = self.nodes[node_name]
            mat_groups = {}
            for mat_idx, pos, norm, ind in prims_for_node:
                if mat_idx not in mat_groups:
                    mat_groups[mat_idx] = ([], [], [])
                p_list, n_list, i_list = mat_groups[mat_idx]
                offset = len(p_list)
                p_list.extend(pos)
                n_list.extend(norm)
                i_list.extend([i + offset for i in ind])

            mesh_primitives = []
            for mat_idx, (p_list, n_list, i_list) in mat_groups.items():
                if not i_list:
                    continue
                # Indices
                ind_bytes = bytearray()
                for idx in i_list:
                    ind_bytes.extend(struct.pack('<H', idx))
                while len(ind_bytes) % 4 != 0:
                    ind_bytes.extend(b'\x00')

                ind_bv_idx = len(buffer_views)
                ind_offset = len(bin_data)
                bin_data.extend(ind_bytes)
                buffer_views.append({
                    "buffer": 0,
                    "byteOffset": ind_offset,
                    "byteLength": len(ind_bytes),
                    "target": 34963
                })
                ind_acc_idx = len(accessors)
                accessors.append({
                    "bufferView": ind_bv_idx,
                    "byteOffset": 0,
                    "componentType": 5123,
                    "count": len(i_list),
                    "type": "SCALAR",
                    "max": [max(i_list)],
                    "min": [min(i_list)]
                })

                # Positions
                pos_bytes = bytearray()
                min_pos = [1e9, 1e9, 1e9]
                max_pos = [-1e9, -1e9, -1e9]
                for px, py, pz in p_list:
                    pos_bytes.extend(struct.pack('<fff', px, py, pz))
                    min_pos[0] = min(min_pos[0], px)
                    min_pos[1] = min(min_pos[1], py)
                    min_pos[2] = min(min_pos[2], pz)
                    max_pos[0] = max(max_pos[0], px)
                    max_pos[1] = max(max_pos[1], py)
                    max_pos[2] = max(max_pos[2], pz)
                while len(pos_bytes) % 4 != 0:
                    pos_bytes.extend(b'\x00')

                pos_bv_idx = len(buffer_views)
                pos_offset = len(bin_data)
                bin_data.extend(pos_bytes)
                buffer_views.append({
                    "buffer": 0,
                    "byteOffset": pos_offset,
                    "byteLength": len(pos_bytes),
                    "target": 34962
                })
                pos_acc_idx = len(accessors)
                accessors.append({
                    "bufferView": pos_bv_idx,
                    "byteOffset": 0,
                    "componentType": 5126,
                    "count": len(p_list),
                    "type": "VEC3",
                    "max": max_pos,
                    "min": min_pos
                })

                # Normals
                norm_bytes = bytearray()
                for nx, ny, nz in n_list:
                    norm_bytes.extend(struct.pack('<fff', nx, ny, nz))
                while len(norm_bytes) % 4 != 0:
                    norm_bytes.extend(b'\x00')

                norm_bv_idx = len(buffer_views)
                norm_offset = len(bin_data)
                bin_data.extend(norm_bytes)
                buffer_views.append({
                    "buffer": 0,
                    "byteOffset": norm_offset,
                    "byteLength": len(norm_bytes),
                    "target": 34962
                })
                norm_acc_idx = len(accessors)
                accessors.append({
                    "bufferView": norm_bv_idx,
                    "byteOffset": 0,
                    "componentType": 5126,
                    "count": len(n_list),
                    "type": "VEC3"
                })

                mesh_primitives.append({
                    "attributes": {
                        "POSITION": pos_acc_idx,
                        "NORMAL": norm_acc_idx
                    },
                    "indices": ind_acc_idx,
                    "material": mat_idx
                })

            mesh_idx = len(meshes)
            meshes.append({
                "name": f"Mesh_{node_name}",
                "primitives": mesh_primitives
            })
            nodes.append({
                "mesh": mesh_idx,
                "name": node_name
            })

        gltf = {
            "asset": {"version": "2.0", "generator": "PoposLookout_OSRS_Guild_3D"},
            "scene": 0,
            "scenes": [{"nodes": [0]}],
            "nodes": nodes,
            "meshes": meshes,
            "materials": self.materials,
            "buffers": [{"byteLength": len(bin_data)}],
            "bufferViews": buffer_views,
            "accessors": accessors
        }

        json_bytes = json.dumps(gltf, separators=(',', ':')).encode('utf-8')
        while len(json_bytes) % 4 != 0:
            json_bytes += b' '

        while len(bin_data) % 4 != 0:
            bin_data.extend(b'\x00')

        total_length = 12 + 8 + len(json_bytes) + 8 + len(bin_data)
        glb = bytearray()
        glb.extend(b'glTF')
        glb.extend(struct.pack('<I', 2))
        glb.extend(struct.pack('<I', total_length))
        glb.extend(struct.pack('<I', len(json_bytes)))
        glb.extend(b'JSON')
        glb.extend(json_bytes)
        glb.extend(struct.pack('<I', len(bin_data)))
        glb.extend(b'BIN\x00')
        glb.extend(bin_data)

        return bytes(glb)


def generate():
    b = MultiNodeGLBBuilder()

    # Materials
    m_lookout_white = b.add_material("LookoutWhiteMarble", (0.95, 0.94, 0.92, 1.0), roughness=0.50, emissive=0.75)
    m_lookout_rim   = b.add_material("LookoutGoldenRim",   (1.00, 0.84, 0.12, 1.0), metallic=0.7, roughness=0.20, emissive=0.85)
    m_spire_gold    = b.add_material("SpireGoldenCore",    (0.96, 0.78, 0.10, 1.0), metallic=0.8, roughness=0.15, emissive=0.90)
    m_stone_dark    = b.add_material("GraniteFoundation",  (0.25, 0.23, 0.20, 1.0), roughness=0.85, emissive=0.45)
    m_stone         = b.add_material("StoneMasonry",       (0.55, 0.51, 0.45, 1.0), roughness=0.80, emissive=0.55)
    m_flagstone     = b.add_material("PlazaFlagstone",     (0.68, 0.65, 0.60, 1.0), roughness=0.70, emissive=0.65)
    m_cobble        = b.add_material("CobblestonePath",    (0.48, 0.44, 0.38, 1.0), roughness=0.85, emissive=0.55)
    m_brick_red     = b.add_material("VarrockBrick",       (0.72, 0.32, 0.20, 1.0), roughness=0.75, emissive=0.60)
    m_tile_crimson  = b.add_material("LookoutTerracotta",  (0.78, 0.24, 0.16, 1.0), roughness=0.65, emissive=0.70)
    m_hatch_line    = b.add_material("CrossHatchLine",     (0.20, 0.18, 0.16, 1.0), roughness=0.90, emissive=0.40)
    m_water         = b.add_material("MoatCanalWater",     (0.10, 0.72, 0.96, 1.0), metallic=0.2, roughness=0.08, emissive=0.90)
    m_canal_border  = b.add_material("CanalStoneCoping",   (0.40, 0.37, 0.32, 1.0), roughness=0.80, emissive=0.50)

    # Architectural Materials
    m_timber        = b.add_material("HeavyTimber",        (0.38, 0.24, 0.12, 1.0), roughness=0.70, emissive=0.50)
    m_wood_plank    = b.add_material("WoodPlank",          (0.58, 0.40, 0.22, 1.0), roughness=0.75, emissive=0.55)
    m_redroof       = b.add_material("VarrockRedRoof",     (0.82, 0.22, 0.14, 1.0), roughness=0.60, emissive=0.70)
    m_blueroof      = b.add_material("WizardBlueRoof",     (0.14, 0.38, 0.88, 1.0), roughness=0.45, emissive=0.80)
    m_gold          = b.add_material("GildedOrnament",     (1.00, 0.85, 0.05, 1.0), metallic=0.8, roughness=0.15, emissive=0.95)
    m_cyan_rune     = b.add_material("CyanKiRune",         (0.00, 0.95, 1.00, 1.0), metallic=0.2, roughness=0.05, emissive=1.0)
    m_lantern       = b.add_material("LanternAmberGlow",   (1.00, 0.92, 0.35, 1.0), roughness=0.1, emissive=1.0)
    m_parchment     = b.add_material("ParchmentScroll",    (0.96, 0.90, 0.76, 1.0), roughness=0.90, emissive=0.80)
    m_wax_seal      = b.add_material("WaxSealRed",         (0.92, 0.12, 0.12, 1.0), roughness=0.3, emissive=0.90)
    m_awning_blue   = b.add_material("StallAwningBlue",    (0.15, 0.45, 0.85, 1.0), roughness=0.6, emissive=0.70)
    m_awning_white  = b.add_material("StallAwningWhite",   (0.95, 0.95, 0.95, 1.0), roughness=0.7, emissive=0.80)

    # Planet Namek Materials
    m_namek_stone   = b.add_material("NamekStoneBase",     (0.70, 0.74, 0.65, 1.0), roughness=0.85, emissive=0.60)
    m_namek_plaster = b.add_material("NamekOrganicPlaster",(0.92, 0.94, 0.90, 1.0), roughness=0.60, emissive=0.75)
    m_namek_teal    = b.add_material("NamekTealTrim",      (0.12, 0.55, 0.58, 1.0), roughness=0.50, emissive=0.80)
    m_namek_crystal = b.add_material("PorungaEmeraldKi",   (0.05, 1.00, 0.55, 1.0), metallic=0.3, roughness=0.05, emissive=1.0)

    # DBZ Sky, Nimbus & Saiyans Materials
    m_cloud_white   = b.add_material("PuffyCloudWhite",    (0.98, 0.99, 1.00, 1.0), roughness=0.95, emissive=0.90)
    m_cloud_blue    = b.add_material("PuffyCloudSkyBlue",  (0.82, 0.90, 0.98, 1.0), roughness=0.90, emissive=0.85)
    m_nimbus_gold   = b.add_material("FlyingNimbusGold",   (1.00, 0.88, 0.12, 1.0), metallic=0.3, roughness=0.20, emissive=1.0)
    m_nimbus_trail  = b.add_material("NimbusTrailAura",    (1.00, 0.96, 0.40, 1.0), metallic=0.2, roughness=0.10, emissive=1.0)
    m_dragonball    = b.add_material("DragonBallAmber",    (1.00, 0.54, 0.04, 1.0), metallic=0.3, roughness=0.10, emissive=1.0)
    m_star_red      = b.add_material("DragonStarRed",      (0.96, 0.08, 0.08, 1.0), roughness=0.2, emissive=1.0)
    m_skin          = b.add_material("SaiyanSkin",         (0.94, 0.78, 0.62, 1.0), roughness=0.8, emissive=0.65)
    m_tunic_blu     = b.add_material("SaiyanNavySuit",     (0.12, 0.25, 0.65, 1.0), roughness=0.7, emissive=0.70)
    m_turtle_gi     = b.add_material("TurtleGiOrange",     (1.00, 0.42, 0.06, 1.0), roughness=0.7, emissive=0.75)
    m_saiyan_ssj    = b.add_material("SuperSaiyanHair",    (1.00, 0.92, 0.15, 1.0), metallic=0.3, roughness=0.15, emissive=1.0)
    m_saiyan_aura   = b.add_material("SuperSaiyanKi",      (1.00, 0.96, 0.25, 1.0), metallic=0.4, roughness=0.10, emissive=1.0)
    m_kamehameha    = b.add_material("KamehamehaBlue",     (0.00, 0.92, 1.00, 1.0), metallic=0.5, roughness=0.05, emissive=1.0)
    m_kamehameha_core = b.add_material("KamehamehaWhiteCore", (0.92, 0.98, 1.00, 1.0), metallic=0.2, roughness=0.02, emissive=1.0)

    # Kid Gohan Materials
    m_gohan_green   = b.add_material("KidGohanGreenTunic", (0.16, 0.65, 0.28, 1.0), roughness=0.7, emissive=0.65)
    m_gohan_yellow  = b.add_material("KidGohanYellowAccent", (0.98, 0.86, 0.12, 1.0), roughness=0.6, emissive=0.80)
    m_hair_black    = b.add_material("KidGohanHairBlack",  (0.08, 0.08, 0.08, 1.0), roughness=0.8, emissive=0.30)

    # Anime Shaded Aura Materials (Charging Shade Ki Effects)
    m_saiyan_aura_bright = b.add_material("SuperSaiyanAuraBright", (1.00, 0.96, 0.25, 0.90), metallic=0.3, roughness=0.08, emissive=1.0)
    m_saiyan_aura_deep   = b.add_material("SuperSaiyanAuraDeep",   (1.00, 0.55, 0.05, 0.75), metallic=0.2, roughness=0.12, emissive=0.85)
    m_vegeta_aura_purple = b.add_material("VegetaAuraPurple",     (0.78, 0.12, 0.95, 0.85), metallic=0.3, roughness=0.08, emissive=0.95)
    m_vegeta_aura_cyan   = b.add_material("VegetaAuraCyan",       (0.18, 0.48, 1.00, 0.75), metallic=0.2, roughness=0.10, emissive=0.85)

    # Vegeta Purple Galick Gun Beam Materials
    m_galick_core        = b.add_material("GalickGunVioletCore",   (0.98, 0.88, 1.00, 1.0), metallic=0.2, roughness=0.02, emissive=1.0)
    m_galick_aura        = b.add_material("GalickGunPurpleAura",   (0.82, 0.10, 0.98, 0.90), metallic=0.4, roughness=0.05, emissive=1.0)

    # Goku Spirit Bomb (Genki Dama) Celestial Energy Materials
    m_spiritbomb_core    = b.add_material("SpiritBombWhiteCore",   (0.92, 0.98, 1.00, 1.0), metallic=0.1, roughness=0.02, emissive=1.0)
    m_spiritbomb_aura    = b.add_material("SpiritBombCyanAura",    (0.18, 0.78, 1.00, 0.65), metallic=0.3, roughness=0.05, emissive=0.90)
    m_spiritbomb_spark   = b.add_material("SpiritBombEnergyRing",  (0.60, 0.95, 1.00, 0.85), metallic=0.2, roughness=0.05, emissive=1.0)

    # Final Flash Beam Materials (Brilliant Golden-Yellow & White Core)
    m_finalflash      = b.add_material("FinalFlashGold",      (1.00, 0.88, 0.08, 1.0), metallic=0.5, roughness=0.05, emissive=1.0)
    m_finalflash_core = b.add_material("FinalFlashWhiteCore", (1.00, 1.00, 0.88, 1.0), metallic=0.2, roughness=0.02, emissive=1.0)

    # Lord Beerus Materials
    m_beerus_purple = b.add_material("BeerusFelineSkin",   (0.62, 0.40, 0.78, 1.0), roughness=0.70, emissive=0.70)
    m_beerus_sash   = b.add_material("BeerusWhiteSash",    (0.95, 0.95, 0.95, 1.0), roughness=0.60, emissive=0.75)
    m_hakai_aura    = b.add_material("HakaiDestructionKi", (0.88, 0.12, 0.95, 1.0), metallic=0.4, roughness=0.05, emissive=1.0)
    m_hakai_core    = b.add_material("HakaiVioletCore",    (0.98, 0.82, 1.00, 1.0), metallic=0.2, roughness=0.02, emissive=1.0)
    m_hakai_beam    = b.add_material("HakaiBeamPurple",    (0.92, 0.10, 0.96, 1.0), metallic=0.4, roughness=0.05, emissive=1.0)

    # Beam Clash Impact Materials
    m_clash_burst   = b.add_material("ClashShockwaveCore", (1.00, 1.00, 0.90, 1.0), metallic=0.1, roughness=0.02, emissive=1.0)
    m_clash_ring    = b.add_material("ClashEnergyRing",    (1.00, 0.88, 0.15, 1.0), metallic=0.5, roughness=0.05, emissive=1.0)

    # =============================================================
    # NODE 0: OSRS_GrandExchangeTown (Static Platform & Architecture)
    # =============================================================
    b.set_node("OSRS_GrandExchangeTown")

    # 1. Popo's Lookout Sky Citadel Base
    # Cylindrical white marble platform deck (radius 18.0, height 0.9)
    b.add_cylinder(m_lookout_white, 0, -0.45, 0, 18.0, 0.90, segments=32)
    b.add_cylinder(m_lookout_rim, 0, 0.05, 0, 18.15, 0.18, segments=32)
    b.add_cylinder(m_lookout_rim, 0, -0.85, 0, 18.05, 0.15, segments=32)

    # Spherical hemisphere underbelly curving down to -7.8
    b.add_hemisphere_bottom(m_lookout_white, 0, -0.85, 0, 17.9, depth=6.95, lat_segs=10, lon_segs=24)
    b.add_cylinder(m_lookout_rim, 0, -2.60, 0, 15.6, 0.25, segments=24)
    b.add_cylinder(m_lookout_rim, 0, -5.10, 0, 11.2, 0.25, segments=20)
    b.add_cylinder(m_lookout_rim, 0, -7.20, 0, 4.8, 0.35, segments=16)

    # Central golden spire descending straight down to -26.0
    b.add_cylinder(m_spire_gold,    0, -16.5, 0, 1.5, 17.5, segments=16)
    b.add_cylinder(m_lookout_rim,   0, -10.5, 0, 2.1, 0.8, segments=12)
    b.add_cylinder(m_lookout_rim,   0, -15.0, 0, 1.9, 0.6, segments=12)
    b.add_cylinder(m_lookout_rim,   0, -20.0, 0, 1.9, 0.6, segments=12)
    b.add_cone(m_cyan_rune,         0, -26.0, 0, 1.5, 1.8, segments=10)

    # -------------------------------------------------------------
    # 2. FLOOR TILES & ARCHITECTURAL CROSS-HATCHING PATTERNS
    # -------------------------------------------------------------
    # Checkered marble and terracotta tiles across the circular deck
    tile_step = 1.8
    grid_range = 10 # -9 to 9
    for ix in range(-grid_range, grid_range + 1):
        for iz in range(-grid_range, grid_range + 1):
            tx = ix * tile_step
            tz = iz * tile_step
            dist = math.sqrt(tx * tx + tz * tz)
            # Only inside the platform perimeter and outside the central fountain
            if 4.8 < dist < 17.1:
                # Checkered alternating tiles
                mat_t = m_lookout_white if (ix + iz) % 2 == 0 else m_tile_crimson
                b.add_box(mat_t, tx, 0.04, tz, tile_step * 0.94, 0.05, tile_step * 0.94)
                # Fine cross-hatching grout line between tiles
                b.add_box(m_hatch_line, tx, 0.02, tz, tile_step * 0.98, 0.02, 0.06)
                b.add_box(m_hatch_line, tx, 0.02, tz, 0.06, 0.02, tile_step * 0.98)

    # Moat / Canal water ring
    b.add_cylinder(m_water, 0, 0.05, 0, 13.8, 0.10, segments=24)
    b.add_cylinder(m_canal_border, 0, 0.09, 0, 14.1, 0.16, segments=24)

    # 4 Stone Arched Bridges at cardinal points
    for rx, rz, rot in [(0, -11.5, 0), (0, 11.5, 0), (-11.5, 0, 90), (11.5, 0, 90)]:
        w_b, l_b = (3.2, 5.0) if rot == 0 else (5.0, 3.2)
        b.add_box(m_stone,     rx, 0.15, rz, w_b, 0.22, l_b)
        b.add_box(m_cobble,    rx, 0.26, rz, w_b - 0.4, 0.08, l_b)
        # Cross-hatching railing details
        if rot == 0:
            b.add_box(m_timber, rx - 1.5, 0.50, rz, 0.18, 0.45, l_b)
            b.add_box(m_timber, rx + 1.5, 0.50, rz, 0.18, 0.45, l_b)
            b.add_box(m_gold,   rx - 1.5, 0.78, rz - 2.2, 0.22, 0.20, 0.22)
            b.add_box(m_gold,   rx + 1.5, 0.78, rz - 2.2, 0.22, 0.20, 0.22)
            b.add_box(m_gold,   rx - 1.5, 0.78, rz + 2.2, 0.22, 0.20, 0.22)
            b.add_box(m_gold,   rx + 1.5, 0.78, rz + 2.2, 0.22, 0.20, 0.22)
        else:
            b.add_box(m_timber, rx, 0.50, rz - 1.5, l_b, 0.45, 0.18)
            b.add_box(m_timber, rx, 0.50, rz + 1.5, l_b, 0.45, 0.18)
            b.add_box(m_gold,   rx - 2.2, 0.78, rz - 1.5, 0.22, 0.20, 0.22)
            b.add_box(m_gold,   rx - 2.2, 0.78, rz + 1.5, 0.22, 0.20, 0.22)
            b.add_box(m_gold,   rx + 2.2, 0.78, rz - 1.5, 0.22, 0.20, 0.22)
            b.add_box(m_gold,   rx + 2.2, 0.78, rz + 1.5, 0.22, 0.20, 0.22)

    # Cross-axial cobblestone pathways connecting all quarters
    b.add_box(m_cobble, 0, 0.08, 0, 3.4, 0.08, 32.0)
    b.add_box(m_cobble, 0, 0.08, 0, 32.0, 0.08, 3.4)

    # Diagonal walkways leading to the 4 guild headquarters
    for dx, dz in [(-5.0, -5.0), (5.0, -5.0), (-5.0, 5.0), (5.0, 5.0)]:
        b.add_box(m_cobble, dx, 0.10, dz, 4.4, 0.08, 4.4)

    # Central Grand Exchange octagonal paved trading floor
    b.add_cylinder(m_flagstone, 0, 0.14, 0, 8.8, 0.12, segments=16)
    b.add_cylinder(m_stone,     0, 0.20, 0, 5.8, 0.10, segments=16)

    # -------------------------------------------------------------
    # 3. MR. POPO'S LOOKOUT PALACE / TEMPLE (NORTH-CENTER)
    # -------------------------------------------------------------
    # Positioned majestically on the northern plaza (px = 0.0, pz = -7.5)
    px, pz = 0.0, -7.5
    # Marble ziggurat terrace base
    b.add_box(m_lookout_white, px, 0.35, pz, 6.8, 0.50, 4.8)
    b.add_box(m_gold,          px, 0.62, pz, 7.0, 0.08, 5.0)

    # Popo's Main Palace Hall (Ivory white with cross-hatch carvings)
    b.add_box(m_lookout_white, px, 1.85, pz, 5.8, 2.40, 3.8)
    # Cross-hatching decorative frieze along the entablature
    b.add_box(m_tile_crimson,  px, 2.95, pz, 6.0, 0.30, 4.0)
    b.add_box(m_gold,          px, 3.15, pz, 6.2, 0.12, 4.2)
    for c_i in range(-5, 6):
        b.add_box(m_hatch_line, px + c_i * 0.55, 2.95, pz + 2.02, 0.04, 0.28, 0.06)

    # Front Colonnade: 4 Elegant White Marble Columns with Golden Capitals
    for col_x in [-2.2, -0.75, 0.75, 2.2]:
        b.add_cylinder(m_lookout_white, px + col_x, 1.70, pz + 2.25, 0.22, 2.20, segments=8)
        b.add_box(m_gold, px + col_x, 2.85, pz + 2.25, 0.50, 0.15, 0.50)
        b.add_box(m_stone, px + col_x, 0.65, pz + 2.25, 0.50, 0.15, 0.50)

    # Grand Golden Dome Cupola of Popo's Lookout Palace
    b.add_cylinder(m_lookout_white, px, 3.35, pz, 2.2, 0.35, segments=16)
    b.add_sphere(m_gold,            px, 4.40, pz, 1.9, lat_segs=8, lon_segs=16)
    b.add_cone(m_spire_gold,        px, 6.30, pz, 0.45, 1.90, segments=8)
    b.add_sphere(m_cyan_rune,       px, 7.35, pz, 0.28, lat_segs=6, lon_segs=8)

    # Flanking Side Cupola Domes (Red & Gold)
    for side_x in [-2.5, 2.5]:
        b.add_cylinder(m_lookout_white, px + side_x, 3.25, pz, 1.1, 0.30, segments=10)
        b.add_sphere(m_tile_crimson,    px + side_x, 3.90, pz, 1.0, lat_segs=7, lon_segs=12)
        b.add_cone(m_gold,              px + side_x, 4.85, pz, 0.25, 0.90, segments=6)

    # Golden Arched Grand Entrance to Popo's Lookout Palace
    b.add_box(m_stone_dark, px, 1.35, pz + 1.95, 1.6, 1.90, 0.20)
    b.add_box(m_gold,       px, 1.35, pz + 2.05, 1.3, 1.70, 0.05)
    # Ornamental golden braziers flanking the palace stairs
    b.add_cylinder(m_gold, px - 3.2, 0.75, pz + 2.3, 0.30, 0.60, segments=8)
    b.add_cone(m_lantern,  px - 3.2, 1.15, pz + 2.3, 0.25, 0.40, segments=6)
    b.add_cylinder(m_gold, px + 3.2, 0.75, pz + 2.3, 0.30, 0.60, segments=8)
    b.add_cone(m_lantern,  px + 3.2, 1.15, pz + 2.3, 0.25, 0.40, segments=6)

    # -------------------------------------------------------------
    # 4. CENTRAL GRAND EXCHANGE FOUNTAIN & TRADE ARCADE
    # -------------------------------------------------------------
    b.add_cylinder(m_stone_dark, 0, 0.42, 0, 3.6, 0.38, segments=12)
    b.add_cylinder(m_water,      0, 0.55, 0, 3.2, 0.18, segments=12)
    b.add_cylinder(m_stone,      0, 0.95, 0, 2.0, 0.45, segments=10)
    b.add_cylinder(m_water,      0, 1.15, 0, 1.7, 0.15, segments=10)
    b.add_cylinder(m_stone,      0, 1.68, 0, 0.85, 1.00, segments=8)
    b.add_cone(m_cyan_rune,      0, 2.50, 0, 0.55, 0.85, segments=6)
    b.add_cone(m_cyan_rune,      0, 2.00, 0, 0.55, -0.85, segments=6)
    b.add_cylinder(m_saiyan_aura, 0, 2.20, 0, 2.5, 0.06, segments=16)
    b.add_cylinder(m_kamehameha,  0, 2.70, 0, 1.3, 0.05, segments=12)

    for fx, fz in [(-2.1, 0), (2.1, 0), (0, -2.1), (0, 2.1)]:
        b.add_box(m_gold, fx, 0.58, fz, 0.32, 0.32, 0.32)

    # -------------------------------------------------------------
    # 5. [NORTH-WEST] BOUNTY BOARD (LIVE JOB RADAR GUILDHALL)
    # -------------------------------------------------------------
    bx, bz = -7.2, -7.2
    b.add_box(m_stone_dark, bx, 0.25, bz, 5.8, 0.45, 5.8)
    b.add_box(m_stone,      bx, 0.52, bz, 5.2, 0.22, 5.2)
    b.add_box(m_brick_red, bx, 1.90, bz, 4.4, 2.60, 4.2)
    b.add_box(m_timber, bx, 1.90, bz + 2.12, 4.44, 0.16, 0.08)
    b.add_box(m_timber, bx, 2.80, bz + 2.12, 4.44, 0.16, 0.08)
    b.add_box(m_timber, bx - 1.4, 1.90, bz + 2.12, 0.16, 2.60, 0.08)
    b.add_box(m_timber, bx + 1.4, 1.90, bz + 2.12, 0.16, 2.60, 0.08)

    b.add_box(m_redroof, bx, 3.70, bz, 4.8, 1.00, 4.6)
    b.add_cone(m_gold,   bx, 4.60, bz, 0.55, 0.85, segments=6)

    b.add_box(m_stone,   bx, 0.65, bz + 2.7, 4.6, 0.18, 1.6)
    b.add_box(m_timber,  bx - 1.8, 1.55, bz + 3.2, 0.22, 1.9, 0.22)
    b.add_box(m_timber,  bx + 1.8, 1.55, bz + 3.2, 0.22, 1.9, 0.22)
    b.add_box(m_redroof, bx, 2.55, bz + 2.8, 4.6, 0.20, 1.6)

    # In-World 3D Signboard
    b.add_building_sign(m_timber, m_gold, m_parchment, bx, 2.95, bz + 3.4, 2.6, 0.65, emblem_type="scroll")
    b.add_box(m_timber,    bx - 0.9, 1.65, bz + 3.0, 1.3, 1.40, 0.12)
    b.add_box(m_parchment, bx - 0.9, 1.65, bz + 3.08, 1.15, 1.25, 0.04)
    b.add_box(m_wax_seal,  bx - 0.9, 2.15, bz + 3.12, 0.15, 0.15, 0.03)

    b.add_box(m_timber,    bx + 0.9, 1.65, bz + 3.0, 1.3, 1.40, 0.12)
    b.add_box(m_parchment, bx + 0.9, 1.65, bz + 3.08, 1.15, 1.25, 0.04)
    b.add_box(m_wax_seal,  bx + 0.9, 2.15, bz + 3.12, 0.15, 0.15, 0.03)

    # -------------------------------------------------------------
    # 6. [NORTH-EAST] SCRIBE TOWER (ATS RESUME & COVER LETTER FORGE)
    # -------------------------------------------------------------
    sx, sz = 7.2, -7.2
    b.add_cylinder(m_stone_dark, sx, 0.35, sz, 3.5, 0.65, segments=12)
    b.add_cylinder(m_stone,      sx, 1.45, sz, 2.9, 1.80, segments=12)
    b.add_box(m_timber, sx, 0.95, sz + 2.75, 0.95, 1.35, 0.20)

    # In-World 3D Signboard
    b.add_building_sign(m_stone_dark, m_gold, m_parchment, sx, 2.05, sz + 2.85, 2.2, 0.65, emblem_type="tome")

    b.add_cylinder(m_timber,     sx, 2.85, sz, 3.2, 0.25, segments=12)
    b.add_cylinder(m_wood_plank, sx, 3.80, sz, 2.6, 1.80, segments=12)
    b.add_cylinder(m_timber,     sx, 3.20, sz, 3.2, 0.45, segments=12)
    for ang in [0, math.pi * 0.5, math.pi, math.pi * 1.5]:
        wx = sx + 2.60 * math.cos(ang)
        wz = sz + 2.60 * math.sin(ang)
        b.add_box(m_lantern, wx, 3.85, wz, 0.32, 0.75, 0.32)

    b.add_cylinder(m_gold,  sx, 4.80, sz, 2.7, 0.20, segments=12)
    b.add_cylinder(m_stone, sx, 5.35, sz, 2.1, 1.00, segments=12)
    b.add_cone(m_blueroof,  sx, 6.85, sz, 2.5, 2.40, segments=12)
    b.add_cylinder(m_gold,  sx, 8.10, sz, 0.45, 0.20, segments=8)
    b.add_cone(m_cyan_rune, sx, 8.75, sz, 0.35, 1.20, segments=6)

    # -------------------------------------------------------------
    # 7. [SOUTH-WEST] SKILL ALTAR (DYNAMIC SKILL MATRIX & TECH RADAR)
    # -------------------------------------------------------------
    ax, az = -7.2, 7.2
    b.add_box(m_stone_dark, ax, 0.25, az, 6.0, 0.45, 6.0)
    b.add_box(m_stone,      ax, 0.55, az, 4.8, 0.30, 4.8)
    b.add_box(m_flagstone,  ax, 0.80, az, 3.5, 0.25, 3.5)

    b.add_box(m_stone_dark, ax, 1.20, az, 1.9, 0.55, 1.9)
    b.add_box(m_cyan_rune,  ax, 1.50, az, 1.5, 0.08, 1.5)
    b.add_cone(m_cyan_rune, ax, 2.35, az, 0.50, 1.10, segments=6)
    b.add_cone(m_cyan_rune, ax, 1.70, az, 0.50, -1.10, segments=6)

    for i in range(7):
        ang = 2 * math.pi * i / 7
        dbx = ax + 1.25 * math.cos(ang)
        dbz = az + 1.25 * math.sin(ang)
        b.add_sphere(m_dragonball, dbx, 1.55, dbz, 0.16, lat_segs=6, lon_segs=8)
        b.add_box(m_star_red,      dbx, 1.62, dbz + 0.12, 0.05, 0.05, 0.02)

    for ox, oz in [(-2.0, -2.0), (2.0, -2.0), (-2.0, 2.0), (2.0, 2.0)]:
        b.add_box(m_stone_dark, ax + ox, 0.95, az + oz, 0.65, 1.60, 0.65)
        b.add_box(m_stone,      ax + ox, 1.90, az + oz, 0.50, 0.50, 0.50)
        b.add_cone(m_cyan_rune, ax + ox, 2.55, az + oz, 0.40, 0.90, segments=4)

    # Torii Gate / Shrine Arch
    b.add_box(m_brick_red, ax - 1.4, 1.35, az - 2.4, 0.28, 2.40, 0.28)
    b.add_box(m_brick_red, ax + 1.4, 1.35, az - 2.4, 0.28, 2.40, 0.28)
    b.add_box(m_gold,      ax,       2.60, az - 2.4, 3.50, 0.22, 0.35)
    b.add_box(m_timber,    ax,       2.35, az - 2.4, 3.10, 0.16, 0.25)

    # In-World 3D Signboard
    b.add_building_sign(m_stone_dark, m_gold, m_cyan_rune, ax, 3.05, az - 2.35, 2.5, 0.65, emblem_type="rune")

    # -------------------------------------------------------------
    # 8. [SOUTH-EAST] COURIER GUILD (AUTO-APPLY & DISPATCH DEPOT)
    # -------------------------------------------------------------
    cx, cz = 7.2, 7.2
    b.add_box(m_stone_dark, cx, 0.25, cz, 5.5, 0.45, 5.5)
    b.add_box(m_wood_plank, cx, 1.90, cz, 4.4, 2.80, 4.0)
    b.add_box(m_timber, cx, 1.90, cz + 2.02, 4.44, 0.18, 0.08)
    b.add_box(m_timber, cx, 2.90, cz + 2.02, 4.44, 0.18, 0.08)
    b.add_box(m_timber, cx - 1.3, 1.90, cz + 2.02, 0.18, 2.80, 0.08)
    b.add_box(m_timber, cx + 1.3, 1.90, cz + 2.02, 0.18, 2.80, 0.08)

    b.add_box(m_redroof, cx, 3.75, cz, 4.8, 1.00, 4.4)
    b.add_box(m_timber,  cx, 4.60, cz, 1.8, 1.00, 1.8)
    b.add_cone(m_gold,    cx, 5.55, cz, 1.3, 1.10, segments=6)

    b.add_box(m_stone,   cx, 0.55, cz + 2.8, 4.6, 0.20, 1.8)
    b.add_box(m_timber,  cx - 1.8, 1.55, cz + 3.4, 0.20, 2.1, 0.20)
    b.add_box(m_timber,  cx + 1.8, 1.55, cz + 3.4, 0.20, 2.1, 0.20)
    b.add_box(m_redroof, cx, 2.60, cz + 3.0, 4.8, 0.18, 1.6)

    # In-World 3D Signboard
    b.add_building_sign(m_timber, m_gold, m_parchment, cx, 3.05, cz + 3.4, 2.6, 0.65, emblem_type="courier")

    # Delivery crates and handcart
    b.add_box(m_timber,    cx - 1.0, 0.50, cz + 2.8, 0.65, 0.60, 0.65)
    b.add_box(m_wood_plank,cx - 1.0, 0.95, cz + 2.8, 0.50, 0.50, 0.50)
    b.add_box(m_parchment, cx + 1.1, 0.45, cz + 2.9, 0.55, 0.45, 0.55)
    b.add_box(m_gold,      cx + 1.1, 0.53, cz + 2.9, 0.58, 0.06, 0.58)

    # -------------------------------------------------------------
    # 9. 8 LOOKOUT TOWERS (EXACTLY 2 AT EACH OUTER EDGE SEGMENT)
    # -------------------------------------------------------------
    namek_tower_locs = [
        (-14.0, -4.5), (-14.0, 4.5),  # West outer edge segment (2)
        ( 14.0, -4.5), ( 14.0, 4.5),  # East outer edge segment (2)
        (-4.5, -14.0), ( 4.5, -14.0),  # North outer edge segment (2)
        (-4.5,  14.0), ( 4.5,  14.0),  # South outer edge segment (2)
    ]
    for ntx, ntz in namek_tower_locs:
        b.add_namek_tower(m_namek_stone, m_namek_plaster, m_namek_teal, m_namek_crystal, ntx, 0.05, ntz)

    # -------------------------------------------------------------
    # 10. 7 SACRED DRAGON BALLS DIRECTLY ON GROUND WITHIN THE LAND
    # -------------------------------------------------------------
    # Platform radius is 18.0; placed within the land at radius 13.8 directly on ground (not floating)
    for i in range(7):
        ang = 2 * math.pi * i / 7 + math.pi / 14
        dbx = 13.8 * math.cos(ang)
        dbz = 13.8 * math.sin(ang)
        b.add_dragon_ball(m_dragonball, m_star_red, m_gold, dbx, 0.0, dbz, stars=i + 1)

    # -------------------------------------------------------------
    # 11. STATIC 3D BLUE & WHITE CLOUDS IN SKY ARCHIPELAGO
    # -------------------------------------------------------------
    cloud_locs = [
        (-26.0,   4.5, -22.0, 2.4, False),
        ( 28.0,   6.0, -20.0, 2.6, True),
        (-28.0,   2.0,  18.0, 2.5, True),
        ( 25.0,   5.5,  22.0, 2.7, False),
        (  0.0,  -2.5, -30.0, 3.2, False),
        (  0.0,  -3.0,  30.0, 3.0, True),
        (-32.0,  -1.5,   0.0, 3.1, False),
        ( 32.0,  -1.0,   0.0, 3.3, True),
        (-22.0,   8.0,  -8.0, 2.0, False),
        ( 22.0,   9.5,   8.0, 2.2, False),
        (-18.0,  -5.0, -26.0, 2.8, True),
        ( 18.0,  -4.5,  26.0, 2.9, False),
        (-10.0,  11.0,  24.0, 1.8, False),
        ( 12.0,  12.0, -22.0, 1.9, True),
    ]
    for ccx, ccy, ccz, cscale, is_blue in cloud_locs:
        mat_c = m_cloud_blue if is_blue else m_cloud_white
        b.add_cloud(mat_c, ccx, ccy, ccz, scale=cscale)

    # =============================================================
    # NODE 1: Nimbus (Flying Nimbus with trailing golden wisps & Kid Gohan)
    # =============================================================
    b.set_node("Nimbus")
    # 1. Flying Nimbus Cloud Body (cushion top at y = +0.60)
    b.add_nimbus(m_nimbus_gold, m_nimbus_trail, 0.0, 0.0, 0.0, scale=1.0)

    # 2. Kid Gohan Riding The Flying Nimbus (Resting directly on top of cloud crest, zero clipping)
    # Sitting dark trousers & folded legs resting firmly on top of cloud surface (base at y=0.63, resting on y=0.60)
    b.add_box(m_stone_dark,  0.00, 0.72, 0.06, 0.38, 0.18, 0.34)
    # Little red shoes resting forward on top of cloud crest
    b.add_box(m_tile_crimson,-0.15, 0.68, 0.26, 0.13, 0.12, 0.18)
    b.add_box(m_tile_crimson, 0.15, 0.68, 0.26, 0.13, 0.12, 0.18)

    # Kid Gohan Torso in Green Tunic with Yellow Collar & Red Sash
    b.add_box(m_gohan_green,  0.00, 1.04, 0.06, 0.34, 0.36, 0.26)
    b.add_box(m_tile_crimson, 0.00, 0.86, 0.06, 0.36, 0.08, 0.28) # Red sash belt
    b.add_box(m_gohan_yellow, 0.00, 1.04, 0.20, 0.18, 0.22, 0.03) # Yellow chest badge
    b.add_box(m_tile_crimson, 0.00, 1.04, 0.21, 0.08, 0.08, 0.03) # Kanji center seal

    # Little Arms Holding / Resting on the Golden Nimbus
    b.add_box(m_gohan_yellow,-0.20, 1.00, 0.18, 0.10, 0.10, 0.22)
    b.add_box(m_skin,        -0.20, 0.94, 0.29, 0.09, 0.08, 0.09)
    b.add_box(m_gohan_yellow, 0.20, 1.00, 0.18, 0.10, 0.10, 0.22)
    b.add_box(m_skin,         0.20, 0.94, 0.29, 0.09, 0.08, 0.09)

    # Kid Gohan Head, Face & Black Bowl-Cut Hair
    b.add_box(m_skin,        0.00, 1.34,  0.06, 0.25, 0.24, 0.24)
    b.add_box(m_hair_black,  0.00, 1.32, -0.06, 0.27, 0.22, 0.06) # Back hair
    b.add_box(m_hair_black, -0.13, 1.33,  0.06, 0.04, 0.18, 0.22) # Left fringe
    b.add_box(m_hair_black,  0.13, 1.33,  0.06, 0.04, 0.18, 0.22) # Right fringe
    b.add_box(m_hair_black,  0.00, 1.44,  0.17, 0.22, 0.08, 0.04) # Front forehead bangs

    # Iconic Red Scholar Cap
    b.add_cylinder(m_tile_crimson, 0.00, 1.50, 0.06, 0.20, 0.12, segments=10)
    b.add_sphere(m_tile_crimson,   0.00, 1.56, 0.06, 0.18, lat_segs=6, lon_segs=8)

    # 4-Star Sacred Dragon Ball Crown on Gohan's Cap
    b.add_cylinder(m_gold,       0.00, 1.68, 0.06, 0.07, 0.04, segments=8)
    b.add_sphere(m_dragonball,   0.00, 1.80, 0.06, 0.14, lat_segs=7, lon_segs=10)
    b.add_box(m_star_red,        0.00, 1.80, 0.19, 0.05, 0.05, 0.02)

    # =============================================================
    # NODE 2: SaiyanGoku (Super Saiyan 3 Goku in Turtle School Gi)
    # =============================================================
    b.set_node("SaiyanGoku")
    # Torso (Orange Gi + Dark Blue Undershirt)
    b.add_box(m_turtle_gi, 0.0, 0.0, 0.0, 0.42, 0.60, 0.28)
    b.add_box(m_tunic_blu, 0.0, 0.12, 0.02, 0.24, 0.22, 0.26) # Blue chest neckline
    b.add_box(m_tunic_blu, 0.0, -0.22, 0.0, 0.44, 0.14, 0.30) # Blue obi belt
    # Head & Skin
    b.add_box(m_skin, 0.0, 0.44, 0.0, 0.26, 0.28, 0.24)
    # SSJ3 Heavy Brow Ridge (Signature SSJ3 Pronounced Brow)
    b.add_box(m_skin, 0.0, 0.52, 0.12, 0.27, 0.08, 0.06)

    # Super Saiyan 3 (SSJ3) Hair:
    # 1. Front Forehead Fringe & Sharp Drooping SSJ3 Bangs
    b.add_cone(m_saiyan_ssj,  0.02, 0.44, 0.15, 0.06, 0.32, segments=4) # Center long drooping bang
    b.add_cone(m_saiyan_ssj, -0.11, 0.44, 0.13, 0.05, 0.30, segments=4) # Left forehead bang
    b.add_cone(m_saiyan_ssj,  0.12, 0.44, 0.13, 0.05, 0.30, segments=4) # Right forehead bang

    # 2. Massive Upright Crown Spikes (Base y >= 0.60, clean crown clearing skull)
    b.add_cone(m_saiyan_ssj,  0.00, 0.88, -0.05, 0.30, 0.55, segments=6) # Central crown spire
    b.add_cone(m_saiyan_ssj, -0.20, 0.80, -0.04, 0.20, 0.50, segments=5) # Left crown flair
    b.add_cone(m_saiyan_ssj,  0.20, 0.80, -0.04, 0.20, 0.50, segments=5) # Right crown flair
    b.add_cone(m_saiyan_ssj, -0.10, 0.85,  0.04, 0.16, 0.45, segments=4)
    b.add_cone(m_saiyan_ssj,  0.10, 0.85,  0.04, 0.16, 0.45, segments=4)

    # 3. Flanking Side Locks Draping Outside Shoulders (|x| >= 0.24, no chest/torso penetration)
    b.add_cone(m_saiyan_ssj, -0.26, 0.30, -0.06, 0.10, 0.48, segments=4)
    b.add_cone(m_saiyan_ssj,  0.26, 0.30, -0.06, 0.10, 0.48, segments=4)
    b.add_cone(m_saiyan_ssj, -0.24, 0.05, -0.10, 0.09, 0.42, segments=4)
    b.add_cone(m_saiyan_ssj,  0.24, 0.05, -0.10, 0.09, 0.42, segments=4)

    # 4. Long Super Saiyan 3 Cascading Back Hair (Mathematically cz + r <= -0.16, strictly behind z=-0.14 torso back)
    b.add_cone(m_saiyan_ssj,  0.00,  0.48, -0.36, 0.20, 0.62, segments=5) # Upper neck/back
    b.add_cone(m_saiyan_ssj, -0.16,  0.44, -0.34, 0.18, 0.58, segments=5)
    b.add_cone(m_saiyan_ssj,  0.16,  0.44, -0.34, 0.18, 0.58, segments=5)

    b.add_cone(m_saiyan_ssj,  0.00,  0.10, -0.39, 0.22, 0.72, segments=5) # Mid-back spine volume
    b.add_cone(m_saiyan_ssj, -0.15,  0.08, -0.37, 0.19, 0.68, segments=5)
    b.add_cone(m_saiyan_ssj,  0.15,  0.08, -0.37, 0.19, 0.68, segments=5)

    b.add_cone(m_saiyan_ssj,  0.00, -0.30, -0.38, 0.20, 0.70, segments=5) # Lower waist & hips
    b.add_cone(m_saiyan_ssj, -0.13, -0.28, -0.36, 0.17, 0.66, segments=4)
    b.add_cone(m_saiyan_ssj,  0.13, -0.28, -0.36, 0.17, 0.66, segments=4)

    b.add_cone(m_saiyan_ssj,  0.00, -0.62, -0.37, 0.16, 0.58, segments=4) # Lowest trailing SSJ3 tip past thighs
    b.add_cone(m_saiyan_ssj, -0.09, -0.58, -0.35, 0.13, 0.50, segments=4)
    b.add_cone(m_saiyan_ssj,  0.09, -0.58, -0.35, 0.13, 0.50, segments=4)

    # Combat Arms reaching forward (+Z)
    b.add_box(m_skin, -0.28, 0.05, 0.32, 0.14, 0.14, 0.55)
    b.add_box(m_skin,  0.28, 0.05, 0.32, 0.14, 0.14, 0.55)
    b.add_box(m_tunic_blu, -0.28, 0.05, 0.48, 0.16, 0.16, 0.18) # Blue wristbands
    b.add_box(m_tunic_blu,  0.28, 0.05, 0.48, 0.16, 0.16, 0.18)

    # Flying Combat Legs
    b.add_box(m_turtle_gi, -0.14, -0.55, -0.25, 0.16, 0.16, 0.65)
    b.add_box(m_turtle_gi,  0.14, -0.55, -0.25, 0.16, 0.16, 0.65)
    b.add_box(m_tunic_blu, -0.14, -0.55, -0.65, 0.18, 0.18, 0.22) # Blue boots
    b.add_box(m_tunic_blu,  0.14, -0.55, -0.65, 0.18, 0.18, 0.22)

    # Anime Shaded Charging Ki Aura (Layered inner bright gold & outer deep amber shades)
    b.add_cone(m_saiyan_aura_bright,  0.00, -0.55, -0.75, 0.22, 0.65, segments=6) # Inner propulsion cone behind boots
    b.add_cone(m_saiyan_aura_deep,    0.00, -0.55, -0.80, 0.32, 0.80, segments=6) # Outer deep charging shade flare
    b.add_cone(m_saiyan_aura_bright, -0.28,  0.18, -0.18, 0.11, 0.40, segments=4) # Left shoulder inner flare
    b.add_cone(m_saiyan_aura_deep,   -0.32,  0.18, -0.22, 0.16, 0.50, segments=4) # Left shoulder outer flare
    b.add_cone(m_saiyan_aura_bright,  0.28,  0.18, -0.18, 0.11, 0.40, segments=4) # Right shoulder inner flare
    b.add_cone(m_saiyan_aura_deep,    0.32,  0.18, -0.22, 0.16, 0.50, segments=4) # Right shoulder outer flare
    b.add_cone(m_saiyan_aura_bright,  0.00,  0.25, -0.22, 0.13, 0.45, segments=4) # Upper spine inner flare
    b.add_cone(m_saiyan_aura_deep,    0.00,  0.25, -0.26, 0.18, 0.55, segments=4) # Upper spine outer flare
    b.add_cone(m_saiyan_aura_deep,   -0.18, -0.20, -0.38, 0.14, 0.40, segments=4) # Left waist trail (behind)
    b.add_cone(m_saiyan_aura_deep,    0.18, -0.20, -0.38, 0.14, 0.40, segments=4) # Right waist trail (behind)

    # =============================================================
    # NODE 3: SaiyanVegeta (Prince of Saiyans in Normal Saiyan Battle Armor)
    # =============================================================
    b.set_node("SaiyanVegeta")
    # Royal blue jumpsuit with white & gold Saiyan battlesuit armor
    b.add_box(m_tunic_blu, 0.0, 0.0, 0.0, 0.40, 0.58, 0.26)
    # Saiyan Chestplate & Pauldrons
    b.add_box(m_lookout_white, 0.0, 0.06, 0.02, 0.44, 0.42, 0.30)
    b.add_box(m_gold,          0.0, 0.06, 0.16, 0.26, 0.32, 0.04) # Center gold ribbing
    b.add_box(m_gold,         -0.28, 0.24, 0.0, 0.22, 0.10, 0.32) # Shoulder pauldrons
    b.add_box(m_gold,          0.28, 0.24, 0.0, 0.22, 0.10, 0.32)
    # Head & Iconic Upright Flame-like Spiky Hair (Signature Widow's Peak, zero body clipping)
    b.add_box(m_skin, 0.0, 0.42, 0.0, 0.24, 0.26, 0.24)
    b.add_cone(m_saiyan_ssj,  0.00, 0.50,  0.13, 0.05, 0.16, segments=3) # Signature widow's peak bangs
    b.add_cone(m_saiyan_ssj,  0.00, 0.88, -0.02, 0.26, 0.68, segments=5) # Central flame spike
    b.add_cone(m_saiyan_ssj, -0.13, 0.78, -0.02, 0.16, 0.54, segments=4) # Left flame spike
    b.add_cone(m_saiyan_ssj,  0.13, 0.78, -0.02, 0.16, 0.54, segments=4) # Right flame spike
    b.add_cone(m_saiyan_ssj,  0.00, 0.76, -0.10, 0.18, 0.52, segments=4) # Back flame crown
    # Combat Stance Arms
    b.add_box(m_tunic_blu,    -0.28, 0.02, 0.22, 0.14, 0.14, 0.45)
    b.add_box(m_lookout_white,-0.28, 0.02, 0.42, 0.16, 0.16, 0.16) # White gloves
    b.add_box(m_tunic_blu,     0.28, 0.02, 0.22, 0.14, 0.14, 0.45)
    b.add_box(m_lookout_white, 0.28, 0.02, 0.42, 0.16, 0.16, 0.16)
    # Trailing Legs with White Saiyan Boots
    b.add_box(m_tunic_blu,    -0.12, -0.52, -0.22, 0.15, 0.15, 0.60)
    b.add_box(m_lookout_white,-0.12, -0.52, -0.60, 0.17, 0.17, 0.20) # White boots
    b.add_box(m_gold,         -0.12, -0.52, -0.70, 0.15, 0.15, 0.08) # Gold toe cap
    b.add_box(m_tunic_blu,     0.12, -0.52, -0.22, 0.15, 0.15, 0.60)
    b.add_box(m_lookout_white, 0.12, -0.52, -0.60, 0.17, 0.17, 0.20)
    b.add_box(m_gold,          0.12, -0.52, -0.70, 0.15, 0.15, 0.08)
    # Anime Shaded Charging Ki Aura (Layered Royal Purple & Deep Cyan shades)
    b.add_cone(m_vegeta_aura_purple,  0.00, -0.52, -0.75, 0.20, 0.60, segments=6) # Inner purple propulsion cone
    b.add_cone(m_vegeta_aura_cyan,    0.00, -0.52, -0.80, 0.30, 0.75, segments=6) # Outer cyan charging shade flare
    b.add_cone(m_vegeta_aura_purple, -0.30,  0.26, -0.18, 0.11, 0.40, segments=4) # Left pauldron inner flare
    b.add_cone(m_vegeta_aura_cyan,   -0.34,  0.26, -0.22, 0.16, 0.50, segments=4) # Left pauldron outer flare
    b.add_cone(m_vegeta_aura_purple,  0.30,  0.26, -0.18, 0.11, 0.40, segments=4) # Right pauldron inner flare
    b.add_cone(m_vegeta_aura_cyan,    0.34,  0.26, -0.22, 0.16, 0.50, segments=4) # Right pauldron outer flare
    b.add_cone(m_vegeta_aura_purple,  0.00,  0.32, -0.20, 0.13, 0.44, segments=4) # Back spine inner flare
    b.add_cone(m_vegeta_aura_cyan,    0.00,  0.32, -0.24, 0.18, 0.52, segments=4) # Back spine outer flare

    # =============================================================
    # NODE 4: Beerus (Lord Beerus God of Destruction)
    # =============================================================
    b.set_node("Beerus")
    # Slender purple feline deity
    b.add_box(m_beerus_purple, 0.0, 0.0, 0.0, 0.32, 0.55, 0.22)
    # Egyptian Nemes Shoulder Collar (Black, Gold & Cyan)
    b.add_cylinder(m_stone_dark, 0.0, 0.22, 0.0, 0.38, 0.08, segments=8)
    b.add_cylinder(m_gold,       0.0, 0.24, 0.0, 0.36, 0.04, segments=8)
    b.add_cylinder(m_cyan_rune,  0.0, 0.25, 0.0, 0.32, 0.03, segments=8)
    # Feline Head & Large Pointed Ears
    b.add_box(m_beerus_purple, 0.0, 0.42, 0.0, 0.22, 0.26, 0.22)
    b.add_cone(m_beerus_purple, -0.14, 0.74, 0.0, 0.09, 0.42, segments=4)
    b.add_cone(m_beerus_purple,  0.14, 0.74, 0.0, 0.09, 0.42, segments=4)
    # Long Feline Destruction Tail
    b.add_box(m_beerus_purple, 0.0, -0.22, -0.25, 0.08, 0.08, 0.35)
    b.add_box(m_beerus_purple, 0.0, -0.10, -0.45, 0.08, 0.28, 0.08)
    b.add_box(m_beerus_purple, 0.0,  0.15, -0.48, 0.08, 0.30, 0.08)
    # Beerus White Sash & Dark Blue Trousers
    b.add_box(m_beerus_sash, 0.0, -0.20, 0.0, 0.35, 0.12, 0.24)
    b.add_box(m_tunic_blu,  -0.10, -0.52, -0.20, 0.15, 0.15, 0.58)
    b.add_box(m_tunic_blu,   0.10, -0.52, -0.20, 0.15, 0.15, 0.58)
    b.add_box(m_gold,       -0.10, -0.52, -0.55, 0.16, 0.16, 0.15)
    b.add_box(m_gold,        0.10, -0.52, -0.55, 0.16, 0.16, 0.15)
    # Arms with gold armlets reaching forward (+Z)
    b.add_box(m_beerus_purple, -0.24, 0.02, 0.22, 0.11, 0.11, 0.45)
    b.add_box(m_gold,          -0.24, 0.02, 0.28, 0.13, 0.13, 0.08)
    b.add_box(m_beerus_purple,  0.24, 0.02, 0.22, 0.11, 0.11, 0.45)
    b.add_box(m_gold,           0.24, 0.02, 0.28, 0.13, 0.13, 0.08)
    # Clean Hakai Ki Aura (Gentle trailing ribbons behind feet/tail, zero balloon mesh)
    b.add_cone(m_hakai_aura, 0.00, -0.52, -0.68, 0.20, 0.50, segments=5) # Gentle trail behind feet
    b.add_cone(m_hakai_aura, 0.00,  0.05, -0.45, 0.14, 0.35, segments=4) # Tail energy stream

    # =============================================================
    # NODE 5: KamehamehaBeam (Cyan Ki Beam along +Z)
    # =============================================================
    b.set_node("KamehamehaBeam")
    b.add_beam_z(m_kamehameha_core, m_kamehameha, length=12.0, r_core=0.20, r_aura=0.44, segments=12)

    # =============================================================
    # NODE 6: HakaiBeam (Vegeta's Purple Galick Gun Ki Beam along +Z)
    # =============================================================
    b.set_node("HakaiBeam")
    b.add_beam_z(m_galick_core, m_galick_aura, length=12.0, r_core=0.22, r_aura=0.48, segments=12)

    # =============================================================
    # NODE 6b: FinalFlashBeam (Vegeta's Golden Final Flash Ki Beam)
    # =============================================================
    b.set_node("FinalFlashBeam")
    b.add_beam_z(m_finalflash_core, m_finalflash, length=12.0, r_core=0.24, r_aura=0.50, segments=12)

    # =============================================================
    # NODE 7: KiClashImpact (Explosive Shockwaves & Collision Spikes)
    # =============================================================
    b.set_node("KiClashImpact")
    b.add_ki_clash_burst(m_clash_burst, m_kamehameha, m_clash_ring, radius=1.2)

    # =============================================================
    # NODE 8: SpiritBomb (Goku's Celestial Genki Dama with Orbiting Energy Rings)
    # =============================================================
    b.set_node("SpiritBomb")
    b.add_spirit_bomb(m_spiritbomb_core, m_spiritbomb_aura, m_spiritbomb_spark, radius=1.8)

    # Build and write GLB binary
    glb_bytes = b.build_glb()
    out_path = "app/src/main/assets/models/osrs_guild.glb"
    with open(out_path, "wb") as f:
        f.write(glb_bytes)
    print(f"Successfully generated Popo's Lookout OSRS Town (Multi-Node): {out_path} ({len(glb_bytes)} bytes)")

if __name__ == "__main__":
    generate()
