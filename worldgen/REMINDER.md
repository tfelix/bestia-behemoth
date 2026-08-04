# Last Steps

## Improved City Generation

TBD

## Improved History Generation

- Add some connections to the three factions of the bestia game system. Dont make it too prominent but make it so it is possible that some hints here and there can appear over the course of the time. Since it is important for the game which faction "won" the last world it would be nice if there is a tunable parameter on how much influence a faction has had over the history. So as a little gimick the winner of the last world gets a bit more influence over the history generation.
- Also review the system in general and make a few suggestion on how you think the system can be improved to give the player easier access and deeper lore of the world.
- Suggest some ways on how to integrate this lore system later into the game especially considering NPC or written hints, artifacts etc.

## Engine Integration

Tie this all into the engine! We need to battle test the renderer if it works for this world. Shader are not really important we will get to those later but stablity and performance are top priority. Double check if the interaction with the engine works or if you see any shortcomings in the communication protocol.
This is more like a tight review on the renderer integration into the world system. Look into it with a fresh mind. Analyze for bugs or inconsistency.
Also make sure "special" stuff like cave systems work.
Check if special marker like trees, treasures in caves, tombes etc translate into entities with visuals that get correctly placed. Make sure those visuals are then persisted into the database. Make sure restards of the server work well and nothing is like double imported especially if players started to modify and re-shape the world. Also take care with how to implement this partial occupancy to allow more shallow slopes and go away from this "minecrafty full blocky" look.

Please compile a list of issues you have found and how to tackle them.

## Final Cleanup Phase

Feel free to re-arrange those points into a order which you think makes more sense.

- Review the architecture and TODO document if anything substantial is left out. Then remove those two documents after the information was consolidated into the ../bestia-docs server/map generation sections.
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