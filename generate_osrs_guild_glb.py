#!/usr/bin/env python3
import struct
import json
import math

class GLBBuilder:
    def __init__(self):
        self.materials = []
        self.primitives = [] # list of (material_idx, positions, normals, indices)

    def add_material(self, name, base_color, metallic=0.1, roughness=0.8):
        idx = len(self.materials)
        self.materials.append({
            "name": name,
            "pbrMetallicRoughness": {
                "baseColorFactor": list(base_color),
                "metallicFactor": metallic,
                "roughnessFactor": roughness
            }
        })
        return idx

    def add_box(self, mat_idx, cx, cy, cz, sx, sy, sz):
        hx, hy, hz = sx / 2.0, sy / 2.0, sz / 2.0
        faces = [
            ((0, 1, 0), [(-hx, hy, -hz), (hx, hy, -hz), (hx, hy, hz), (-hx, hy, hz)]),  # Top
            ((0, -1, 0), [(-hx, -hy, hz), (hx, -hy, hz), (hx, -hy, -hz), (-hx, -hy, -hz)]), # Bottom
            ((0, 0, 1), [(-hx, -hy, hz), (hx, -hy, hz), (hx, hy, hz), (-hx, hy, hz)]),  # Front
            ((0, 0, -1), [(hx, -hy, -hz), (-hx, -hy, -hz), (-hx, hy, -hz), (hx, hy, -hz)]), # Back
            ((-1, 0, 0), [(-hx, -hy, -hz), (-hx, -hy, hz), (-hx, hy, hz), (-hx, hy, -hz)]), # Left
            ((1, 0, 0), [(hx, -hy, hz), (hx, -hy, -hz), (hx, hy, -hz), (hx, hy, hz)]),   # Right
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
        self.primitives.append((mat_idx, positions, normals, indices))

    def add_cylinder(self, mat_idx, cx, cy, cz, r, h, segments=8):
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

        # Top cap
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

        self.primitives.append((mat_idx, positions, normals, indices))

    def add_cone(self, mat_idx, cx, cy, cz, r, h, segments=8):
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
        self.primitives.append((mat_idx, positions, normals, indices))

    def add_character(self, cx, cy, cz, tunic_mat, cape_mat, skin_mat, rot_y=0.0):
        # Stylized low-poly OSRS adventurer / NPC:
        # Legs (dark boots), Torso (tunic), Head (skin/helmet), Cape (on back)
        cos_r = math.cos(rot_y)
        sin_r = math.sin(rot_y)
        def rot(lx, lz):
            return (cx + lx * cos_r - lz * sin_r, cz + lx * sin_r + lz * cos_r)

        # Feet / Boots (dark)
        px1, pz1 = rot(-0.12, 0)
        px2, pz2 = rot(0.12, 0)
        self.add_box(tunic_mat, px1, cy + 0.2, pz1, 0.16, 0.4, 0.22)
        self.add_box(tunic_mat, px2, cy + 0.2, pz2, 0.16, 0.4, 0.22)

        # Torso / Tunic
        tx, tz = rot(0, 0)
        self.add_box(tunic_mat, tx, cy + 0.7, tz, 0.45, 0.6, 0.28)

        # Head / Face
        hx, hz = rot(0, 0)
        self.add_box(skin_mat, hx, cy + 1.15, hz, 0.26, 0.28, 0.26)

        # Cape on back
        cx_c, cz_c = rot(0, -0.16)
        self.add_box(cape_mat, cx_c, cy + 0.65, cz_c, 0.38, 0.7, 0.06)

    def build_glb(self):
        mat_groups = {}
        for mat_idx, pos, norm, ind in self.primitives:
            if mat_idx not in mat_groups:
                mat_groups[mat_idx] = ([], [], [])
            p_list, n_list, i_list = mat_groups[mat_idx]
            offset = len(p_list)
            p_list.extend(pos)
            n_list.extend(norm)
            i_list.extend([i + offset for i in ind])

        bin_data = bytearray()
        buffer_views = []
        accessors = []
        mesh_primitives = []

        for mat_idx, (p_list, n_list, i_list) in mat_groups.items():
            if not i_list:
                continue
            # Pack Indices
            ind_bytes = bytearray()
            for idx in i_list:
                ind_bytes.extend(struct.pack('<I', idx))
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
                "componentType": 5125,
                "count": len(i_list),
                "type": "SCALAR",
                "max": [max(i_list)],
                "min": [min(i_list)]
            })

            # Pack Positions
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

            # Pack Normals
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

        gltf = {
            "asset": {"version": "2.0", "generator": "OSRS Grand Exchange Living Guild 3D"},
            "scene": 0,
            "scenes": [{"nodes": [0]}],
            "nodes": [{"mesh": 0, "name": "OSRS_GrandExchangeTown"}],
            "meshes": [{"name": "GrandExchangeMesh", "primitives": mesh_primitives}],
            "materials": self.materials,
            "accessors": accessors,
            "bufferViews": buffer_views,
            "buffers": [{"byteLength": len(bin_data)}]
        }

        json_bytes = json.dumps(gltf).encode('utf-8')
        while len(json_bytes) % 4 != 0:
            json_bytes += b' '

        while len(bin_data) % 4 != 0:
            bin_data += b'\x00'

        total_length = 12 + 8 + len(json_bytes) + 8 + len(bin_data)
        header = struct.pack('<4sII', b'glTF', 2, total_length)
        chunk0_header = struct.pack('<I4s', len(json_bytes), b'JSON')
        chunk1_header = struct.pack('<I4s', len(bin_data), b'BIN\x00')

        return header + chunk0_header + json_bytes + chunk1_header + bin_data


