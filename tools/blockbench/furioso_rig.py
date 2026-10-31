"""Writes tools/blockbench/furioso.bbmodel: a player-shaped rig with one animation per Furioso weapon.

Open the .bbmodel in Blockbench (Animate tab) to preview or edit the moves, save it, then run
tools/bbmodel_to_furioso.py to bake it into FuriosoAnims.java. This script is only the first draft: once you have
edited the animations in Blockbench, the .bbmodel is the source of truth, so don't re-run this over your edits.

Times are Furioso script ticks / 20 (so 1 s on the Blockbench timeline = 20 script ticks), counted from the start of
each weapon's beat. Poses are written here in Java model space (degrees: x < 0 raises an arm forward, y > 0 swings
it towards the entity's right) and stored in Blockbench's convention (x and y negated), the same mapping the
converter undoes.
"""
import json
import os
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "furioso.bbmodel")

BONES = ["body", "head", "right_arm", "left_arm", "right_leg", "left_leg"]
# Blockbench origins (pivot) and cubes for the player shape (x mirrored vs. Java, y = 24 - javaY)
RIG = {
    "body": ([0, 24, 0], [-4, 12, -2], [4, 24, 2], [16, 16]),
    "head": ([0, 24, 0], [-4, 24, -4], [4, 32, 4], [0, 0]),
    "right_arm": ([5, 22, 0], [4, 12, -2], [8, 24, 2], [40, 16]),
    "left_arm": ([-5, 22, 0], [-8, 12, -2], [-4, 24, 2], [32, 48]),
    "right_leg": ([1.9, 12, 0], [-0.1, 0, -2], [3.9, 12, 2], [0, 16]),
    "left_leg": ([-1.9, 12, 0], [-3.9, 0, -2], [0.1, 12, 2], [16, 48]),
}

# weapon -> (length in script ticks, {bone: [(tick, (x, y, z)), ...]})   Java degrees
A = {}

def anim(name, length, **bones):
    A[name] = (length, bones)

anim("pistols", 12,
     right_arm=[(0, (0, 0, 0)), (2, (-90, -6, 0)), (4, (-112, -6, 0)), (5, (-90, -6, 0)), (10, (-90, -6, 0)), (12, (-45, 0, 0))],
     left_arm=[(0, (0, 0, 0)), (2, (-90, 6, 0)), (5, (-90, 6, 0)), (7, (-112, 6, 0)), (8, (-90, 6, 0)), (12, (-45, 0, 0))],
     body=[(0, (0, 0, 0)), (4, (0, -6, 0)), (7, (0, 6, 0)), (12, (0, 0, 0))])
anim("lance", 14,
     right_arm=[(0, (-20, 0, 0)), (3, (-15, 0, 10)), (5, (-92, 0, 0)), (6.5, (-40, 0, 0)), (8, (-96, 0, 0)), (11, (-90, 0, 0)), (14, (-40, 0, 0))],
     left_arm=[(0, (-50, 20, 0)), (5, (-82, -10, 0)), (6.5, (-55, 15, 0)), (8, (-86, -10, 0)), (14, (-30, 0, 0))],
     body=[(0, (0, 30, 0)), (3, (0, 35, 0)), (5, (0, -10, 0)), (6.5, (0, 20, 0)), (8, (8, -15, 0)), (14, (0, 0, 0))],
     right_leg=[(0, (0, 0, 0)), (8, (-30, 0, 0)), (12, (-20, 0, 0)), (14, (0, 0, 0))],
     left_leg=[(0, (0, 0, 0)), (8, (20, 0, 0)), (12, (12, 0, 0)), (14, (0, 0, 0))])
anim("hammer", 12,
     right_arm=[(0, (-60, -15, 0)), (3, (-175, 0, 0)), (6, (-170, 0, 0)), (7, (-30, 0, 0)), (9, (-30, 0, 0)), (12, (-40, 0, 0))],
     left_arm=[(0, (-60, 15, 0)), (3, (-170, 0, 0)), (6, (-165, 0, 0)), (7, (-35, 0, 0)), (9, (-35, 0, 0)), (12, (-30, 0, 0))],
     body=[(0, (0, 0, 0)), (3, (-10, 0, 0)), (6, (-12, 0, 0)), (7, (25, 0, 0)), (9, (25, 0, 0)), (12, (0, 0, 0))],
     right_leg=[(0, (0, 0, 0)), (7, (-25, 0, 0)), (12, (0, 0, 0))],
     left_leg=[(0, (0, 0, 0)), (7, (15, 0, 0)), (12, (0, 0, 0))])
anim("katana", 12,
     right_arm=[(0, (-30, -40, 0)), (4.5, (-30, -45, 0)), (6, (-90, 70, 0)), (7, (-80, 95, 0)), (9, (-70, 90, 0)), (12, (-30, 40, 0))],
     left_arm=[(0, (-30, 20, 0)), (6, (-20, -10, 0)), (12, (-15, 0, 0))],
     body=[(0, (5, 40, 0)), (4.5, (10, 45, 0)), (6, (0, -30, 0)), (9, (0, -35, 0)), (12, (0, 0, 0))],
     right_leg=[(0, (0, 0, 0)), (4.5, (-20, 0, 0)), (6, (-40, 0, 0)), (9, (-30, 0, 0)), (12, (0, 0, 0))],
     left_leg=[(0, (0, 0, 0)), (4.5, (15, 0, 0)), (6, (30, 0, 0)), (9, (20, 0, 0)), (12, (0, 0, 0))])
