"""The White-hot Gladius as a 3D item model (the same profile VergiliusRenderer.PROFILE draws in his hand): a short dark
grip, then the blade swelling, pinching to a waist, swelling a little and tapering to a needle point, with a raised hot
ridge down both flats. The blade is lit at full brightness (neoforge_data). The inventory icon is the flat sprite
(neoforge:separate_transforms). Usage: python tools/gen_gladius_model.py"""
import json, os

PROFILE = [(0.9, 0.5), (1.9, 0.7), (2.9, 1.0), (2.3, 0.7), (1.5, 0.8), (1.2, 0.9), (1.6, 0.7), (1.95, 1.0), (1.75, 1.6), (1.45, 1.6),
           (1.15, 1.6), (0.85, 1.5), (0.55, 1.3), (0.25, 1.1)]
K, T = 1.1, 0.45
OUT = 'src/main/resources/assets/projectminecraft/models/item'
TEXP = 'projectminecraft:item/white_hot_gladius_blade'


def box(x0, y0, z0, x1, y1, z1, v0, v1, lit):
    faces = {f: {'uv': [0, v0, 16, v1], 'texture': '#t'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}
    e = {'from': [round(x0, 3), round(y0, 3), round(z0, 3)], 'to': [round(x1, 3), round(y1, 3), round(z1, 3)], 'faces': faces}
    if lit:
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
    return e


els = [box(7.45, -0.6, 7.45, 8.55, 0.0, 8.55, 12, 16, False), box(7.55, 0.0, 7.55, 8.45, 2.5, 8.45, 12, 16, False)]
y = 2.5
for w, l in PROFILE:
    w, l = w * K, l * K
    els.append(box(8 - w / 2, y, 8 - T / 2, 8 + w / 2, y + l, 8 + T / 2, 0, 8, True))
    if w > 0.4:
        els.append(box(8 - w * 0.2, y, 8 - T / 2 - 0.15, 8 + w * 0.2, y + l, 8 + T / 2 + 0.15, 8, 12, True))
    y += l

model = {
    'textures': {'t': TEXP, 'particle': TEXP},
    'elements': els,
    'display': {
        'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 3.5, 1.2], 'scale': [0.85, 0.85, 0.85]},
        'thirdperson_lefthand': {'rotation': [0, -90, 0], 'translation': [0, 3.5, 1.2], 'scale': [0.85, 0.85, 0.85]},
        'firstperson_righthand': {'rotation': [0, 90, 20], 'translation': [1.5, 2.6, 0.5], 'scale': [0.55, 0.55, 0.55]},
        'firstperson_lefthand': {'rotation': [0, -90, -20], 'translation': [1.5, 2.6, 0.5], 'scale': [0.55, 0.55, 0.55]},
        'head': {'rotation': [0, 0, 0], 'translation': [0, 6, 0], 'scale': [1, 1, 1]},
    },
}
os.makedirs(OUT, exist_ok=True)
json.dump(model, open(os.path.join(OUT, 'white_hot_gladius_3d.json'), 'w'), indent=1)
flat = {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'projectminecraft:item/white_hot_gladius'}}
json.dump({'loader': 'neoforge:separate_transforms', 'base': {'parent': 'projectminecraft:item/white_hot_gladius_3d'},
           'perspectives': {'gui': flat, 'ground': flat, 'fixed': flat}}, open(os.path.join(OUT, 'white_hot_gladius.json'), 'w'), indent=1)
print('elements', len(els), 'length', round(y, 2))
