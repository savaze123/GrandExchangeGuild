#!/usr/bin/env python3
import struct
import json
import math

class GLBBuilder:
    def __init__(self):
        self.materials = []
        self.primitives = [] # list of (material_idx, positions, normals, indices)
        self.nodes = []

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
        # 8 corners
        hx, hy, hz = sx / 2.0, sy / 2.0, sz / 2.0
        # 6 faces * 4 vertices = 24 vertices for proper flat normals
        faces = [
            # Normal, 4 local vertices
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
        self.primitives.append((mat_idx, positions, normals, indices))

    def add_cylinder(self, mat_idx, cx, cy, cz, r, h, segments=8):
        positions = []
        normals = []
        indices = []
        hy = h / 2.0
        # Side faces
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
            # Face normal
            nx = math.cos((a1 + a2)/2)
            nz = math.sin((a1 + a2)/2)
            positions.extend([(x1, cy - hy, z1), (x2, cy - hy, z2), apex])
            normals.extend([(nx, 0.5, nz), (nx, 0.5, nz), (0, 1, 0)])
            indices.extend([base_v, base_v + 1, base_v + 2])
            base_v += 3
        self.primitives.append((mat_idx, positions, normals, indices))

    def build_glb(self):
        # Merge primitives by material
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
            # Pack Indices (UNSIGNED_INT 4 bytes)
            ind_bytes = bytearray()
            for idx in i_list:
                ind_bytes.extend(struct.pack('<I', idx))
            # Pad to 4 bytes
            while len(ind_bytes) % 4 != 0:
                ind_bytes.extend(b'\x00')

            ind_bv_idx = len(buffer_views)
            ind_offset = len(bin_data)
            bin_data.extend(ind_bytes)
            buffer_views.append({
                "buffer": 0,
                "byteOffset": ind_offset,
                "byteLength": len(ind_bytes),
                "target": 34963 # ELEMENT_ARRAY_BUFFER
            })
            ind_acc_idx = len(accessors)
            accessors.append({
                "bufferView": ind_bv_idx,
                "byteOffset": 0,
                "componentType": 5125, # UNSIGNED_INT
                "count": len(i_list),
                "type": "SCALAR",
                "max": [max(i_list)],
                "min": [min(i_list)]
            })

            # Pack Positions (FLOAT 3 components = 12 bytes)
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
                "target": 34962 # ARRAY_BUFFER
            })
            pos_acc_idx = len(accessors)
            accessors.append({
                "bufferView": pos_bv_idx,
                "byteOffset": 0,
                "componentType": 5126, # FLOAT
                "count": len(p_list),
                "type": "VEC3",
                "max": max_pos,
                "min": min_pos
            })

            # Pack Normals (FLOAT 3 components = 12 bytes)
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
                "target": 34962 # ARRAY_BUFFER
            })
            norm_acc_idx = len(accessors)
            accessors.append({
                "bufferView": norm_bv_idx,
                "byteOffset": 0,
                "componentType": 5126, # FLOAT
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
            "asset": {"version": "2.0", "generator": "OSRS Grand Exchange Guild GLB Generator"},
            "scene": 0,
            "scenes": [{"nodes": [0]}],
            "nodes": [{"mesh": 0, "name": "OSRS_GrandExchangeHub"}],
            "meshes": [{"name": "TownHubMesh", "primitives": mesh_primitives}],
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
    builder = GLBBuilder()

    # OSRS Palettes
    m_grass   = builder.add_material("Grass",   (0.25, 0.40, 0.16, 1.0), roughness=0.9)
    m_stone   = builder.add_material("Stone",   (0.42, 0.39, 0.35, 1.0), roughness=0.8)
    m_cobble  = builder.add_material("Cobble",  (0.35, 0.30, 0.25, 1.0), roughness=0.85)
    m_timber  = builder.add_material("Timber",  (0.35, 0.22, 0.12, 1.0), roughness=0.7)
    m_wood    = builder.add_material("Wood",    (0.48, 0.34, 0.20, 1.0), roughness=0.7)
    m_roof    = builder.add_material("Roof",    (0.55, 0.18, 0.10, 1.0), roughness=0.6)
    m_gold    = builder.add_material("Gold",    (1.00, 0.84, 0.00, 1.0), metallic=0.7, roughness=0.2)
    m_rune    = builder.add_material("CyanRune",(0.00, 0.90, 1.00, 1.0), roughness=0.1)
    m_water   = builder.add_material("Water",   (0.15, 0.40, 0.65, 0.8), roughness=0.1)
    m_paper   = builder.add_material("Parchment",(0.85, 0.78, 0.62, 1.0), roughness=0.9)
    m_foliage = builder.add_material("Foliage", (0.18, 0.32, 0.12, 1.0), roughness=0.9)

    # 1. Base Terrain: Ground plaza
    # Outer grass field
    builder.add_box(m_grass, 0, -0.4, 0, 32, 0.4, 32)
    # Cobblestone courtyard
    builder.add_cylinder(m_cobble, 0, -0.15, 0, 13.0, 0.2, segments=16)
    # Inner decorative stone pathway
    builder.add_cylinder(m_stone, 0, -0.05, 0, 7.5, 0.1, segments=16)

    # 2. Central Grand Exchange Fountain / Pillar
    # Fountain base pool
    builder.add_cylinder(m_stone, 0, 0.25, 0, 3.2, 0.5, segments=12)
    # Water surface
    builder.add_cylinder(m_water, 0, 0.35, 0, 2.9, 0.2, segments=12)
    # Central stone pedestal
    builder.add_cylinder(m_stone, 0, 0.8, 0, 1.2, 1.2, segments=8)
    # Grand Exchange Gold Emblem Ring
    builder.add_cylinder(m_gold, 0, 1.8, 0, 0.8, 0.8, segments=8)
    builder.add_box(m_gold, 0, 2.4, 0, 0.4, 0.4, 0.4)

    # 3. NORTH-WEST: BOUNTY BOARD (Jobs / Quests)
    # Located around (-6, 0, -6)
    bx, bz = -6.5, -6.5
    # Stone foundation
    builder.add_box(m_stone, bx, 0.1, bz, 3.4, 0.2, 2.0)
    # Timber posts
    builder.add_box(m_timber, bx - 1.2, 1.3, bz, 0.25, 2.4, 0.25)
    builder.add_box(m_timber, bx + 1.2, 1.3, bz, 0.25, 2.4, 0.25)
    # Main notice board
    builder.add_box(m_wood, bx, 1.5, bz, 2.3, 1.4, 0.12)
    # Small pitched roof
    builder.add_box(m_roof, bx, 2.3, bz, 2.7, 0.2, 0.6)
    # Parchment Quest Scrolls / Job bounties
    builder.add_box(m_paper, bx - 0.6, 1.6, bz + 0.08, 0.5, 0.7, 0.04)
    builder.add_box(m_gold, bx - 0.6, 1.9, bz + 0.10, 0.1, 0.1, 0.04) # gold wax seal
    builder.add_box(m_paper, bx + 0.5, 1.5, bz + 0.08, 0.6, 0.8, 0.04)
    builder.add_box(m_gold, bx + 0.5, 1.85, bz + 0.10, 0.1, 0.1, 0.04)
    builder.add_box(m_paper, bx, 1.3, bz + 0.08, 0.5, 0.5, 0.04)

    # 4. NORTH-EAST: SCRIBE TOWER (Resume Forge & Cover Letter)
    # Located around (6.5, 0, -6.5)
    tx, tz = 6.5, -6.5
    # Base foundation
    builder.add_box(m_stone, tx, 0.2, tz, 4.0, 0.4, 4.0)
    # Lower stone tower body
    builder.add_box(m_stone, tx, 2.2, tz, 3.2, 3.6, 3.2)
    # Balcony trim
    builder.add_box(m_timber, tx, 4.1, tz, 3.6, 0.3, 3.6)
    # Upper tower
    builder.add_box(m_stone, tx, 5.2, tz, 2.8, 2.0, 2.8)
    # Scribe Tower pointed roof
    builder.add_cone(m_roof, tx, 7.3, tz, 2.2, 2.2, segments=8)
    # Golden Quill / Weather vane on top
    builder.add_box(m_gold, tx, 8.6, tz, 0.15, 0.6, 0.15)
    # Wooden door & parchment banner
    builder.add_box(m_timber, tx - 1.2, 0.9, tz + 1.65, 0.9, 1.6, 0.1)
    builder.add_box(m_paper, tx + 0.5, 2.5, tz + 1.65, 1.2, 1.8, 0.05)

    # 5. SOUTH-WEST: SKILL ALTAR (Dynamic Skill Matrix)
    # Located around (-6.5, 0, 6.5)
    ax, az = -6.5, 6.5
    # 3-tier stepped stone pyramid / altar
    builder.add_box(m_stone, ax, 0.15, az, 4.4, 0.3, 4.4)
    builder.add_box(m_stone, ax, 0.45, az, 3.6, 0.3, 3.6)
    builder.add_box(m_stone, ax, 0.75, az, 2.8, 0.3, 2.8)
    # Center Altar table
    builder.add_box(m_cobble, ax, 1.2, az, 1.6, 0.6, 1.6)
    # Altar glowing rune center slab
    builder.add_box(m_rune, ax, 1.55, az, 1.2, 0.1, 1.2)
    # 4 Corner Obelisk Pillars with glowing cyan runes
    for ox, oz in [(-1.5, -1.5), (1.5, -1.5), (-1.5, 1.5), (1.5, 1.5)]:
        builder.add_box(m_stone, ax + ox, 1.0, az + oz, 0.45, 1.8, 0.45)
        builder.add_cone(m_rune, ax + ox, 2.1, az + oz, 0.3, 0.5, segments=4)

    # 6. SOUTH-EAST: COURIER GUILD (Auto-Apply Queue & Ledger)
    # Located around (6.5, 0, 6.5)
    cx, cz = 6.5, 6.5
    # Stone footing
    builder.add_box(m_stone, cx, 0.15, cz, 4.2, 0.3, 4.2)
    # Timber wall body
    builder.add_box(m_wood, cx, 1.6, cz, 3.6, 2.6, 3.4)
    # Wooden cross-beams (half-timbered style)
    builder.add_box(m_timber, cx, 1.6, cz + 1.72, 3.65, 0.15, 0.05)
    builder.add_box(m_timber, cx, 2.6, cz + 1.72, 3.65, 0.15, 0.05)
    # Slanted roof
    builder.add_box(m_roof, cx, 3.3, cz, 4.0, 0.8, 3.8)
    # Courier porch awning & posts
    builder.add_box(m_timber, cx - 1.4, 1.1, cz + 2.2, 0.15, 2.0, 0.15)
    builder.add_box(m_timber, cx + 1.4, 1.1, cz + 2.2, 0.15, 2.0, 0.15)
    builder.add_box(m_roof, cx, 2.1, cz + 2.0, 3.4, 0.15, 1.0)
    # Courier parcel / crates on the porch
    builder.add_box(m_wood, cx - 0.7, 0.4, cz + 2.0, 0.5, 0.5, 0.5)
    builder.add_box(m_paper, cx + 0.6, 0.3, cz + 2.0, 0.4, 0.4, 0.4)

    # 7. Decorative low-poly OSRS Trees along courtyard borders
    tree_positions = [(-11, -2), (-11, 4), (11, -3), (11, 4), (-2, -11), (3, -11), (-2, 11), (3, 11)]
    for tx, tz in tree_positions:
        # Trunk
        builder.add_cylinder(m_timber, tx, 0.8, tz, 0.25, 1.6, segments=6)
        # Foliage cones
        builder.add_cone(m_foliage, tx, 2.0, tz, 1.4, 1.6, segments=7)
        builder.add_cone(m_foliage, tx, 2.9, tz, 1.0, 1.4, segments=6)

    glb_bytes = builder.build_glb()
    out_path = "app/src/main/assets/models/osrs_guild.glb"
    with open(out_path, "wb") as f:
        f.write(glb_bytes)
    print(f"Generated {out_path} ({len(glb_bytes)} bytes)")

if __name__ == "__main__":
    generate()
