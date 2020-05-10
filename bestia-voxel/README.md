# Bestia-Voxel

This aims to be a slim and small library around efficient voxel storage and retrieval for massive game worlds.

It is heavily inspired by thoughts from the [roblox blog post](https://blog.roblox.com/2017/04/voxel-terrain-storage/)
about voxel storage algorithms. Yet its very specialized for the use in the [Bestia Game](https://bestia-game.net) so
it might not directly usable for you.

## Modifications

I felt that in Bestia I possibly need more than the 255 materials suggested in the blog post. Yet I don't think a
quantization of 255 steps is needed for occupancy of a single voxel but instead 64 steps should be more then enough.
This resulted in 2^6 (6 bits) for the quantization and 12bit for the materials.

Which leaves me with the following specs:

* Up to 2048 different materials
* 64 quantization levels for voxel occupation