anim("gauntlets", 10,
     right_arm=[(0, (-75, -15, 0)), (2, (-75, -15, 0)), (3, (-96, -5, 0)), (4, (-72, -15, 0)), (10, (-50, -10, 0))],
     left_arm=[(0, (-75, 15, 0)), (4.5, (-75, 15, 0)), (5.5, (-96, 5, 0)), (6.5, (-72, 15, 0)), (10, (-50, 10, 0))],
     body=[(0, (5, 0, 0)), (3, (5, -15, 0)), (5.5, (5, 15, 0)), (10, (0, 0, 0))])
anim("knife", 10,
     right_arm=[(0, (-60, -40, 0)), (3, (-65, -50, 0)), (4, (-85, 60, 0)), (6, (-70, 80, 0)), (10, (-40, 20, 0))],
     left_arm=[(0, (-40, 10, 0)), (4, (-30, -10, 0)), (10, (-20, 0, 0))],
     body=[(0, (0, 25, 0)), (3, (0, 30, 0)), (4, (0, -25, 0)), (6, (0, -28, 0)), (10, (0, 0, 0))])
anim("axe", 12,
     right_arm=[(0, (-60, 0, 0)), (3, (-165, 0, 20)), (5, (-40, 10, 0)), (7, (-40, 10, 0)), (12, (-40, 0, 0))],
     left_arm=[(0, (-30, 0, 0)), (3, (-40, 10, 0)), (5, (-20, 0, 0)), (12, (-20, 0, 0))],
     body=[(0, (0, 10, 0)), (3, (-5, 20, 0)), (5, (20, -10, 0)), (7, (20, -10, 0)), (12, (0, 0, 0))])
anim("mace", 14,
     right_arm=[(0, (-40, 0, 0)), (3, (15, 0, 0)), (5, (-170, 0, 0)), (8, (-160, 0, 0)), (14, (-60, 0, 0))],
     left_arm=[(0, (-30, 0, 0)), (3, (-50, 0, 0)), (5, (10, 0, 0)), (14, (-20, 0, 0))],
     body=[(0, (0, 0, 0)), (3, (18, 15, 0)), (5, (-15, -10, 0)), (8, (-10, -8, 0)), (14, (0, 0, 0))],
     right_leg=[(0, (0, 0, 0)), (3, (-25, 0, 0)), (5, (0, 0, 0))],
     left_leg=[(0, (0, 0, 0)), (3, (-25, 0, 0)), (5, (10, 0, 0)), (14, (0, 0, 0))])
anim("greatsword", 16,
     right_arm=[(0, (-60, -10, 0)), (4, (-180, 0, 0)), (7, (-185, 0, 0)), (8, (-20, 0, 0)), (10, (-20, 0, 0)), (16, (-45, 0, 0))],
     left_arm=[(0, (-60, 10, 0)), (4, (-180, 0, 0)), (7, (-185, 0, 0)), (8, (-25, 0, 0)), (10, (-25, 0, 0)), (16, (-45, 0, 0))],
     body=[(0, (0, 0, 0)), (4, (-20, 0, 0)), (7, (-22, 0, 0)), (8, (35, 0, 0)), (10, (35, 0, 0)), (16, (0, 0, 0))],
     right_leg=[(0, (0, 0, 0)), (4, (-40, 0, 0)), (8, (-50, 0, 0)), (12, (-30, 0, 0)), (16, (0, 0, 0))],
     left_leg=[(0, (0, 0, 0)), (4, (-60, 0, 0)), (8, (10, 0, 0)), (12, (10, 0, 0)), (16, (0, 0, 0))])
anim("shortswords", 18,
     right_arm=[(0, (-40, 0, 0)), (3, (-50, 0, 0)), (5, (-90, -60, 0)), (6, (-90, 60, 0)), (7.2, (-80, 50, 0)), (10, (-90, -30, 0)),
                (11, (-150, 0, 0)), (12, (-60, 40, 0)), (14, (-60, 40, 0)), (18, (-40, 0, 0))],
     left_arm=[(0, (-40, 0, 0)), (3, (-50, 0, 0)), (7, (-90, 60, 0)), (8, (-90, -60, 0)), (9, (-80, -50, 0)), (10, (-90, 30, 0)),
               (12, (-60, 0, 0)), (18, (-40, 0, 0))],
     body=[(0, (-20, 0, 0)), (3, (0, 0, 0)), (6, (0, -20, 0)), (8, (0, 20, 0)), (10, (0, 0, 0)), (12, (5, -25, 0)), (14, (5, -25, 0)), (18, (0, 0, 0))],
     right_leg=[(0, (20, 0, 0)), (3, (0, 0, 0)), (12, (-20, 0, 0)), (18, (0, 0, 0))],
     left_leg=[(0, (-10, 0, 0)), (3, (0, 0, 0)), (12, (15, 0, 0)), (18, (0, 0, 0))])
