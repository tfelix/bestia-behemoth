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
    internal const byte Granite = 10;
    internal const byte Grass = 40;

    /// <summary>
    /// The shipped palette, trimmed to the handful of materials these tests use.
    /// </summary>
    /// <remarks>
    /// A subset rather than <c>BlockAppearance.Current</c>, so a test asserting that water and terrain land on
    /// different surfaces says which materials it means instead of depending on two dozen it does not.
    /// </remarks>
    internal static BlockAppearance Appearance() => BlockAppearance.From(new[]
    {
      Block(Water, "WATER", false, new Color(0.16f, 0.35f, 0.52f, 0.72f)),
      Block(2, "ICE", true, new Color(0.78f, 0.88f, 0.93f)),
      Block(Granite, "GRANITE", true, new Color(0.60f, 0.56f, 0.55f)),
      Block(Grass, "GRASS", true, new Color(0.28f, 0.45f, 0.19f))
    });

    private static BlockAppearance.Block Block(byte id, string name, bool solid, Color colour) =>
      new() { Id = id, Name = name, Solid = solid, Opaque = solid, Colour = colour };

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
