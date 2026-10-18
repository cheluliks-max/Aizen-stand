#!/usr/bin/env python3
"""
Generates the stand's custom design:
  - textures/entity/aizen_stand.png   (128x128 skin)
  - textures/item/stand_arrow.png     (16x16 item)
  - icon.png
  - client/AizenStandModel.java       (geometry that matches the skin 1:1)
Run:  python3 tools/gen_assets.py     (needs Pillow: pip install pillow)
The ready-made results are already in the project - you only need this to tweak the look.
"""
import math, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TEX_ENTITY = os.path.join(ROOT, "src/main/resources/assets/aizen_stand/textures/entity/aizen_stand.png")
TEX_ITEM = os.path.join(ROOT, "src/main/resources/assets/aizen_stand/textures/item/stand_arrow.png")
ICON = os.path.join(ROOT, "src/main/resources/assets/aizen_stand/icon.png")
JAVA = os.path.join(ROOT, "src/main/java/com/aizenstand/client/AizenStandModel.java")

SIZE = 128

# ---------------------------------------------------------------- palettes
PAL = {
    "pearl":  dict(base=(236, 232, 246), shade=(206, 198, 228), edge=(150, 118, 210), hi=(252, 250, 255)),
    "violet": dict(base=(92, 50, 158),   shade=(66, 34, 120),   edge=(196, 156, 255), hi=(226, 196, 255)),
    "dark":   dict(base=(40, 24, 72),    shade=(28, 16, 54),    edge=(112, 72, 196),  hi=(160, 120, 240)),
    "gold":   dict(base=(232, 190, 72),  shade=(190, 146, 40),  edge=(140, 98, 22),   hi=(255, 236, 150)),
    "glow":   dict(base=(214, 172, 255), shade=(190, 140, 250), edge=(150, 90, 232),  hi=(255, 252, 255)),
    "hair":   dict(base=(60, 38, 96),    shade=(44, 26, 74),    edge=(30, 18, 52),    hi=(124, 88, 180)),
    "wing":   dict(base=(190, 160, 246), shade=(150, 112, 220), edge=(96, 56, 178),   hi=(246, 238, 255)),
}


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------------------------------------------------------- face painters
def paint_face(style, part, face, w, h):
    """Returns a w x h RGBA image for one face of a cuboid."""
    p = PAL[style]
    img = Image.new("RGBA", (max(w, 1), max(h, 1)), p["base"] + (255,))
    px = img.load()

    def put(x, y, c):
        if 0 <= x < w and 0 <= y < h:
            px[x, y] = tuple(c) + (255,)

    shade = {"up": 1.0, "down": 0.0, "front": 0.7, "back": 0.45, "left": 0.55, "right": 0.55}[face]
    base = mix(p["shade"], p["base"], shade)
    for y in range(h):
        for x in range(w):
            put(x, y, base)

    if style in ("pearl", "violet", "gold", "dark") and w > 2 and h > 2:
        # outline
        for x in range(w):
            put(x, 0, p["edge"]); put(x, h - 1, p["edge"])
        for y in range(h):
            put(0, y, p["edge"]); put(w - 1, y, p["edge"])
        put(1, 1, p["hi"])

    if style == "glow":
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                d = math.hypot((x - cx) / max(w / 2, 1), (y - cy) / max(h / 2, 1))
                put(x, y, mix((255, 255, 255), p["edge"], min(1.0, d)))

    if style == "hair":
        for y in range(h):
            for x in range(w):
                if (x * 2 + y) % 5 == 0:
                    put(x, y, p["hi"])
                elif (x + y * 3) % 7 == 0:
                    put(x, y, p["edge"])

    if style == "wing":
        for x in range(w):
            for y in range(h):
                t = y / max(h - 1, 1)
                c = mix(p["hi"], p["base"], t * 0.9)
                if x % 3 == 0:
                    c = mix(c, p["edge"], 0.35)
                put(x, y, c)
        for x in range(w):
            put(x, 0, p["edge"]); put(x, h - 1, p["shade"])

    # ---- per-part details
    if part == "head" and face == "front":
        for y in range(h):
            for x in range(w):
                put(x, y, p["base"] if y != 7 else p["shade"])
        for x in range(8):                       # visor band
            put(x, 3, (28, 14, 58)); put(x, 4, (28, 14, 58))
        for x in (1, 2, 5, 6):                   # glowing slits
            put(x, 3, (255, 214, 255)); put(x, 4, (186, 110, 255))
        put(3, 3, (60, 30, 110)); put(4, 3, (60, 30, 110))
        for y in (5, 6):                         # mask line
            put(3, y, PAL["violet"]["base"]); put(4, y, PAL["violet"]["base"])
        for x in range(8):
            put(x, 0, PAL["gold"]["base"])       # circlet
    if part == "head" and face in ("left", "right"):
        for y in range(3, 6):
            for x in range(3, 6):
                put(x, y, PAL["violet"]["base"])
        put(4, 4, PAL["gold"]["hi"])
    if part in ("body", "chest") and face in ("front", "back"):
        for y in range(h):
            put(w // 2 - 1, y, PAL["violet"]["base"]); put(w // 2, y, PAL["violet"]["base"])
        for y in range(1, h, 3):
            put(w // 2 - 1, y, PAL["gold"]["base"]); put(w // 2, y, PAL["gold"]["base"])
    if part == "coat" and face in ("front", "back", "left", "right"):
        for y in range(h):
            put(0, y, PAL["violet"]["base"]); put(w - 1, y, PAL["violet"]["base"])
        for x in range(w):
            put(x, h - 1, PAL["violet"]["base"]); put(x, h - 2, PAL["gold"]["base"])
    if part == "pauldron":
        for x in range(w):
            put(x, 1 if h > 2 else 0, PAL["gold"]["base"])
    if part in ("bracer", "boot") and face not in ("up", "down"):
        for x in range(w):
            put(x, 1, PAL["gold"]["base"])
    return img


# ---------------------------------------------------------------- model description
# cube: (style, part_tag, (ox, oy, oz), (w, h, d), dilation)
# part: name, parent, pivot, rotation(rx, ry, rz), cubes
PARTS = [
    dict(name="head", parent=None, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=[
        ("pearl", "head", (-4, -8, -4), (8, 8, 8), 0.0),
        ("hair", "hair", (-4, -8, -4), (8, 2, 8), 0.45),            # swept-back hair cap
    ]),
    dict(name="hair_c", parent="head", pivot=(0, -7, 3.5), rot=(0.35, 0, 0), cubes=[
        ("hair", "hair", (-1.5, -1, 0), (3, 2, 8), 0.0)]),
    dict(name="hair_l", parent="head", pivot=(2.5, -7, 3.5), rot=(0.25, -0.35, 0), cubes=[
        ("hair", "hair", (-1.5, -1, 0), (3, 2, 7), 0.0)]),
    dict(name="hair_r", parent="head", pivot=(-2.5, -7, 3.5), rot=(0.25, 0.35, 0), cubes=[
        ("hair", "hair", (-1.5, -1, 0), (3, 2, 7), 0.0)]),
    dict(name="body", parent=None, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=[
        ("pearl", "body", (-4, 0, -2), (8, 12, 4), 0.0),
        ("pearl", "chest", (-4, 0, -2), (8, 7, 4), 0.55),            # breastplate
        ("glow", "orb", (-1.5, 2.2, -3.1), (3, 3, 1), 0.0),          # Hogyoku
    ]),
    dict(name="coat", parent=None, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=[
        ("pearl", "coat", (-5, 11, -3), (10, 10, 6), 0.0)]),         # captain's coat
    dict(name="right_arm", parent=None, pivot=(-5, 2, 0), rot=(0, 0, 0), cubes=[
        ("pearl", "arm", (-3, -2, -2), (4, 12, 4), 0.0),
        ("gold", "pauldron", (-3, -2, -2), (4, 3, 4), 0.9),
        ("dark", "bracer", (-3, 6, -2), (4, 5, 4), 0.3)]),
    dict(name="left_arm", parent=None, pivot=(5, 2, 0), rot=(0, 0, 0), cubes=[
        ("pearl", "arm", (-1, -2, -2), (4, 12, 4), 0.0),
        ("gold", "pauldron", (-1, -2, -2), (4, 3, 4), 0.9),
        ("dark", "bracer", (-1, 6, -2), (4, 5, 4), 0.3)]),
    dict(name="right_leg", parent=None, pivot=(-2, 12, 0), rot=(0, 0, 0), cubes=[
        ("pearl", "leg", (-2, 0, -2), (4, 12, 4), 0.0),
        ("dark", "boot", (-2, 7, -2), (4, 5, 4), 0.3)]),
    dict(name="left_leg", parent=None, pivot=(2, 12, 0), rot=(0, 0, 0), cubes=[
        ("pearl", "leg", (-2, 0, -2), (4, 12, 4), 0.0),
        ("dark", "boot", (-2, 7, -2), (4, 5, 4), 0.3)]),
    # butterfly wings (Kyoka Suigetsu / hollowfication motif)
    dict(name="wing_r_big", parent=None, pivot=(-2, 3, 2.5), rot=(0, 0.75, 0.30), cubes=[
        ("wing", "wing", (-15, -11, 0), (15, 23, 1), 0.0)]),
    dict(name="wing_r_small", parent=None, pivot=(-2, 8, 2.8), rot=(0, 0.55, -0.10), cubes=[
        ("wing", "wing", (-10, -2, 0), (10, 15, 1), 0.0)]),
    dict(name="wing_l_big", parent=None, pivot=(2, 3, 2.5), rot=(0, -0.75, -0.30), cubes=[
        ("wing", "wing", (0, -11, 0), (15, 23, 1), 0.0)]),
    dict(name="wing_l_small", parent=None, pivot=(2, 8, 2.8), rot=(0, -0.55, 0.10), cubes=[
        ("wing", "wing", (0, -2, 0), (10, 15, 1), 0.0)]),
    # slowly rotating halo behind the head
    dict(name="halo", parent=None, pivot=(0, -4, 9), rot=(0, 0, 0), cubes=[
        ("violet", "halo", (-9, -9, -1), (18, 2, 2), 0.0),
        ("violet", "halo", (-9, 7, -1), (18, 2, 2), 0.0),
        ("violet", "halo", (-9, -7, -1), (2, 14, 2), 0.0),
        ("violet", "halo", (7, -7, -1), (2, 14, 2), 0.0)]),
]


# ---------------------------------------------------------------- texture packing
def cube_footprint(size):
    w, h, d = size
    return 2 * (w + d), d + h


def pack():
    items = []
    for part in PARTS:
        for i, c in enumerate(part["cubes"]):
            items.append((part["name"], i, cube_footprint(c[3])))
    order = sorted(items, key=lambda t: -t[2][1])
    pos, x, y, row_h = {}, 0, 0, 0
    for name, i, (fw, fh) in order:
        if x + fw > SIZE:
            x, y, row_h = 0, y + row_h, 0
        pos[(name, i)] = (x, y)
        x += fw
        row_h = max(row_h, fh)
    assert y + row_h <= SIZE, "texture is full - increase SIZE"
    return pos


def build_texture(pos):
    tex = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for part in PARTS:
        for i, (style, tag, origin, size, dil) in enumerate(part["cubes"]):
            u, v = pos[(part["name"], i)]
            w, h, d = size
            faces = {
                "up": (u + d, v, w, d),
                "down": (u + d + w, v, w, d),
                "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h),
                "left": (u + d + w, v + d, d, h),
                "back": (u + 2 * d + w, v + d, w, h),
            }
            for face, (fx, fy, fw, fh) in faces.items():
                tex.paste(paint_face(style, tag, face, fw, fh), (fx, fy))
    return tex


# ---------------------------------------------------------------- Java generation
def f(n):
    return ("%s" % round(n, 3)).rstrip("0").rstrip(".") + "F" if n != int(n) else "%dF" % int(n)


def java_parts(pos):
    lines = []
    for part in PARTS:
        b = "ModelPartBuilder.create()"
        for i, (style, tag, o, s, dil) in enumerate(part["cubes"]):
            u, v = pos[(part["name"], i)]
            dil_s = ", new Dilation(%s)" % f(dil) if dil else ""
            b += "\n                .uv(%d, %d).cuboid(%s, %s, %s, %s, %s, %s%s)" % (
                u, v, f(o[0]), f(o[1]), f(o[2]), f(s[0]), f(s[1]), f(s[2]), dil_s)
        px, py, pz = part["pivot"]
        rx, ry, rz = part["rot"]
        tr = "ModelTransform.of(%s, %s, %s, %s, %s, %s)" % (f(px), f(py), f(pz), f(rx), f(ry), f(rz))
        parent = "root" if part["parent"] is None else part["parent"]
        var = part["name"]
        if part["parent"] is None or True:
            lines.append("        ModelPartData %s = %s.addChild(\"%s\", %s,\n                %s);" % (var, parent, var, b, tr))
    return "\n".join(lines)


JAVA_TEMPLATE = '''package com.aizenstand.client;

import com.aizenstand.AizenStandMod;
import com.aizenstand.AizenStandEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * GENERATED by tools/gen_assets.py - the geometry matches aizen_stand.png exactly.
 * The animation part (setAngles) is hand-written and safe to edit.
 */
public class AizenStandModel extends EntityModel<AizenStandEntity> {
    public static final EntityModelLayer LAYER =
            new EntityModelLayer(Identifier.of(AizenStandMod.MOD_ID, "aizen_stand"), "main");

    private final ModelPart root;
    private final ModelPart head, rightArm, leftArm, rightLeg, leftLeg;
    private final ModelPart wingRBig, wingRSmall, wingLBig, wingLSmall, halo;

    public AizenStandModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.wingRBig = root.getChild("wing_r_big");
        this.wingRSmall = root.getChild("wing_r_small");
        this.wingLBig = root.getChild("wing_l_big");
        this.wingLSmall = root.getChild("wing_l_small");
        this.halo = root.getChild("halo");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
@@PARTS@@
        return TexturedModelData.of(data, @@SIZE@@, @@SIZE@@);
    }

    @Override
    public void setAngles(AizenStandEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        float t = animationProgress;
        float cast = entity.getCastAnim();          // 0 = idle pose, 1 = casting pose

        // gentle levitation
        root.pivotY = (float) Math.sin(t * 0.11F) * 1.2F;

        // arms: idle sway -> right arm rises forward while casting a hado
        rightArm.pitch = lerp(cast, -0.15F + (float) Math.sin(t * 0.09F) * 0.06F, -1.55F);
        rightArm.roll = lerp(cast, 0.10F, 0.05F);
        leftArm.pitch = lerp(cast, -0.10F - (float) Math.sin(t * 0.09F) * 0.06F, -0.35F);
        leftArm.roll = -0.10F;

        rightLeg.pitch = (float) Math.sin(t * 0.07F) * 0.05F;
        leftLeg.pitch = -(float) Math.sin(t * 0.07F) * 0.05F;

        head.yaw = headYaw * 0.0175F * 0.4F;
        head.pitch = headPitch * 0.0175F * 0.4F;

        // wings breathe
        float flap = (float) Math.sin(t * 0.12F) * 0.07F + cast * 0.25F;
        wingRBig.yaw = 0.75F + flap;
        wingRSmall.yaw = 0.55F + flap * 0.8F;
        wingLBig.yaw = -0.75F - flap;
        wingLSmall.yaw = -0.55F - flap * 0.8F;

        // halo spins
        halo.roll = t * 0.03F;
    }

    private static float lerp(float k, float a, float b) {
        return a + (b - a) * k;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
'''


# ---------------------------------------------------------------- item / icon
def build_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    shaft, shaft_d = (112, 78, 40), (78, 52, 26)
    gold, gold_d, gold_h = (240, 196, 70), (170, 120, 30), (255, 240, 160)
    vio, vio_h = (120, 70, 200), (200, 160, 255)
    # diagonal arrow, tip in the top-right corner
    for i in range(3, 13):
        px[i, 15 - i] = shaft
        if i + 1 < 16:
            px[i + 1, 15 - i] = shaft_d
    tip = [(13, 2), (12, 2), (13, 3), (14, 1), (14, 2), (13, 1), (15, 0), (14, 0), (15, 1), (12, 1), (13, 0), (14, 3), (11, 2), (12, 3)]
    for (x, y) in tip:
        px[x, y] = gold
    for (x, y) in [(15, 0), (14, 1), (13, 2)]:
        px[x, y] = gold_h
    for (x, y) in [(11, 2), (12, 3), (14, 3), (13, 3)]:
        px[x, y] = gold_d
    px[13, 1] = vio_h
    # fletching
    for (x, y) in [(1, 12), (2, 13), (2, 11), (3, 14), (1, 14), (3, 11), (4, 12)]:
        px[x, y] = vio
    for (x, y) in [(2, 12), (3, 13)]:
        px[x, y] = vio_h
    # engraved band
    for (x, y) in [(8, 7), (9, 6)]:
        px[x, y] = gold
    px[7, 8] = gold_d
    return img


def main():
    pos = pack()
    tex = build_texture(pos)
    os.makedirs(os.path.dirname(TEX_ENTITY), exist_ok=True)
    tex.save(TEX_ENTITY)
    item = build_item()
    os.makedirs(os.path.dirname(TEX_ITEM), exist_ok=True)
    item.save(TEX_ITEM)
    item.resize((128, 128), Image.NEAREST).save(ICON)
    java = JAVA_TEMPLATE.replace("@@PARTS@@", java_parts(pos)).replace("@@SIZE@@", str(SIZE))
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write(java)
    print("ok")


if __name__ == "__main__":
    main()
