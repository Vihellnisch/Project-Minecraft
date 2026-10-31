# Project Minecraft (NeoForge 1.21.1)

Project Moon in Minecraft: Roland's gloves and Furioso, three E.G.O weapons from Limbus Company, and the Black Silence's reception in the Library from Library of Ruina. Everything is in the Project Minecraft creative tab.

These used to be part of [Random Slop](https://github.com/Vihellnisch/Effect-Slop). Items from old worlds carry over: the old `effectslop:` ids are aliased to `projectminecraft:`.

## The Black Silence (boss)

A **Library Invitation** turns up now and then in ancient city, end city, stronghold, woodland mansion and ominous trial chests. Hold it up and the pages take you to **the Library**, your own reception, where Roland waits at his desk. Use it again there to go home. Lose, and you wake at home with everything you carried and the invitation still in hand; win, and it's spent. Fought after the reception in Library of Ruina:

1. **The Black Silence**: his nine workshop weapons one page at a time (Atelier Logic's pistols and shotgun, Zelkova's axe and mace, Allas's lance, Durandal, Old Boys' hammer, Wheels' greatsword, Ranga's gauntlets and knife, Crystal Atelier's short swords, Mook's katana). Once all nine are spent: **Furioso**.
2. **Distorted**: he breaks into a crawling beast of flesh, eye holes, maws and black tendrils, and the floor gives way to a factory under a tower of pipes. Claws, tail, a lunging bite, a sinew rope that drags you in, tendrils bursting from the floor, and **Suffocating Haze**: up to three stacks, each choking you with something every few seconds. Four Smouldering Byproducts fight with him, and crawl back once.
3. **Roland and Angelica**: the distortion settles into smoke around him, his weapons black with it, and Angelica forms out of white mist behind him. **Soul Link**: bring one down and they come back a few seconds later, a link fewer, unless you bring the other down while they're still mist. With no links left, both stagger. Their combos, Dark Bond and Ashen Bond, and the **Waltz in Black and White**, which knocks the weapon out of your hand.
4. **The Black Silence and his weapons**: memories of guests he fought (each with its own colour), and every few seconds three of his weapons rise behind him. Break them before the next scene, or they're nailed into the floor, and every nailed one adds two blows to his next Furioso.
5. **Images of the Bygones**: he crouches in his smoke, untouchable, while four images of old guests fight for him, then rises and **Screams**.

He drops the **Black Silence** gloves and his **Perception-Blocking Mask**: a plain black square plate worn over the face. While you wear it, hostile creatures don't notice you, not even an enderman meeting your eyes; strike one and that one sees you and comes for you. Leave the Library mid-fight and he goes back to his desk. Skins by the user.

Invitations turn up in woodland mansion chests (12%), ancient city and end city treasure chests (6%), stronghold chests (5%) and ominous trial vaults (4%).

## E.G.O

| Item | How it works |
|---|---|
| Solemn Lament | Five coins. The Living and The Departed fire in turn at random enemies, each shot a swarm of funeral butterflies; hits heal you |
| Magic Bullet | One coin. The Freischutz fires through a corridor of magic circles; the count goes 1 to 7, and the seventh bullet is something else |
| 4th Match Flame | One coin. You appear before the enemy and write the character for "fierce" in fire, then it ignites |

E.G.O skills open with a Limbus-style cut-in in the sin's colour. Coins flip above your head; heads add power.

## Black Silence and Furioso
**Black Silence**: Roland's gloves from Library of Ruina, worn on both hands (black leather, the flat-lining pulse on the back). They go in the **Curios** hands slot (right-click to put them on), or in the offhand if Curios isn't installed.

**The wheel**: tap **B** to switch to the next weapon, or hold **B** and point at one of his twelve, then let go. With an empty main hand, every attack uses that weapon:
- pistols and shotgun shoot (hitscan, and an 8-pellet cone)
- the lance thrusts and lunges
- the hammer smashes the ground around the target and briefly guards you
- the katana dashes through in one iai cut
- gauntlets jab, the knife bleeds, the axe sweeps, the mace launches
- the greatsword slams down hard
- the short swords cut twice with a sidestep
- Durandal makes a long cut

Holding a tool or block works as normal, and nothing touches the terrain. **Random mode** (click the wheel's centre) draws a new weapon every 3 hits, favouring ones you haven't used on that enemy yet.

**Furioso is earned**: land all twelve weapons on the same enemy (the pips right of the hotbar light up; progress resets after 30 s without hitting it, or if you switch targets). The wheel's centre then becomes **FURIOSO**, his page exactly as he plays it: Atelier Logic pistols, the Allas Workshop lance, the Old Boys Workshop hammer, the Mook Workshop katana, the Ranga Workshop gauntlets and combat knife, the Zelkova Workshop axe and mace, the Wheels Industry greatsword, the Crystal Atelier short swords, the Atelier Logic shotgun, and finally Durandal. Each weapon seeps out of the gloves in black mist, strikes as many times as its combat page has dice, and is tossed away to dissolve. Every big hit drops into slow motion with a steady camera shot, each new weapon gets a freeze-frame close-up with its workshop's name, and a hit counter climbs the whole way. The victim can't die before the last cut; anything that survives it bleeds (Wither) and is bound (Slowness).

The moves are keyframed in Blockbench: `tools/blockbench/furioso.bbmodel` (Animate tab, 1 s = 20 script ticks). After editing, run `python tools/bbmodel_to_furioso.py` to bake them into `FuriosoAnims.java`.

## Config
`projectminecraft-common.toml` (E.G.O cooldown multiplier) and `projectminecraft-client.toml` (screen shaders, camera shake, hit stop, impact frames, cinematics). Both are editable in-game through Mods > Project Minecraft > Config.

## Building
JDK 21 is required. `gradlew build` puts the jar in `build/libs/`.

Tours (dev-only visual checks, `src/tour`): delete `run-tour/saves/es_test`, then `gradlew runTourClient -DtourRoland=true` (the reception: invite, models, mask, arena, looks, p1-p5, death, reset) or `-DtourGloves=true` (every weapon held and swung, the model sheet, and a real Furioso). `-DtourOnly=mask,furioso` keeps just the stops whose names contain one of those. Screenshots go to `run-tour/screenshots`.

Asset tools (`tools/`):
- `java tools/GenModels.java src/main/resources`: the E.G.O, Black Silence and Furioso weapon models (palette rows match Random Slop's sheets).
- `java tools/GenRolandTextures.java src/main/resources [skins|mist|distorted|beast]`: Angelica's hair, the Bygones, Angelica's mist, the distorted weapon textures and the beast (Roland's and Angelica's own skins are the user's art, not generated).
- `java tools/GenGloveTextures.java src/main/resources`: the gloves' worn texture and icon.
- `tools/gen_textures.ps1`: the item sprites. `tools/build_sounds.ps1` (ffmpeg): the E.G.O and Furioso sounds; the rest are copies of Random Slop's.
- `tools/blockbench/furioso.bbmodel` + `python tools/bbmodel_to_furioso.py`: Furioso's keyframes.

## Credits
Based on Project Moon's Library of Ruina and Limbus Company (fan work, not affiliated). Roland and Angelica skins by the user.
Sounds: [Kenney](https://kenney.nl) Sci-Fi Sounds and Impact Sounds (CC0), [EZduzziteh "Squish Sounds Effects"](https://opengameart.org/content/squish-sounds-effects) (CC0). Several sounds are synthesized.
