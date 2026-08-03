# Last Steps

## Tree & Forrests as Entity

Then we need to refine this a bit more. Trees and forrests should later be subject to e.g. like wildfires and grow cycles which is simulated by the ECS system. So it definatly needs to be an entity rather than a part of the map itself. Can you make suggestions how to realize this in a realistic pattern? I assume e.g. we can unload most of those entities if no player is in range but also we maybe need to reduce the total number of single entities a bit. Can you make suggestions to refine this.
What I can imagine is some sort of special entity like a spawn director for vegetation which is placed inside such a biome which takes care of regular watching and respawning entities which got missing (e.g. if a player harvested a certain plant for example and watches over spawn points). But those must be placed in strategic points during world generation. It also makes sense to initially fill the world e.g. with trees so the players do not need to wait until vegetation has grown into place. First lets discuss and brainstorm a few options we have. Also grill me with questions about this topic.
Same goes for bestia spawner or mana crystals. All those stuff must be modelled as an entity and not as a structure of the the tile map. Please design a system which makes it easy upon world generation to save this as an entity.
Then remove all remnantes of those stuff from the world generation which is now not reqsectionuired anymore e.g. like specialized voxel types.

## Improved River Rendering

No comb like structures like on the genesis map. The river should have some sloping in the detail view, maybe even if this is not neceassairly "physically correct", but just good looking. Just make sure rivers do not flow uphill so there needs to be some carving to make them go downhill in the right stage.
But again prefer a nice optic over fully simulation correctness.

# Missing biomes

- please list all available biomes and let me confirm or discuss the existence of those
- ideally we also have some rarer volcanic biome in which are sources like sulfur gysirs or other materials are found (please list a few which make sense in a MMORPG crafting context in volcanic regions).
- we need a lava voxel.
- include possible volcanic eruptions in the history system
- tie lava wells into the temperature system. we need an api for the engine to query local temperature, also taking into account weather conditions and seasons. In the low level regions temperatures should more or less the whole year stay in comfortable regions for most bestia while in the high level areas, high mana or mountains/volcanos, deserts more extreme temperature swings should happen which needs better equipment or buffs for bestia/entities to operate efficiently. Maybe this is a task on its own.

 ## Improved biomes

- suggest interesting resources or events or things you can place or do inside a desert biome so players have a motivation to explore those harsh lands.
-- strong bestia which can be found and their loot is one point but a few more would be nice. (make sure this strong bestia point is reflected in the spawn system)

- do the same for swamps
-- i can imagine more ingrediants for poisons

## Improved City Generation

TBD

## Improved History Generation

- Add some connections to the three factions of the bestia game system. Dont make it too prominent but make it so it is possible that some hints here and there can appear over the course of the time. Since it is important for the game which faction "won" the last world it would be nice if there is a tunable parameter on how much influence a faction has had over the history. So as a little gimick the winner of the last world gets a bit more influence over the history generation.

## Engine Integration

Tie this all into the engine! We need to battle test the renderer if it works for this world. Shader are not really important we will get to those later but stablity and performance are top priority. Double check if the interaction with the engine works or if you see any shortcomings in the communication protocol.
Also make sure "special" stuff like cave systems work.
Check if special marker like trees, treasures in caves, tombes etc translate into entities with visuals that get correctly placed. Make sure those visuals are then persisted into the database. Make sure restards of the server work well and nothing is like double imported especially if players started to modify and re-shape the world. Also take care with how to implement this partial occupancy to allow more shallow slopes and go away from this "minecrafty full blocky" look.

## Final Cleanup Phase

Feel free to re-arrange those points into a order which you think makes more sense.

- Review the architecture and TODO document if anything substantial is left out. Then remove those two documents after the information was consolidated into the ../bestia-docs server and map generation section.
- reset all versions back to initial values, we are dev build nothing exists in the wild yet. We can also check if enums are not containing any legacy placeholders. Consolidate everything down, not legacy code.
- make sure to clearify especially how the storage mechanism of voxel, chunk, player modification (diffs) and regeneration works. So write down every storage that is required for such a map and how it is stored within the engine.
- make sure you also document the motivation behind certain biomes, in the documentation too.
- Go throgh the different voxel materials. I am sure we can maybe reduce some materials there which are not necessairy. This might require some changes to the underlying systems afterwards.
- Full code review of any bugs, code quality, comments.
- list how many possible worlds there are (seed size?)
- unit test for world sizes other then power of two. I think so far we only tested 512 and 1024 and 128. Run some sanity checks for world sizes in between which are supported by the engine.
- run some benchmarks list results and also include the results in die bestia-docs.
- Check if its safe: the game code is open source, if the player do know the world seed they can basically unravel all the secrets of the initial map. Therefore its important that the seed is considered secret. Review the code under this aspect, or if we need some tricks or changes to keep the seed unguessable and server only. Ideally its also not bruteforcable.
- Remove unnecessairy comments which only explain what but not the how, keep it concise and informative. Not redundant, also check for corectness.
- Check existing test coverage (maybe perform a code coverage run and see if there are tests missing somewhere?) No need for 100% coverage but the most important, complex and critical parts should be well tested. Better test too much than too little.
- Check if every world generation parameter is well documented and explains what it does
- for curiosity benchmark other world sizes e.g. 32x32km, 64x64km, 128km and maybe something in between, write the findings down inside the ../bestia-docs.
- Remove code which is not in use and just dead code
- Remove legacy left over like versions or sparse enums which where not filled in.
- Update the docs under ../bestia-docs/server (there is a world generator section), with the latest features, explain the pipeline in detail and what parameters are used and their meaning and how this ties into the engine. Provide some code examples too.