def generate():
    b = GLBBuilder()

    # OSRS & DBZ Fusion Aesthetic Materials
    m_grass       = b.add_material("Grass",          (0.20, 0.35, 0.12, 1.0), roughness=0.9)
    m_stone       = b.add_material("Stone",          (0.42, 0.40, 0.36, 1.0), roughness=0.8)
    m_darkstone   = b.add_material("DarkStone",      (0.28, 0.26, 0.23, 1.0), roughness=0.8)
    m_cobble      = b.add_material("Cobble",         (0.36, 0.32, 0.27, 1.0), roughness=0.85)
    m_timber      = b.add_material("Timber",         (0.32, 0.20, 0.10, 1.0), roughness=0.7)
    m_wood        = b.add_material("Wood",           (0.48, 0.33, 0.19, 1.0), roughness=0.7)
    m_redroof     = b.add_material("RedRoof",        (0.56, 0.17, 0.11, 1.0), roughness=0.6)
    m_blueroof    = b.add_material("BlueRoof",       (0.12, 0.25, 0.58, 1.0), roughness=0.5)
    m_gold        = b.add_material("Gold",           (1.00, 0.84, 0.00, 1.0), metallic=0.7, roughness=0.2)
    m_cyan_rune   = b.add_material("CyanRune",       (0.00, 0.92, 1.00, 1.0), metallic=0.2, roughness=0.1)
    m_water       = b.add_material("Water",          (0.12, 0.55, 0.78, 0.85), metallic=0.1, roughness=0.1)
    m_parchment   = b.add_material("Parchment",      (0.88, 0.80, 0.65, 1.0), roughness=0.9)
    m_foliage     = b.add_material("Foliage",        (0.17, 0.32, 0.11, 1.0), roughness=0.9)
    m_lantern     = b.add_material("LanternGlow",    (1.00, 0.95, 0.40, 1.0), roughness=0.1)
    m_crystal     = b.add_material("Crystal",        (0.70, 0.20, 0.90, 1.0), metallic=0.3, roughness=0.1)
    
    # DBZ Cel-Shaded Energy Materials
    m_saiyan_aura = b.add_material("SuperSaiyanKi",  (1.00, 0.92, 0.15, 1.0), metallic=0.4, roughness=0.1)
    m_kamehameha  = b.add_material("KamehamehaBlue", (0.00, 0.88, 1.00, 1.0), metallic=0.5, roughness=0.05)
    m_dragonball  = b.add_material("DragonBallAmber",(1.00, 0.48, 0.04, 1.0), metallic=0.3, roughness=0.15)
    m_star_red    = b.add_material("DragonStarRed",  (0.88, 0.08, 0.08, 1.0), roughness=0.3)
    m_namek_green = b.add_material("NamekianGreen",  (0.10, 0.86, 0.36, 1.0), roughness=0.3)
    m_turtle_gi   = b.add_material("TurtleGiOrange", (0.95, 0.38, 0.06, 1.0), roughness=0.7)
    m_saiyan_hair = b.add_material("SuperSaiyanHair",(1.00, 0.95, 0.30, 1.0), metallic=0.2, roughness=0.3)

    # Character NPC Materials
    m_skin        = b.add_material("Skin",           (0.85, 0.68, 0.52, 1.0), roughness=0.8)
    m_tunic_blu   = b.add_material("TunicBlue",      (0.12, 0.25, 0.65, 1.0), roughness=0.7)
    m_tunic_grn   = b.add_material("TunicGreen",     (0.18, 0.48, 0.22, 1.0), roughness=0.7)
    m_tunic_red   = b.add_material("TunicRed",       (0.65, 0.15, 0.15, 1.0), roughness=0.7)
    m_tunic_pur   = b.add_material("TunicPurple",    (0.45, 0.18, 0.58, 1.0), roughness=0.7)
    m_cape_red    = b.add_material("CapeRed",        (0.75, 0.12, 0.12, 1.0), roughness=0.6)
    m_cape_gold   = b.add_material("CapeGold",       (0.95, 0.78, 0.10, 1.0), roughness=0.4)
    m_cape_dark   = b.add_material("CapeDark",       (0.15, 0.15, 0.18, 1.0), roughness=0.7)

    # -------------------------------------------------------------
    # 1. BASE TERRAIN & GRAND EXCHANGE OCTAGONAL CENTRAL PLAZA
    # -------------------------------------------------------------
    # Outer grass courtyard
    b.add_box(m_grass, 0, -0.4, 0, 36, 0.4, 36)
    # Octagonal main plaza (approximated with 16-sided cylinder)
    b.add_cylinder(m_cobble, 0, -0.15, 0, 14.0, 0.2, segments=16)
    # Inner ornamental stone ring
    b.add_cylinder(m_stone, 0, -0.04, 0, 8.5, 0.1, segments=16)

    # Central Grand Exchange Ornate Cyan Fountain
    # Outer pool wall
    b.add_cylinder(m_darkstone, 0, 0.25, 0, 3.8, 0.5, segments=12)
    # Inner water pool
    b.add_cylinder(m_water, 0, 0.36, 0, 3.4, 0.22, segments=12)
    # Tier 2 stone basin
    b.add_cylinder(m_stone, 0, 0.75, 0, 2.0, 0.6, segments=10)
    b.add_cylinder(m_water, 0, 0.95, 0, 1.7, 0.18, segments=10)
    # Tier 3 central glowing pillar
    b.add_cylinder(m_stone, 0, 1.5, 0, 0.9, 1.2, segments=8)
    # Center floating cyan power crystal / water spout
    b.add_cone(m_cyan_rune, 0, 2.4, 0, 0.5, 0.8, segments=6)
    b.add_cone(m_cyan_rune, 0, 1.9, 0, 0.5, -0.8, segments=6)
    # Super Saiyan Ki Aura Energy Rings (DBZ Cel-Shaded)
    b.add_cylinder(m_saiyan_aura, 0, 2.1, 0, 2.6, 0.08, segments=16)
    b.add_cylinder(m_kamehameha,  0, 2.6, 0, 1.4, 0.06, segments=12)
    # 4 Gilded gargoyles / spouts on the fountain edge
    for fx, fz in [(-2.2, 0), (2.2, 0), (0, -2.2), (0, 2.2)]:
        b.add_box(m_gold, fx, 0.4, fz, 0.3, 0.3, 0.3)

    # -------------------------------------------------------------
    # 1B. 7 DRAGON BALLS ON STONE PEDESTALS (ORBITING PLAZA WALKWAY)
    # -------------------------------------------------------------
    db_radius = 6.2
    for db_i in range(7):
        db_angle = 2 * math.pi * db_i / 7.0
        dx = db_radius * math.cos(db_angle)
        dz = db_radius * math.sin(db_angle)
        # Hexagonal stone pedestal
        b.add_cylinder(m_darkstone, dx, 0.35, dz, 0.35, 0.7, segments=6)
        # Gold collar ring
        b.add_cylinder(m_gold, dx, 0.72, dz, 0.38, 0.08, segments=6)
        # Glowing Amber Dragon Ball sphere
        b.add_cylinder(m_dragonball, dx, 0.95, dz, 0.28, 0.34, segments=8)
        # Red Dragon Star glyph on the face
        b.add_box(m_star_red, dx, 0.95, dz + 0.12, 0.08, 0.08, 0.03)

    # -------------------------------------------------------------
    # 2. THE 4 DISTINCT GUILD BUILDINGS AT PLAZA CORNERS
    # -------------------------------------------------------------

    # === [A] BOUNTY BOARD (NORTH-WEST: -7.0, -7.0) ===
    # Medieval wooden notice board with scrolls, lantern post, wooden canopy
    bx, bz = -7.2, -7.2
    # Stone paved foundation
    b.add_box(m_stone, bx, 0.15, bz, 4.4, 0.3, 3.2)
    # Massive timber support posts
    b.add_box(m_timber, bx - 1.5, 1.6, bz, 0.32, 2.8, 0.32)
    b.add_box(m_timber, bx + 1.5, 1.6, bz, 0.32, 2.8, 0.32)
    b.add_box(m_timber, bx, 2.9, bz, 3.4, 0.25, 0.25) # top crossbeam
    # Central cork / oak board
    b.add_box(m_wood, bx, 1.7, bz, 2.8, 1.8, 0.15)
    # Shingled wooden roof canopy
    b.add_box(m_redroof, bx, 3.2, bz, 3.8, 0.25, 1.2)
    # Hanging Parchment Job Scrolls with wax seals
    # Scroll 1
    b.add_box(m_parchment, bx - 0.8, 1.8, bz + 0.10, 0.65, 0.9, 0.04)
    b.add_box(m_redroof,   bx - 0.8, 2.2, bz + 0.12, 0.12, 0.12, 0.04) # red wax seal
    # Scroll 2
    b.add_box(m_parchment, bx, 1.5, bz + 0.10, 0.7, 0.75, 0.04)
    b.add_box(m_gold,      bx, 1.8, bz + 0.12, 0.14, 0.14, 0.04) # gold wax seal
    # Scroll 3
    b.add_box(m_parchment, bx + 0.8, 1.9, bz + 0.10, 0.65, 0.95, 0.04)
    b.add_box(m_cyan_rune, bx + 0.8, 2.3, bz + 0.12, 0.12, 0.12, 0.04) # cyan quest seal
    # Lantern post beside board
    b.add_box(m_timber, bx + 2.0, 1.2, bz + 0.8, 0.15, 2.2, 0.15)
    b.add_box(m_gold,   bx + 2.0, 2.3, bz + 0.8, 0.25, 0.1, 0.25)
    b.add_box(m_lantern,bx + 2.0, 2.1, bz + 0.8, 0.2, 0.3, 0.2)

    # === [B] SCRIBE TOWER (NORTH-EAST: +7.2, -7.2) ===
    # Tall wizard/scholar stone tower with peaked blue roof, glowing arcane windows, floating books
    sx, sz = 7.2, -7.2
    # Fortified stone base
    b.add_box(m_darkstone, sx, 0.25, sz, 4.6, 0.5, 4.6)
    # Lower tower shaft (stone blocks)
    b.add_box(m_stone, sx, 2.4, sz, 3.6, 4.0, 3.6)
    # Mid-tower overhanging scholar's gallery
    b.add_box(m_timber, sx, 4.6, sz, 4.2, 0.4, 4.2)
    # Upper observatory
    b.add_box(m_stone, sx, 5.8, sz, 3.2, 2.2, 3.2)
    # Peaked Wizard Blue conical roof
    b.add_cone(m_blueroof, sx, 8.4, sz, 2.5, 3.2, segments=10)
    # Spire weather vane & golden star
    b.add_box(m_gold, sx, 10.2, sz, 0.12, 1.0, 0.12)
    b.add_cone(m_gold, sx, 10.8, sz, 0.35, 0.5, segments=5)
    # Glowing arcane windows (blue & gold)
    b.add_box(m_cyan_rune, sx, 2.4, sz + 1.83, 0.7, 1.2, 0.08)
    b.add_box(m_cyan_rune, sx, 5.8, sz + 1.63, 0.6, 0.9, 0.08)
    b.add_box(m_cyan_rune, sx + 1.83, 2.4, sz, 0.08, 1.2, 0.7)
    # Arch entryway
    b.add_box(m_timber, sx - 0.8, 0.9, sz + 1.83, 0.9, 1.6, 0.1)
    # Floating arcane book geometry at side
    b.add_box(m_gold, sx - 1.8, 1.8, sz + 1.4, 0.45, 0.08, 0.35) # Book cover
    b.add_box(m_parchment, sx - 1.8, 1.85, sz + 1.4, 0.4, 0.08, 0.32) # Pages

    # === [C] SKILL ALTAR (SOUTH-WEST: -7.2, +7.2) ===
    # Ancient Runecrafting stone ruin with glowing obelisks & floating crystal above altar table
    ax, az = -7.2, 7.2
    # 3-tier stepped ziggurat base
    b.add_box(m_darkstone, ax, 0.2, az, 5.2, 0.4, 5.2)
    b.add_box(m_stone,     ax, 0.55, az, 4.2, 0.35, 4.2)
    b.add_box(m_stone,     ax, 0.85, az, 3.2, 0.3, 3.2)
    # Altar Table
    b.add_box(m_darkstone, ax, 1.3, az, 1.8, 0.65, 1.8)
    # Mystical rune engraving on altar slab
    b.add_box(m_cyan_rune, ax, 1.66, az, 1.3, 0.08, 1.3)
    # Floating magical crystal cluster above altar
    b.add_cone(m_crystal, ax, 2.6, az, 0.45, 0.9, segments=6)
    b.add_cone(m_crystal, ax, 2.0, az, 0.45, -0.9, segments=6)
    # 4 Corner Mystical Obelisks with glowing energy tips
    for ox, oz in [(-1.8, -1.8), (1.8, -1.8), (-1.8, 1.8), (1.8, 1.8)]:
        b.add_box(m_stone,     ax + ox, 1.2, az + oz, 0.55, 2.0, 0.55)
        b.add_box(m_darkstone, ax + ox, 2.3, az + oz, 0.45, 0.4, 0.45)
        b.add_cone(m_cyan_rune,ax + ox, 2.9, az + oz, 0.35, 0.8, segments=4)

    # === [D] COURIER GUILD (SOUTH-EAST: +7.2, +7.2) ===
    # Dispatch depot / post office with crates, scrolls, banners, gilded pigeon coop roof
    cx, cz = 7.2, 7.2
    # Stone foundation
    b.add_box(m_stone, cx, 0.2, cz, 4.8, 0.4, 4.8)
    # Timbered guildhouse body
    b.add_box(m_wood, cx, 1.7, cz, 4.0, 2.8, 3.8)
    # Half-timbered cross beams
    b.add_box(m_timber, cx, 1.7, cz + 1.93, 4.05, 0.18, 0.06)
    b.add_box(m_timber, cx, 2.7, cz + 1.93, 4.05, 0.18, 0.06)
    b.add_box(m_timber, cx - 1.2, 1.7, cz + 1.93, 0.18, 2.8, 0.06)
    b.add_box(m_timber, cx + 1.2, 1.7, cz + 1.93, 0.18, 2.8, 0.06)
    # Gable roof (Varrock Red)
    b.add_box(m_redroof, cx, 3.5, cz, 4.4, 0.9, 4.2)
    # Gilded pigeon coop / attic dispatch tower on roof
    b.add_box(m_timber, cx, 4.3, cz, 1.6, 0.9, 1.6)
    b.add_cone(m_gold,   cx, 5.2, cz, 1.2, 1.0, segments=6)
    # Porch awning and support posts
    b.add_box(m_timber, cx - 1.6, 1.2, cz + 2.5, 0.18, 2.2, 0.18)
    b.add_box(m_timber, cx + 1.6, 1.2, cz + 2.5, 0.18, 2.2, 0.18)
    b.add_box(m_redroof,cx, 2.3, cz + 2.3, 3.8, 0.15, 1.2)
    # Stacked delivery crates & parcels on the porch
    b.add_box(m_timber,    cx - 0.8, 0.45, cz + 2.3, 0.6, 0.55, 0.6)
    b.add_box(m_wood,      cx - 0.8, 0.85, cz + 2.3, 0.45, 0.45, 0.45)
    b.add_box(m_parchment, cx + 0.7, 0.38, cz + 2.4, 0.5, 0.4, 0.5)
    b.add_box(m_gold,      cx + 0.7, 0.45, cz + 2.4, 0.52, 0.06, 0.52) # gold binding string

    # -------------------------------------------------------------
    # 3. LIVING TOWN NPCS / AVATARS (DBZ x RuneScape Stylized)
    # -------------------------------------------------------------
    # 1. Saiyan Scholar near Scribe Tower (Turtle Gi + Super Saiyan Cape)
    b.add_character(4.5, 0.0, -4.5, m_turtle_gi, m_saiyan_aura, m_skin, rot_y=math.pi * 0.75)
    # 2. Saiyan Warrior near Bounty Board (Royal Blue Battle Armor + Gold Trim)
    b.add_character(-4.5, 0.0, -4.5, m_tunic_blu, m_gold, m_skin, rot_y=math.pi * 0.25)
    # 3. Namekian Mystic near Skill Altar (Purple Gi + Namek Green Aura)
    b.add_character(-4.5, 0.0, 4.5, m_tunic_pur, m_namek_green, m_skin, rot_y=-math.pi * 0.25)
    # 4. Capsule Corp Courier Scout near Courier Guild (Emerald Tunic + Cape Red)
    b.add_character(4.5, 0.0, 4.5, m_tunic_grn, m_cape_red, m_skin, rot_y=-math.pi * 0.75)
    # 5. Super Saiyan Adventurer meditating at central fountain
    b.add_character(0.0, 0.0, -4.2, m_turtle_gi, m_saiyan_hair, m_skin, rot_y=0.0)
    # 6. Wandering Z-Fighter on southern walkway
    b.add_character(0.0, 0.0, 4.2, m_tunic_blu, m_kamehameha, m_skin, rot_y=math.pi)

    # -------------------------------------------------------------
    # 4. DECORATIVE OSRS TREES & ENVIRONMENT PROPS
    # -------------------------------------------------------------
    tree_locs = [
        (-12, -3), (-12, 3), (12, -3), (12, 3),
        (-3, -12), (3, -12), (-3, 12), (3, 12),
        (-11, -11), (11, -11), (-11, 11), (11, 11)
    ]
    for tx, tz in tree_locs:
        b.add_cylinder(m_timber, tx, 0.8, tz, 0.28, 1.6, segments=6)
        b.add_cone(m_foliage, tx, 2.1, tz, 1.5, 1.8, segments=7)
        b.add_cone(m_foliage, tx, 3.1, tz, 1.1, 1.4, segments=6)

    glb_bytes = b.build_glb()
    out_path = "app/src/main/assets/models/osrs_guild.glb"
    with open(out_path, "wb") as f:
        f.write(glb_bytes)
    print(f"Successfully generated {out_path} ({len(glb_bytes)} bytes)")

if __name__ == "__main__":
    generate()