anim("shotgun", 14,
     right_arm=[(0, (-60, 0, 0)), (3, (-90, -10, 0)), (4.5, (-90, -10, 0)), (5, (-122, -10, 0)), (7, (-90, -10, 0)), (14, (-40, 0, 0))],
     left_arm=[(0, (-60, 0, 0)), (3, (-90, 15, 0)), (4.5, (-90, 15, 0)), (5, (-117, 15, 0)), (7, (-90, 15, 0)), (14, (-30, 0, 0))],
     body=[(0, (0, 0, 0)), (3, (0, -8, 0)), (5, (-12, -8, 0)), (7, (0, -8, 0)), (14, (0, 0, 0))])
anim("durandal", 40,
     right_arm=[(0, (-30, -40, 0)), (10, (-35, -45, 0)), (12, (20, 30, 0)), (15, (-150, -20, 0)), (17, (-40, 30, 0)), (19, (-160, 20, 0)),
                (21, (-50, -30, 0)), (24, (30, 40, 0)), (28.5, (25, 45, 0)), (30, (-90, 100, 0)), (34, (-80, 105, 0)), (40, (-20, 30, 0))],
     left_arm=[(0, (-20, 20, 0)), (12, (-25, 10, 0)), (17, (-30, -10, 0)), (21, (-30, 10, 0)), (24, (-20, -20, 0)), (30, (-40, -30, 0)), (40, (-15, 0, 0))],
     body=[(0, (5, 30, 0)), (12, (0, 10, 0)), (15, (-5, 0, 0)), (17, (10, -20, 0)), (19, (-5, 10, 0)), (21, (10, 20, 0)), (24, (10, 35, 0)),
           (28.5, (12, 38, 0)), (30, (5, -40, 0)), (34, (5, -42, 0)), (40, (0, 0, 0))],
     right_leg=[(0, (0, 0, 0)), (17, (-20, 0, 0)), (21, (-15, 0, 0)), (24, (-25, 0, 0)), (30, (-40, 0, 0)), (34, (-35, 0, 0)), (40, (0, 0, 0))],
     left_leg=[(0, (0, 0, 0)), (17, (15, 0, 0)), (21, (10, 0, 0)), (24, (20, 0, 0)), (30, (35, 0, 0)), (34, (30, 0, 0)), (40, (0, 0, 0))])

ORDER = ["pistols", "lance", "hammer", "katana", "gauntlets", "knife", "axe", "mace", "greatsword", "shortswords", "shotgun", "durandal"]


def u():
    return str(uuid.uuid4())


def main():
    elements, outliner, group_ids = [], [], {}
    for bone in BONES:
        origin, frm, to, uvo = RIG[bone]
        cid = u()
        w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
        u0, v0 = uvo
        faces = {"north": {"uv": [u0 + d, v0 + d, u0 + d + w, v0 + d + h]}, "east": {"uv": [u0, v0 + d, u0 + d, v0 + d + h]},
                 "south": {"uv": [u0 + 2 * d + w, v0 + d, u0 + 2 * d + 2 * w, v0 + d + h]}, "west": {"uv": [u0 + d + w, v0 + d, u0 + 2 * d + w, v0 + d + h]},
                 "up": {"uv": [u0 + d + w, v0 + d, u0 + d, v0]}, "down": {"uv": [u0 + 2 * d + w, v0, u0 + d + w, v0 + d]}}
        elements.append({"name": bone, "box_uv": True, "rescale": False, "locked": False, "from": frm, "to": to,
                         "autouv": 0, "color": 0, "origin": origin, "uv_offset": uvo, "faces": faces, "type": "cube", "uuid": cid})
        gid = u()
        group_ids[bone] = gid
        outliner.append({"name": bone, "origin": origin, "color": 0, "uuid": gid, "export": True, "mirror_uv": False,
                         "isOpen": False, "locked": False, "visibility": True, "autouv": 0, "children": [cid]})
    animations = []
    for name in ORDER:
        length, bones = A[name]
        animators = {}
        for bone, keys in bones.items():
            kfs = []
            for t, (x, y, z) in keys:
                kfs.append({"channel": "rotation", "data_points": [{"x": f"{-x:g}", "y": f"{-y:g}", "z": f"{z:g}"}],
                            "uuid": u(), "time": round(t / 20, 4), "color": -1, "interpolation": "linear"})
            animators[group_ids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
        animations.append({"uuid": u(), "name": name, "loop": "once", "override": False, "length": length / 20,
                           "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "",
                           "loop_delay": "", "animators": animators})
    model = {"meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
             "name": "furioso_rig", "model_identifier": "furioso_rig", "modded_entity_entity_class": "", "modded_entity_version": "1.17",
             "modded_entity_flip_y": True, "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
             "timeline_setups": [], "unhandled_root_fields": {}, "resolution": {"width": 64, "height": 64},
             "elements": elements, "outliner": outliner, "textures": [], "animations": animations}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(model, f, indent=1)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
