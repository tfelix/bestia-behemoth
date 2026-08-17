using System;
using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// A chunk source backed by a plain dictionary, so the mesher can be driven without a socket or a store.
  /// </summary>
  /// <remarks>
  /// This is the whole reason <see cref="IChunkSource"/> exists. <see cref="ClientChunkStore"/> would work here
  /// too, but it drags in manifest reconciliation and revision bookkeeping that have nothing to do with whether a
  /// surface lands in the right place.
  /// </remarks>
  internal sealed class FakeChunkSource : IChunkSource
  {
    private readonly Dictionary<ChunkKey, VoxelChunk> _chunks = new();
    private readonly Dictionary<ChunkKey, ChunkBands> _bands = new();

    internal void Put(VoxelChunk chunk)
    {
      var key = new ChunkKey(chunk.ChunkX, chunk.ChunkY, chunk.ChunkZ);

      _chunks[key] = chunk;
      _bands[key] = ChunkBands.Of(chunk);
    }

    public VoxelChunk Get(ChunkKey key) => _chunks.TryGetValue(key, out var chunk) ? chunk : null;

    public ChunkBands BandsOf(ChunkKey key) => _bands.TryGetValue(key, out var bands) ? bands : null;
  }

  /// <summary>Synthetic terrain shaped like what the generator actually writes.</summary>
  internal static class TerrainFixtures
  {
    internal const int Size = 32;
    internal const int Height = 256;

    internal const byte Air = 0;
    internal const byte Water = 1;
    internal const byte Lava = 3;
    internal const byte Granite = 4;
    internal const byte Sand = 10;
    internal const byte Grass = 13;

    /// <summary>
    /// Rich gold ore, for the tests about what a single ore voxel in a granite wall looks like.
    /// </summary>
    /// <remarks>
    /// Shares <c>GRANITE</c>'s slot and not its colour, which is the entire point of it being here: the two are
    /// the same rock and the ore is only visible because the tint says so.
    /// </remarks>
    internal const byte GoldOre = 32;

    /// <summary>
    /// The shipped palette, trimmed to the handful of materials these tests use.
    /// </summary>
    /// <remarks>
    /// A subset rather than <c>BlockAppearance.Current</c>, so a test asserting that two materials land on
    /// different surfaces says which materials it means instead of depending on two dozen it does not.
    /// </remarks>
    internal static BlockAppearance Appearance() => BlockAppearance.From(new[]
    {
      Block(Water, "WATER", false, BlockAppearance.SurfaceKind.Water,
        BlockAppearance.SurfaceSlot.Neutral, new Color(0.16f, 0.35f, 0.52f, 0.72f)),
      Block(Lava, "LAVA", false, BlockAppearance.SurfaceKind.Lava,
        BlockAppearance.SurfaceSlot.Neutral, new Color(0.95f, 0.36f, 0.09f)),
      Block(2, "ICE", true, BlockAppearance.SurfaceKind.Terrain,
        BlockAppearance.SurfaceSlot.Snow, new Color(0.78f, 0.88f, 0.93f)),
      Block(Granite, "GRANITE", true, BlockAppearance.SurfaceKind.Terrain,
        BlockAppearance.SurfaceSlot.Rock, new Color(0.60f, 0.56f, 0.55f)),
      Block(Sand, "SAND", true, BlockAppearance.SurfaceKind.Terrain,
        BlockAppearance.SurfaceSlot.Sand, new Color(0.85f, 0.76f, 0.55f)),
      Block(Grass, "GRASS", true, BlockAppearance.SurfaceKind.Terrain,
        BlockAppearance.SurfaceSlot.Grass, new Color(0.28f, 0.45f, 0.19f)),
      Block(GoldOre, "ORE_GOLD_RICH", true, BlockAppearance.SurfaceKind.Terrain,
        BlockAppearance.SurfaceSlot.Rock, new Color(0.97f, 0.81f, 0.31f))
    });

    /// <summary>
    /// One fixture material. The surface and the slot are stated rather than derived, and that is the point.
    /// </summary>
    /// <remarks>
    /// The surface used to be <c>solid ? Terrain : Water</c>, which was true of the four materials the fixture
    /// then had and became a trap the moment a second non-solid surface existed: <c>LAVA</c> would have been
    /// placed on the water surface, and every test asserting that lava and water do not share a mesh would have
    /// passed vacuously while asserting the opposite of the truth. The slot is stated for the same reason and
    /// would fail the same way - a rule derived from the id's family would put gold ore on rock by accident, and
    /// the test that checks it does would then be checking the rule against itself.
    /// </remarks>
    private static BlockAppearance.Block Block(
      byte id, string name, bool solid, BlockAppearance.SurfaceKind surface,
      BlockAppearance.SurfaceSlot slot, Color colour) =>
      new()
      {
        Id = id, Name = name, Solid = solid,
        Surface = surface,
        Slot = slot,
        Colour = colour
      };

    /// <summary>
    /// Every column solid up to <paramref name="surface"/>, with the topmost voxel partially filled.
    /// </summary>
    /// <remarks>
    /// Exactly what <c>ChunkMaterializer</c> produces: one solid run and one partial voxel carrying the fraction
    /// of the surface elevation that a whole voxel cannot.
    /// </remarks>
    internal static VoxelChunk Flat(int chunkX, int chunkY, int chunkZ, double surface)
    {
      var blocks = new byte[Size * Size * Height];
      var occupancy = new byte[Size * Size * Height];

      var top = (int)Math.Floor(surface);
      var fraction = surface - top;

      for (var column = 0; column < Size * Size; column++)
      {
        var offset = column * Height;

        for (var z = 0; z < top; z++)
        {
          blocks[offset + z] = Granite;
          occupancy[offset + z] = 255;
        }

        if (fraction > 0.0)
        {
          blocks[offset + top] = Grass;
          occupancy[offset + top] = Quantise(fraction);
        }
      }

      return new VoxelChunk(chunkX, chunkY, chunkZ, Size, Height, blocks, occupancy);
    }

    /// <summary>
    /// How deep the surface cover goes in <see cref="Capped"/>, in whole voxels below the partial one.
    /// </summary>
    /// <remarks>
    /// Three, matching <see cref="Rolling"/> and, more to the point, matching what the server writes:
    /// <c>SurfaceCover</c> puts a soil column under the cap rather than one voxel of grass on bare rock. It is
    /// not decoration for the slot tests - it is what makes them test anything. The mesher scores a cell by its
    /// occupancy, and the cap voxel is the *partial* one, so with a single voxel of cover the full granite cell
    /// beneath outscores it and the vertex takes the rock's material. That is correct behaviour on data the
    /// generator never produces, and it would quietly make every assertion below about the wrong material.
    /// </remarks>
    private const int CoverDepth = 3;

    /// <summary>
    /// Flat ground with a surface cover of <paramref name="cap"/>, chosen per column.
    /// </summary>
    /// <remarks>
    /// The per-column callback is what the slot tests need and what <see cref="Flat"/> cannot express: every
    /// question about blending is a question about what happens where two materials meet, and a fixture with one
    /// material everywhere can only answer the easy half of it.
    /// </remarks>
    internal static VoxelChunk Capped(int chunkX, int chunkY, double surface, Func<int, int, byte> cap)
    {
      var blocks = new byte[Size * Size * Height];
      var occupancy = new byte[Size * Size * Height];

      var top = (int)Math.Floor(surface);
      var fraction = surface - top;

      for (var localY = 0; localY < Size; localY++)
      {
        for (var localX = 0; localX < Size; localX++)
        {
          var cover = cap(chunkX * Size + localX, chunkY * Size + localY);
          var offset = (localY * Size + localX) * Height;

          for (var z = 0; z < top; z++)
          {
            blocks[offset + z] = z > top - CoverDepth ? cover : Granite;
            occupancy[offset + z] = 255;
          }

          blocks[offset + top] = cover;
          occupancy[offset + top] = Quantise(fraction);
        }
      }

      return new VoxelChunk(chunkX, chunkY, 0, Size, Height, blocks, occupancy);
    }

    /// <summary>A chunk of one material top to bottom: solid rock, open air, or open water.</summary>
    internal static VoxelChunk Uniform(int chunkX, int chunkY, int chunkZ, byte block, byte occupancy)
    {
      var blocks = new byte[Size * Size * Height];
      var fill = new byte[Size * Size * Height];

      Array.Fill(blocks, block);
      Array.Fill(fill, occupancy);

      return new VoxelChunk(chunkX, chunkY, chunkZ, Size, Height, blocks, fill);
    }

    /// <summary>
    /// Flat terrain with a hollow through it, so a ceiling anchored from above exists to be meshed.
    /// </summary>
    /// <remarks>
    /// The cell above the void is left half full. That is the case a heightfield mesher cannot express at all and
    /// the one an occupancy fraction has no way to label as filling downward - the neighbours have to say so.
    /// </remarks>
    internal static VoxelChunk WithCave(int chunkX, int chunkY, double surface, int floor, int roof)
    {
      var chunk = Flat(chunkX, chunkY, 0, surface);

      for (var column = 0; column < Size * Size; column++)
      {
        var offset = column * Height;

        for (var z = floor; z < roof; z++)
        {
          chunk.Blocks[offset + z] = Air;
          chunk.Occupancy[offset + z] = 0;
        }

        chunk.Blocks[offset + roof] = Granite;
        chunk.Occupancy[offset + roof] = 128;
      }

      return chunk;
    }

    /// <summary>The elevation <see cref="Rolling"/> puts its waterline at, relative to its sea level.</summary>
    /// <remarks>
    /// Fractional, because the generator's is. Water gets the same fill rule as ground - the voxel the surface falls
    /// inside is partially full - so filling the top water voxel completely would put the waterline a whole metre
    /// above where the test asked for it, and a player would swim on the surface of a puddle.
    /// </remarks>
    internal const double WaterFraction = 0.5;

    /// <summary>
    /// Terrain with relief, a partial top voxel everywhere, and standing water in the hollows.
    /// </summary>
    /// <remarks>
    /// Deliberately hilly - relief of about eighteen metres either side of <paramref name="baseElevation"/>. Patch
    /// depth follows the relief across a chunk and its neighbours, so flat ground would flatter the mesher; this is
    /// closer to the worst case a player walks through.
    ///
    /// <para>
    /// <paramref name="waterLevel"/> is separate from <paramref name="baseElevation"/> on purpose. Folding them
    /// into one parameter makes raising the waterline raise the terrain with it, which leaves the water permanently
    /// lapping at the mean surface and almost nothing genuinely submerged.
    /// </para>
    /// </remarks>
    internal static VoxelChunk Rolling(int chunkX, int chunkY, int baseElevation = 40, int? waterLevel = null)
    {
      var seaLevel = waterLevel ?? baseElevation;
      var blocks = new byte[Size * Size * Height];
      var occupancy = new byte[Size * Size * Height];

      for (var localY = 0; localY < Size; localY++)
      {
        for (var localX = 0; localX < Size; localX++)
        {
          var worldX = chunkX * Size + localX;
          var worldY = chunkY * Size + localY;

          var surface = baseElevation
                        + 9.0 * Math.Sin(worldX / 23.0)
                        + 7.0 * Math.Cos(worldY / 19.0)
                        + 2.0 * Math.Sin((worldX + worldY) / 7.0);

          var top = (int)Math.Floor(surface);
          var offset = (localY * Size + localX) * Height;

          for (var z = 0; z < top; z++)
          {
            blocks[offset + z] = z > top - 3 ? Grass : Granite;
            occupancy[offset + z] = 255;
          }

          blocks[offset + top] = Grass;
          occupancy[offset + top] = Quantise(surface - top);

          for (var z = top + 1; z < seaLevel; z++)
          {
            blocks[offset + z] = Water;
            occupancy[offset + z] = 255;
          }

          if (seaLevel > top)
          {
            blocks[offset + seaLevel] = Water;
            occupancy[offset + seaLevel] = Quantise(WaterFraction);
          }
        }
      }

      return new VoxelChunk(chunkX, chunkY, 0, Size, Height, blocks, occupancy);
    }

    /// <summary>Depth of the lava in <see cref="WithLavaPool"/>, so a test can name the surface it expects.</summary>
    internal const double LavaFraction = 0.4;

    /// <summary>
    /// Flat ground with a crater of lava in the middle of it and standing water outside the crater.
    /// </summary>
    /// <remarks>
    /// Both fluids in one chunk, which is what makes this fixture worth having: the server's
    /// <c>ChunkMaterializer</c> guarantees they never share a column, and the mesher has to keep them on separate
    /// surfaces anyway. If lava ever landed on the water mask the two sheets would merge into one and a lava lake
    /// touching a river would render as a single translucent blue-orange plane.
    ///
    /// <para>
    /// The pool is a disc rather than a half-chunk so its boundary is not axis aligned - a boundary that follows
    /// the voxel grid would let a mesher that mixed the masks still produce plausible-looking geometry.
    /// </para>
    /// </remarks>
    internal static VoxelChunk WithLavaPool(
      int chunkX, int chunkY, int ground, int lavaLevel, int waterLevel, double radius)
    {
      var blocks = new byte[Size * Size * Height];
      var occupancy = new byte[Size * Size * Height];

      for (var localY = 0; localY < Size; localY++)
      {
        for (var localX = 0; localX < Size; localX++)
        {
          var worldX = chunkX * Size + localX + 0.5;
          var worldY = chunkY * Size + localY + 0.5;
          var inPool = Math.Sqrt(worldX * worldX + worldY * worldY) <= radius;

          var offset = (localY * Size + localX) * Height;

          for (var z = 0; z < ground; z++)
          {
            blocks[offset + z] = Granite;
            occupancy[offset + z] = 255;
          }

          // The crater floor is basalt on the server; granite here, because what this fixture is about is which
          // *surface* a fluid lands on and the bed material is not part of that.
          blocks[offset + ground] = inPool ? Granite : Grass;
          occupancy[offset + ground] = 255;

          var fluid = inPool ? Lava : Water;
          var level = inPool ? lavaLevel : waterLevel;
          var fraction = inPool ? LavaFraction : WaterFraction;

          for (var z = ground + 1; z < level; z++)
          {
            blocks[offset + z] = fluid;
            occupancy[offset + z] = 255;
          }

          if (level > ground)
          {
            blocks[offset + level] = fluid;
            occupancy[offset + level] = Quantise(fraction);
          }
        }
      }

      return new VoxelChunk(chunkX, chunkY, 0, Size, Height, blocks, occupancy);
    }

    /// <summary>
    /// Solid rock with a sphere carved out of it at the sub-voxel precision a carve brush produces.
    /// </summary>
    /// <remarks>
    /// Coordinates are chunk-local voxel space, where an integer is a voxel's low corner - so the centre of voxel
    /// <c>(i,j,k)</c> is <c>(i+0.5, j+0.5, k+0.5)</c>. The occupancy written is what is *left*, supersampled rather
    /// than tested at the voxel centre, because the whole question these fixtures exist to answer is how much
    /// removed volume the mesher needs before it draws anything, and a centre test quantises that to whole voxels.
    /// </remarks>
    internal static VoxelChunk SolidWithSphere(
      int chunkX, int chunkY, int chunkZ, double centreX, double centreY, double centreZ, double radius)
    {
      var chunk = Uniform(chunkX, chunkY, chunkZ, Granite, 255);
      var reach = (int)Math.Ceiling(radius) + 1;

      for (var z = (int)centreZ - reach; z <= (int)centreZ + reach; z++)
      {
        for (var y = (int)centreY - reach; y <= (int)centreY + reach; y++)
        {
          for (var x = (int)centreX - reach; x <= (int)centreX + reach; x++)
          {
            if (x < 0 || x >= Size || y < 0 || y >= Size || z < 0 || z >= Height)
            {
              continue;
            }

            var remaining = 1.0 - RemovedFraction(x, y, z, centreX, centreY, centreZ, radius);
            if (remaining >= 1.0)
            {
              continue;
            }

            var index = (y * Size + x) * Height + z;

            chunk.Blocks[index] = remaining <= 0.0 ? Air : Granite;
            chunk.Occupancy[index] = remaining <= 0.0 ? (byte)0 : Quantise(remaining);
          }
        }
      }

      return chunk;
    }

    /// <summary>How much of one voxel a sphere takes, by supersampling it.</summary>
    private static double RemovedFraction(
      int x, int y, int z, double centreX, double centreY, double centreZ, double radius)
    {
      const int Samples = 6;

      var inside = 0;

      for (var c = 0; c < Samples; c++)
      {
        var pz = z + (c + 0.5) / Samples - centreZ;

        for (var b = 0; b < Samples; b++)
        {
          var py = y + (b + 0.5) / Samples - centreY;

          for (var a = 0; a < Samples; a++)
          {
            var px = x + (a + 0.5) / Samples - centreX;

            if (px * px + py * py + pz * pz <= radius * radius)
            {
              inside++;
            }
          }
        }
      }

      return (double)inside / (Samples * Samples * Samples);
    }

    /// <summary>
    /// Solid rock with two carved galleries separated by a wall exactly one voxel thick.
    /// </summary>
    /// <remarks>
    /// The shape removal-only guarantees will occur - a player tunnels twice in parallel and, with no placement
    /// system, can never fill the remainder in. It is here because the mesher's own KDoc used to claim a
    /// one-cell-thick feature collapses, and it does not: the *void* collapses and the *solid* survives. Getting
    /// that backwards argues for deleting the wall, which would delete geometry that renders correctly.
    /// </remarks>
    internal static VoxelChunk SolidWithWalledGalleries(
      int chunkX, int chunkY, int chunkZ, int wallX, int span, int zLo, int zHi)
    {
      var chunk = Uniform(chunkX, chunkY, chunkZ, Granite, 255);

      for (var y = 0; y < Size; y++)
      {
        for (var x = wallX - span; x <= wallX + span; x++)
        {
          if (x == wallX || x < 0 || x >= Size)
          {
            continue;
          }

          for (var z = zLo; z <= zHi; z++)
          {
            var index = (y * Size + x) * Height + z;

            chunk.Blocks[index] = Air;
            chunk.Occupancy[index] = 0;
          }
        }
      }

      return chunk;
    }

    /// <summary>
    /// The elevation a surface actually ends up at once occupancy has been quantised to a byte.
    /// </summary>
    /// <remarks>
    /// What the tests compare against, rather than the ideal value. A byte cannot hold 0.3 exactly, so demanding
    /// 40.3 would be asserting that the wire format is lossless when it is documented not to be - the claim worth
    /// testing is that the mesher loses nothing *further*.
    /// </remarks>
    internal static double Quantised(double surface)
    {
      var top = Math.Floor(surface);

      return top + Quantise(surface - top) / 255.0;
    }

    /// <summary>Matches the server's <c>Occupancy.of</c>: a positive fraction never rounds away to empty.</summary>
    private static byte Quantise(double fraction) =>
      (byte)Math.Max(1, Math.Round(fraction * 255.0));
  }
}
