# World identity rule

Every world in Snake Chase is its own place. What you meet in one world never shows up looking
the same in another.

1. **Obstacles and creatures are unique to their world.** The prowler lives only in the Grove, sharks and
   jellyfish only in the Abyss, frostlings only in the Tundra, magma cracks only in the Crucible.
2. **Mechanics may repeat, but they must be reskinned.** A wall, a spinning bar, a moving block, a
   falling hazard or a vortex can appear in several worlds. Each world draws it in its own material
   and gives it its own name.
3. **Everything in a world belongs to its theme.** In the water world every hazard relates to water;
   in the ice world, to ice and snow; in the lava world, to fire and molten rock.
4. **Snakes respect solid things.** Walls, bars and blocks are solid to snakes in every world: snakes
   steer around them and slide along them, so cover is a real way to escape. A bar that swings
   *into* a snake still stuns it.

## Current mapping

| Mechanic | 1 Neon Drift | 2 Neon Grove | 3 Abyssal Chase | 4 Crystal Tundra | 5 Crucible Core |
|---|---|---|---|---|---|
| Wall | neon wall | mossy log | none | ice slab | none |
| Spinning bar | neon bar | jungle branch | anchor chain | icicle spear | molten girder |
| Moving block | neon block | mossy boulder | none | ice block | none |
| Danger zone | neon zone | none | none | thin ice | lava zone |
| Portal | neon gate | leaf-ringed gate | none | frost-ringed gate | none |
| Vortex | none | none | whirlpool (one, pulls and spins, not lethal) | snow whirl | none |
| Falling hazard | none | none | none | icicles | embers |
| Creature | none | forest prowler | sharks, jellyfish | frostlings | magma cracks |

The art for the reskinned mechanics lives in `ui/canvas/WorldObstacleArt.kt`, chosen with
`WorldStyle.forLevel(levelId)`. Adding a world means adding a `WorldStyle` and a look for every
mechanic it uses before it ships.
