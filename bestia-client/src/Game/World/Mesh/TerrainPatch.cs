using System;
using System.Collections.Generic;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// One chunk's cells plus a one-and-two-cell apron of its neighbours, copied flat so the mesher can index it
  /// without a single bounds test or neighbour lookup.
  /// </summary>
  /// <remarks>
  /// Surface nets needs a wider neighbourhood than it looks. A cell's eight corners are each the average of the
  /// eight cells meeting at them, so meshing cell <c>k</c> reads a 3x3x3 cell neighbourhood; and to tile without
  /// seams a chunk must also compute vertices for the cell just <i>outside</i> its own low edge. Working that
  /// out per read would put a chain of conditionals and a dictionary lookup in the innermost loop of the whole
  /// renderer. Gathering once instead costs a few contiguous copies and leaves the mesher reading a plain array.
  ///
  /// <para><b>Seam ownership.</b> A chunk owns the lattice edges at <c>[0, Size)</c> on each axis - so it draws
  /// the surface on its own low boundary and leaves the high one to its neighbour. That splits every shared face
  /// between exactly one of the two chunks: no gaps, and no two chunks emitting the same quad into the same place
  /// to fight over the depth buffer.
  /// </para>
  ///
  /// <para><b>A missing neighbour is extended, not treated as air.</b> Terrain appears as soon as a chunk
  /// decodes rather than waiting for a ring of neighbours, which matters on login. The cost is that a boundary
  /// with nothing beyond it reads as flat, so the mesh there is provisional -
  /// <see cref="MissingNeighbours"/> reports which positions would change it, and the renderer re-meshes when one
  /// arrives. Reading absent terrain as air instead would wrap every chunk at the edge of the streamed disc in a
  /// shell of cliff faces.
  /// </para>
  /// </remarks>
  public sealed class TerrainPatch
  {
    /// <summary>
    /// Cells needed below the chunk's own low edge on each axis.
    /// </summary>
    /// <remarks>
    /// Two, not one. A quad on the lattice edge at zero is built from the vertices of the cells at <c>-1</c> and
    /// <c>0</c>, and the vertex for cell <c>-1</c> reads corners at lattice <c>-1</c>, which average in the cell
    /// at <c>-2</c>.
    /// </remarks>
    public const int ApronLow = 2;

    /// <summary>Cells needed above the chunk's own high edge: the corner at lattice <c>Size</c> averages it in.</summary>
    public const int ApronHigh = 1;

    private readonly byte[] _blocks;
    private readonly byte[] _occupancy;
    private readonly ulong[] _active;

    /// <summary>Cells per horizontal axis, apron included.</summary>
    public int Width { get; }

    /// <summary>Cells along the vertical axis, apron included. Contiguous in memory.</summary>
    public int Depth { get; }

    /// <summary>Words per padded column in <see cref="_active"/>.</summary>
    private int Words { get; }

    /// <summary>The chunk this patch was gathered for.</summary>
    public ChunkKey Key { get; }

    /// <summary>Chunk dimensions, repeated here so the mesher needs nothing else.</summary>
    public int Size { get; }

    public int Height { get; }

    /// <summary>Lowest lattice z, in chunk-local coordinates, whose quads this patch is responsible for.</summary>
    public int QuadZLo { get; }

    /// <summary>Highest such lattice z.</summary>
    public int QuadZHi { get; }

    /// <summary>
    /// Positions whose absence made part of this patch guesswork, so the renderer knows what to wait for.
    /// </summary>
    public ChunkKey[] MissingNeighbours { get; }

    private TerrainPatch(
      ChunkKey key, int size, int height, int width, int depth, int words,
      int quadZLo, int quadZHi,
      byte[] blocks, byte[] occupancy, ulong[] active, ChunkKey[] missingNeighbours)
    {
      Key = key;
      Size = size;
      Height = height;
      Width = width;
      Depth = depth;
      Words = words;
      QuadZLo = quadZLo;
      QuadZHi = quadZHi;
      _blocks = blocks;
      _occupancy = occupancy;
      _active = active;
      MissingNeighbours = missingNeighbours;
    }

    /// <summary>Index of a chunk-local cell in the flat arrays. The vertical axis is contiguous.</summary>
    public int IndexOf(int localX, int localY, int localZ) =>
      PatchIndexOf(localX + ApronLow, localY + ApronLow, localZ - QuadZLo + ApronLow);

    /// <summary>Index of a cell in patch coordinates, where the apron starts at zero.</summary>
    public int PatchIndexOf(int px, int py, int pz) => (py * Width + px) * Depth + pz;

    public byte BlockAt(int index) => _blocks[index];

    public byte RawOccupancyAt(int index) => _occupancy[index];

    /// <summary>Cells in bulk, for the passes that walk all of them and cannot afford a call per read.</summary>
    public ReadOnlySpan<byte> Blocks => _blocks;

    public ReadOnlySpan<byte> Occupancies => _occupancy;

    /// <summary>Cells in the patch, apron included.</summary>
    public int CellCount => _occupancy.Length;

    /// <summary>
    /// Whether any cell in this padded column could hold a piece of surface.
    /// </summary>
    /// <remarks>
    /// Already dilated across the 3x3 horizontal neighbourhood, because a cell's corners read its horizontal
    /// neighbours too - a cliff face is a crossing in a column whose own occupancy never changes.
    /// </remarks>
    public bool AnyActive(int localX, int localY)
    {
      var column = ((localY + ApronLow) * Width + (localX + ApronLow)) * Words;

      for (var word = 0; word < Words; word++)
      {
        if (_active[column + word] != 0)
        {
          return true;
        }
      }

      return false;
    }

    /// <summary>The active bits of one padded column, indexed by patch z.</summary>
    public ReadOnlySpan<ulong> ActiveMask(int localX, int localY) =>
      _active.AsSpan(((localY + ApronLow) * Width + (localX + ApronLow)) * Words, Words);

    /// <summary>Converts a patch-z bit index back to a chunk-local cell index.</summary>
    public int LocalZOf(int patchZ) => patchZ + QuadZLo - ApronLow;

    /// <summary>
    /// Gathers the patch for one chunk, or returns <c>null</c> if the chunk cannot contain any surface.
    /// </summary>
    /// <remarks>
    /// The early return is what keeps a view volume cheap. A chunk with no interior run boundary is solid rock,
    /// open air or open water; the only surface it can carry is on its floor, and only if the chunk below
    /// disagrees with it. Most of the 121 chunks a player holds are exactly that chunk, and they cost one band
    /// lookup and one comparison pass each.
    /// </remarks>
    public static TerrainPatch Gather(IChunkSource source, ChunkKey key)
    {
      var chunk = source.Get(key);
      var bands = source.BandsOf(key);

      if (chunk == null || bands == null)
      {
        return null;
      }

      var size = chunk.Size;
      var height = chunk.Height;

      var below = source.Get(new ChunkKey(key.X, key.Y, key.Z - 1));
      var seamAtFloor = ChunkBands.SeamAtFloor(chunk, below);

      var lo = bands.IsUniform ? int.MaxValue : bands.MinActiveZ;
      var hi = bands.IsUniform ? -1 : bands.MaxActiveZ;

      if (seamAtFloor)
      {
        // The lattice edge at the chunk's floor is this chunk's to draw, and its quads are built from the cells
        // at -1 and 0. Cell 0 is the lowest one that can carry a vertex for it.
        lo = Math.Min(lo, 0);
        hi = Math.Max(hi, 0);
      }

      if (hi < 0)
      {
        return null;
      }

      // A cliff on a shared boundary is a crossing in a column whose own occupancy is constant, so a neighbour's
      // bands can widen the range this chunk has to gather. Only the eight horizontal neighbours: a vertical one
      // contributes through the floor seam above.
      for (var dy = -1; dy <= 1; dy++)
      {
        for (var dx = -1; dx <= 1; dx++)
        {
          if (dx == 0 && dy == 0)
          {
            continue;
          }

          var neighbour = source.BandsOf(new ChunkKey(key.X + dx, key.Y + dy, key.Z));
          if (neighbour == null || neighbour.IsUniform)
          {
            continue;
          }

          lo = Math.Min(lo, neighbour.MinActiveZ);
          hi = Math.Max(hi, neighbour.MaxActiveZ);
        }
      }

      var quadZLo = Math.Max(0, lo);
      var quadZHi = Math.Min(height - 1, hi);

      if (quadZHi < quadZLo)
      {
        return null;
      }

      return Build(source, key, size, height, quadZLo, quadZHi, seamAtFloor);
    }

    private static TerrainPatch Build(
      IChunkSource source, ChunkKey key,
      int size, int height, int quadZLo, int quadZHi, bool seamAtFloor)
    {
      var width = size + ApronLow + ApronHigh;
      var depth = (quadZHi - quadZLo + 1) + ApronLow + ApronHigh;
      var words = (depth + 63) / 64;

      var blocks = new byte[width * width * depth];
      var occupancy = new byte[width * width * depth];
      var raw = new ulong[width * width * words];

      // Global voxel z of patch z zero.
      var baseVoxelZ = (long)key.Z * height + quadZLo - ApronLow;

      var missing = new List<ChunkKey>(4);

      for (var py = 0; py < width; py++)
      {
        for (var px = 0; px < width; px++)
        {
          var voxelX = (long)key.X * size + px - ApronLow;
          var voxelY = (long)key.Y * size + py - ApronLow;

          var chunkX = FloorDiv(voxelX, size);
          var chunkY = FloorDiv(voxelY, size);
          var localX = (int)(voxelX - (long)chunkX * size);
          var localY = (int)(voxelY - (long)chunkY * size);

          // A horizontal neighbour that is not held at all: read this chunk's own edge column instead, which
          // continues the terrain flat rather than cutting it off with a cliff into nothing.
          if ((chunkX != key.X || chunkY != key.Y) &&
              source.Get(new ChunkKey(chunkX, chunkY, key.Z)) == null)
          {
            var absent = new ChunkKey(chunkX, chunkY, key.Z);
            if (!missing.Contains(absent))
            {
              missing.Add(absent);
            }

            chunkX = key.X;
            chunkY = key.Y;
            localX = Math.Clamp(px - ApronLow, 0, size - 1);
            localY = Math.Clamp(py - ApronLow, 0, size - 1);
          }

          var stripBase = (py * width + px) * depth;

          GatherStrip(
            source, chunkX, chunkY, localX, localY, height, baseVoxelZ, depth,
            blocks.AsSpan(stripBase, depth), occupancy.AsSpan(stripBase, depth));

          GatherMask(
            source, chunkX, chunkY, localX, localY, key.Z, height,
            quadZLo, depth, seamAtFloor, raw.AsSpan((py * width + px) * words, words));
        }
      }

      var active = Dilate(raw, width, words);

      return new TerrainPatch(
        key, size, height, width, depth, words, quadZLo, quadZHi,
        blocks, occupancy, active, missing.ToArray());
    }

    /// <summary>
    /// Copies one vertical strip of cells, crossing into the chunks above and below as needed.
    /// </summary>
    /// <remarks>
    /// Contiguous copies rather than a loop of reads, because the vertical axis is contiguous in a chunk. A strip
    /// spans at most three chunks and almost always exactly one, so this is typically a single
    /// <c>CopyTo</c> per array.
    /// </remarks>
    private static void GatherStrip(
      IChunkSource source, int chunkX, int chunkY, int localX, int localY,
      int height, long baseVoxelZ, int depth,
      Span<byte> blocks, Span<byte> occupancy)
    {
      var firstKnown = -1;
      var lastKnown = -1;

      var patchZ = 0;
      while (patchZ < depth)
      {
        var voxelZ = baseVoxelZ + patchZ;
        var chunkZ = FloorDiv(voxelZ, height);
        var localZ = (int)(voxelZ - (long)chunkZ * height);
        var take = Math.Min(depth - patchZ, height - localZ);

        var held = source.Get(new ChunkKey(chunkX, chunkY, chunkZ));

        if (held != null)
        {
          var offset = (localY * held.Size + localX) * height + localZ;

          held.Blocks.AsSpan(offset, take).CopyTo(blocks.Slice(patchZ, take));
          held.Occupancy.AsSpan(offset, take).CopyTo(occupancy.Slice(patchZ, take));

          if (firstKnown < 0)
          {
            firstKnown = patchZ;
          }

          lastKnown = patchZ + take - 1;
        }

        patchZ += take;
      }

      if (firstKnown < 0)
      {
        // Nothing in this column at all. Leaves air, which draws nothing.
        return;
      }

      // Extend the outermost known cell rather than leaving air. Below the streamed slab that keeps rock reading
      // as rock instead of growing a floor; above it, air stays air.
      for (var z = 0; z < firstKnown; z++)
      {
        blocks[z] = blocks[firstKnown];
        occupancy[z] = occupancy[firstKnown];
      }

      for (var z = lastKnown + 1; z < depth; z++)
      {
        blocks[z] = blocks[lastKnown];
        occupancy[z] = occupancy[lastKnown];
      }
    }

    /// <summary>
    /// Copies one column's active bits into patch coordinates.
    /// </summary>
    /// <remarks>
    /// Cells outside the target chunk's own vertical range are marked active unconditionally. They are at most
    /// three cell layers of the whole patch, and the bands of the chunk they really belong to are in different
    /// local coordinates - paying for the conversion would cost more than checking a handful of cells that turn
    /// out to be uniform.
    ///
    /// <para>
    /// <paramref name="seamAtFloor"/> marks cell zero regardless of the bands, and that is not a refinement. A
    /// chunk's bands only know about boundaries inside it, so a chunk of air over a chunk of rock has no marked
    /// cell anywhere even though the surface between them is this chunk's to draw. Sea level is voxel zero, which
    /// is a chunk floor, so that is where a coastal plain lives rather than being a corner case.
    /// </para>
    /// </remarks>
    private static void GatherMask(
      IChunkSource source, int chunkX, int chunkY, int localX, int localY,
      int chunkZ, int height, int quadZLo, int depth, bool seamAtFloor, Span<ulong> mask)
    {
      var bands = source.BandsOf(new ChunkKey(chunkX, chunkY, chunkZ));
      var columnMask = bands == null ? default : bands.ColumnMask(localX, localY);

      for (var patchZ = 0; patchZ < depth; patchZ++)
      {
        var localZ = patchZ + quadZLo - ApronLow;

        var active = localZ < 0
                     || localZ >= height
                     || bands == null
                     || (seamAtFloor && localZ == 0)
                     || (columnMask[localZ >> 6] & (1UL << (localZ & 63))) != 0;

        if (active)
        {
          mask[patchZ >> 6] |= 1UL << (patchZ & 63);
        }
      }
    }

    /// <summary>
    /// Spreads each column's active bits into its eight horizontal neighbours.
    /// </summary>
    /// <remarks>
    /// A cell's corners average in the cells diagonally beside it, so a crossing can appear in a column whose own
    /// occupancy never changes - which is exactly what a cliff face is. Without this the vertical faces of every
    /// step in the terrain would be missing.
    /// </remarks>
    private static ulong[] Dilate(ulong[] raw, int width, int words)
    {
      var dilated = new ulong[raw.Length];

      for (var py = 0; py < width; py++)
      {
        for (var px = 0; px < width; px++)
        {
          var target = (py * width + px) * words;

          for (var dy = -1; dy <= 1; dy++)
          {
            var ny = py + dy;
            if (ny < 0 || ny >= width)
            {
              continue;
            }

            for (var dx = -1; dx <= 1; dx++)
            {
              var nx = px + dx;
              if (nx < 0 || nx >= width)
              {
                continue;
              }

              var neighbour = (ny * width + nx) * words;
              for (var word = 0; word < words; word++)
              {
                dilated[target + word] |= raw[neighbour + word];
              }
            }
          }
        }
      }

      return dilated;
    }

    private static int FloorDiv(long value, int divisor)
    {
      var quotient = value / divisor;

      if (value % divisor != 0 && (value < 0) != (divisor < 0))
      {
        quotient--;
      }

      return (int)quotient;
    }
  }
}
