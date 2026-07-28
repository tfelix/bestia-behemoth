using System;
using System.Numerics;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// Which cells of a chunk could possibly contain a piece of surface, as one bit per cell.
  /// </summary>
  /// <remarks>
  /// This is the structure that makes an isosurface mesher affordable on a 32x32x256 chunk. Surface nets is
  /// defined over cells, so a naive pass visits 262 144 of them; the surface itself touches on the order of a
  /// thousand. Everything else is the interior of a solid run or the interior of an air run, where every corner
  /// value is identical and no crossing can exist.
  ///
  /// <para>
  /// Finding those runs is nearly free <i>because</i> the vertical axis is contiguous
  /// (<see cref="VoxelChunk.Index"/>). One column is one 256-byte span, and occupancy is zero exactly where the
  /// block is air, so <c>IndexOfAnyExcept</c> walks a whole run per call and is vectorised by the runtime. Open
  /// terrain is three runs - solid, the one partial voxel, air - so a column costs three SIMD calls rather than
  /// 256 comparisons. A column with a cave costs a few more, which is the point: the cost tracks surface area
  /// rather than volume, and stays that way when caves arrive.
  /// </para>
  ///
  /// <para>
  /// A bitmask rather than a plain min/max band, for the same reason. A column with a cave at 12 m and its
  /// surface at 40 m has two thin bands and 26 m of solid rock between them, and a min/max band would mesh the
  /// rock. <see cref="MinActiveZ"/> and <see cref="MaxActiveZ"/> still exist, but only to size the gather that
  /// <see cref="TerrainPatch"/> does - the per-cell decision is always the mask.
  /// </para>
  ///
  /// <para>
  /// Only boundaries <i>inside</i> the chunk are recorded. A boundary at the chunk's own floor or ceiling is
  /// between this chunk and its vertical neighbour, and cannot be seen from one chunk's arrays alone; those are
  /// resolved by <see cref="SeamAtFloor"/> at mesh time, when the neighbour is in hand. Keeping them out of here
  /// is what lets <see cref="IsUniform"/> mean something: a chunk of solid rock or open air has no interior
  /// boundary, so it is skipped before anything is allocated for it. Most of a view volume is such a chunk.
  /// </para>
  /// </remarks>
  public sealed class ChunkBands
  {
    /// <summary>
    /// The cells that a run boundary between cells <c>t-1</c> and <c>t</c> can give a sign change to.
    /// </summary>
    /// <remarks>
    /// A corner at lattice <c>z</c> is the average of the cells at <c>z-1</c> and <c>z</c>, and a cell's own
    /// corners sit at its index and one above. So cell <c>k</c>'s corners read cells <c>k-1</c> through
    /// <c>k+1</c>, and a boundary at <c>t</c> can therefore only affect cells <c>t-1</c> and <c>t</c>.
    /// </remarks>
    private const int Reach = 1;

    private readonly ulong[] _mask;

    public int Size { get; }

    public int Height { get; }

    /// <summary>64-bit words per column, enough for one bit per cell of a column.</summary>
    public int Words { get; }

    /// <summary>Lowest cell index marked anywhere in the chunk, or -1 for a uniform chunk.</summary>
    public int MinActiveZ { get; }

    /// <summary>Highest cell index marked anywhere, or -1 for a uniform chunk.</summary>
    public int MaxActiveZ { get; }

    /// <summary>
    /// True for a chunk with no interior run boundary at all - solid rock, open air, or open water.
    /// </summary>
    public bool IsUniform => MaxActiveZ < 0;

    private ChunkBands(int size, int height, int words, ulong[] mask, int minActiveZ, int maxActiveZ)
    {
      Size = size;
      Height = height;
      Words = words;
      _mask = mask;
      MinActiveZ = minActiveZ;
      MaxActiveZ = maxActiveZ;
    }

    /// <summary>The words for one column, in cell-index order.</summary>
    public ReadOnlySpan<ulong> ColumnMask(int localX, int localY) =>
      _mask.AsSpan((localY * Size + localX) * Words, Words);

    /// <summary>
    /// Scans a decoded chunk. Pure computation over the occupancy array; safe on a worker thread.
    /// </summary>
    public static ChunkBands Of(VoxelChunk chunk)
    {
      var size = chunk.Size;
      var height = chunk.Height;
      var words = (height + 63) / 64;

      var mask = new ulong[size * size * words];
      var occupancy = chunk.Occupancy.AsSpan();

      var minActive = int.MaxValue;
      var maxActive = -1;

      // Whether every column turned out to be a single run of the same value as every other column. Tracked
      // because a chunk with no interior boundary anywhere can still have a surface: columns that are solid
      // top-to-bottom beside columns that are air top-to-bottom are a cliff face, and no vertical run boundary
      // exists anywhere to mark it. Real terrain does not produce that - it would be a 256 m sheer face inside one
      // chunk - but a uniform chunk is the case this class exists to skip, so it must not skip a wrong one.
      var mixedColumns = false;
      var firstColumnValue = occupancy[0];

      for (var column = 0; column < size * size; column++)
      {
        var wordBase = column * words;
        var strip = occupancy.Slice(column * height, height);

        if (strip[0] != firstColumnValue)
        {
          mixedColumns = true;
        }

        var z = 0;
        while (z < height)
        {
          // One call per run rather than one comparison per cell. Occupancy is zero exactly where the block is
          // air, so this finds material boundaries and the air interface in the same walk.
          var rest = strip.Slice(z).IndexOfAnyExcept(strip[z]);
          var next = rest < 0 ? height : z + rest;

          if (z > 0)
          {
            for (var k = Math.Max(0, z - Reach); k <= Math.Min(height - 1, z + Reach - 1); k++)
            {
              mask[wordBase + (k >> 6)] |= 1UL << (k & 63);
            }
          }

          z = next;
        }

        for (var word = 0; word < words; word++)
        {
          var bits = mask[wordBase + word];
          if (bits == 0)
          {
            continue;
          }

          var low = word * 64 + BitOperations.TrailingZeroCount(bits);
          var high = word * 64 + 63 - BitOperations.LeadingZeroCount(bits);

          if (low < minActive) minActive = low;
          if (high > maxActive) maxActive = high;
        }
      }

      if (maxActive < 0 && mixedColumns)
      {
        // No vertical boundary anywhere, but the columns disagree: mark the lot rather than declare it uniform.
        mask.AsSpan().Fill(~0UL);
        minActive = 0;
        maxActive = height - 1;
      }

      return new ChunkBands(size, height, words, mask, maxActive < 0 ? -1 : minActive, maxActive);
    }

    /// <summary>
    /// Whether the boundary between <paramref name="below"/>'s top cells and <paramref name="chunk"/>'s bottom
    /// cells carries any change in occupancy.
    /// </summary>
    /// <remarks>
    /// Each chunk owns the lattice edges at its own floor and not at its ceiling, so this is the one seam a
    /// chunk has to ask its neighbour about. It matters more than it looks: voxel index zero is sea level, so
    /// terrain at the waterline sits exactly on the floor of chunk <c>z = 0</c>, and a flat coastal plain would
    /// otherwise be a chunk of solid rock and a chunk of air with nobody meshing the surface between them.
    ///
    /// <para>
    /// A missing neighbour is not a seam. <see cref="TerrainPatch"/> extends the boundary cell outward in that
    /// case, which by construction produces no crossing - the alternative, reading absent terrain as air, would
    /// paint a floor across the bottom of every chunk at the edge of what has been streamed.
    /// </para>
    /// </remarks>
    public static bool SeamAtFloor(VoxelChunk chunk, VoxelChunk below)
    {
      if (below == null)
      {
        return false;
      }

      var height = chunk.Height;
      var columns = chunk.Size * chunk.Size;

      for (var column = 0; column < columns; column++)
      {
        if (chunk.Occupancy[column * height] != below.Occupancy[column * height + height - 1])
        {
          return true;
        }
      }

      return false;
    }
  }
}
