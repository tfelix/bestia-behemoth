# Last Steps

## Tree & Forrests as Entity

Then we need to refine this a bit more. Trees and forrests should later be subject to e.g. like wildfires and grow cycles which is simulated by the ECS system. So it definatly needs to be an enttiy rather than a part of the map itself. Can you make suggestions how to realize this in a realistic pattern? I assume e.g. we can unload most of those entities if no player is in range but also we maybe need to reduce the total number of single entities a bit. Can you make suggestions to refine this

## Improved River Rendering

No comb like structures like on the genesis map. The river should have some sloping, maybe even if this is not neceassairly "physically correct", but just good looking. Just make sure rivers do not flow uphill so there needs to be some carving to make them go downhill in the right stage.
But again prefer a nice optic over fully simulation correctness.

## Wather System

In the game engine there should be a weather "simulation" essentially depending on the season and the day there should be some sort of probabilistic distribution of weather events. Think about a way to represent this to the engine. In essence there should be a region in which there is "rain" with 10% chance per day at a season of the year. Seasonality is enough and make the regions not too big and not too small just for a player to notice if he changes the map region there could also be a reasonable change in weather.
Depending on the region parameter but also on the local mana density (see below) there should be a higher chance of extreme weather events like tornado, thunder storm and rain.

## Bestia Spawn System

We need a way to tell the zone server where bestias spawn points should be placed. There must be interesting challenge for a level ramp.
The lowest entry level bestia should initially spawn expecially around the 3 spawn point villages for players we have determined by the existing systems. We are talking about a few km "safety range".
In general the more remote the area is, away from existing settlements the higher the bestia level range should be.
Mountains, deserts, remote regions, all should increase those levels. (no spawns on the oceans so far there are no fully aquatic bestias yet).
There will be a cross reference with the mana corruption system (see below).
Depending on the mana corruption of the land this will generally increase the bestia level which can be encountered there (more mana spawns more dangerous bestia).

## Mana Corruption System

We need a system which lets mana seep and corrupt the world. This means bestia will spawn more dangerous and in higher levels.
On initial world building mana can just be randomly placed on the world as an overlay (ideally this would be something like a perlin noise (check if this make sense or we have already a good fitting noise system)) and then subtract a bit from this influence where settlements are or roads. Basically all civilized places reduce the mana influence there. So the initial world does not have high mana influences inside settlements.

Areas with high mana content (suggest a threshold) make the land corrupt. Consider new biomes e.g. "corrupted grasland" (just add corrupted as prefix). Generally this is important for the renderer as we will change the appearence of those lands and also spawn special entities there like mana crystals or events like mana storms etc.
Bestia of the highest level spawn there or bosses. Basically endgame content. Ore is converted into a specialized "mana infested ore", i need a good name for it. (Please suggest a name for this "specialized mana corrupted ore").
Think about if it make sense to double the biomes as this effectivly doubles the amount needed or if there is a better solution to "mark" those lands. Also consider how the engine works and transfers tiles to the player. Technically the appearance of such lands should change in the renderer, more dangerous enemies should spawn (but this is handled by the spawn system), living structures like trees, gras etc are getting damaged or turned into other entities (but this ties into the entity ECS system and is not part of the map and voxel design, just as background info for you).

There should be a parameter of how much % of land initially should be initially corrupted to tweak this for the designer. I suggest to star twith ~10% of landmass.

We need to scatter points of mana crystal deposits on the world as this is also an important resource. They are found on the surface, like plants or trees, not underground like ores.
Small mana crystal deposits can be found on non corrupted land too. So lower level players can already start to collect it.

Consider this mana system also in the history generation. E.g. mystical events can or should happen in areas of high concentration. Keep it vague if this is possible. At least make some suggestion first what you can imagine.

## Improved City Generation

TBD

## Improved History Generation

- Add some connections to the three factions of the bestia game system. Dont make it too prominent but make it so it is possible that some hints here and there can appear over the course of the time. Since it is important for the game which faction "won" the last world it would be nice if there is a tunable parameter on how much influence a faction has had over the history. So as a little gimick the winner of the last world gets a bit more influence over the history generation.

## Improved Ore Deposit

- We need the following ores:
  - Iron Ore
  - Gold Ore
  - Silver Ore
  - Tin Ore
  - Copper Ore
  - Mithrandium Ore

- The total deposit of each ore should be able to be denominated in tons
- 3 voxel types: rich ore deposit, medium ore deposit, small ore deposit, where the rich ore yields 5kg, medium 1kg, small 0.5kg. (the idea is to despawn the voxel and spawn a pickable item for the player).
- The distribution should be 5:3:1 between small:medium:rich deposits.

## Navigation Mesh/Networks

Suggest and plan a system which is needed for NPC to navigate the world. They should be capable of long range travel but also short distance high accurancy movement.
It is important to tune it e.g. NPC enemy bestia should be able to avoid high speed ways like roads while merchants, player bestia etc. should be able to prefer those "high speed, low cost" networks.
The system must be able to adopt and updatable to some changes e.g. a bridge was destroyed.
If possible, the system should not be instant e.g. if a new road was build or a bridge destroyed not all NPC should immediatly "know" this and update their pathing magically. But keep the cost of this system somewhat reasonable. If this is super expensive let me know and we must re-evaluate how to implement this.
Also think about a good way to ties this into the exsting zone server backend. The pregenerated and stored network data must be quickly accessible for the navigation algorithms.

## Engine Integration

Tie this all into the engine! We need to battle test the renderer if it works for this world. Shader are not really important we will get to those later but stablity and performance are top priority. Double check if the interaction with the engine works or if you see any shortcomings in the communication protocol.
Also make sure "special" stuff like cave systems work.
Check if special marker like trees, treasures in caves, tombes etc translate into entities with visuals that get correctly placed. Make sure those visuals are then persisted into the database. Make sure restards of the server work well and nothing is like double imported especially if players started to modify and re-shape the world.

## Cleanup Phase

- Review the architecture and TODO document if anything substantial is left out. Then remove those two documents after the information was consolidated into the ../bestia-docs server and map generation section.
- Go throgh the different voxel materials. I am sure we can maybe reduce some materials there which are not necessairy. This might require some changes to the underlying systems afterwards.
- Full code review of any bugs, code quality, comments.
- Check if its safe: the game code is open source, if the player do know the world seed they can basically unravel all the secrets of the initial map. Therefore its important that the seed is considered secret. Review the code under this aspect, or if we need some tricks or changes to keep the seed unguessable and server only. Ideally its also not bruteforcable.
- Remove unnecessairy comments which only explain what but not the how, keep it concise
- Check existing test coverage (maybe perform a code coverage run and see if there are tests missing somewhere?) No need for 100% coverage but the most important, complex and critical parts should be well tested. Better test too much than too little.
- Check if every world generation parameter is well documented and explains what it does
- Remove code which is not in use and just dead code
- Remove legacy left over like versions or sparse enums which where not filled in.
- Update the docs under ../bestia-docs/server (there is a world generator section), with the latest features, explain the pipeline in detail and what parameters are used and their meaning and how this ties into the engine. Provide some code examples too.