# Last Steps

## Final Cleanup Phase

Feel free to re-arrange those points into a order which you think makes more sense. Feel free to go step by step through the task list here. Make sensible commits.

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
- Remove/Reset legacy left over like versions or sparse enums which where not filled in.
- Update the docs under ../bestia-docs/server (there is a world generator section), with the latest features, explain the pipeline in detail and what parameters are used and their meaning and how this ties into the engine. Provide some code examples too.
- Add interesting/important info gathered here into the ../bestia-docs map generation section.