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
  /// <b>Two passes, because a run boundary is only half of a surface.</b> Walking a column finds every
  /// horizontal face - the ground, a cave floor, a cave ceiling - and finds no vertical one at all, because a
  /// wall changes nothing as you walk down either side of it. <see cref="MarkHorizontalFaces"/> is the other
  /// half, comparing adjacent columns run by run so that a cliff and the wall of a gallery are marked too. Both
  /// passes cost surface area rather than volume, which is the property this class exists for.
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

    /// <summary>Marks the cells a run boundary at <paramref name="boundary"/> can give a sign change to.</summary>
    private static void Mark(ulong[] mask, int wordBase, int height, int boundary)
    {
      for (var k = Math.Max(0, boundary - Reach); k <= Math.Min(height - 1, boundary + Reach - 1); k++)
      {
        mask[wordBase + (k >> 6)] |= 1UL << (k & 63);
      }
    }

    private readonly ulong[] _mask;

    /// <summary>
    /// The solid/fluid boundaries, kept apart from <see cref="_mask"/> because only one pass may draw them.
    /// </summary>
    /// <remarks>
    /// Null on the overwhelming majority of chunks, which hold no fluid at all and so have no such boundary.
    /// </remarks>
    private readonly ulong[] _material;

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

    private ChunkBands(
      int size, int height, int words, ulong[] mask, ulong[] material, int minActiveZ, int maxActiveZ)
    {
      Size = size;
      Height = height;
      Words = words;
      _mask = mask;
      _material = material;
      MinActiveZ = minActiveZ;
      MaxActiveZ = maxActiveZ;
    }

    /// <summary>The words for one column, in cell-index order. Occupancy boundaries only.</summary>
    public ReadOnlySpan<ulong> ColumnMask(int localX, int localY) =>
      _mask.AsSpan((localY * Size + localX) * Words, Words);

    /// <summary>
    /// The solid/fluid boundaries of one column, empty where the chunk has none.
    /// </summary>
    /// <remarks>
    /// Separate from <see cref="ColumnMask"/> because <b>the interface between ground and a fluid is drawn once,
    /// by the terrain pass.</b> It bounds both volumes, so drawing it from every pass would have the water and
    /// the lava each emit an underside exactly where the bed they stand on is already being drawn - two
    /// coincident sheets, which reads as a double-blended sheet of water and as z-fighting under an opaque pool.
    ///
    /// <para>
    /// <b>Both sides are material, never air</b>, and that is what lets a fluid pass <i>subtract</i> this rather
    /// than merely not add it - see <see cref="TerrainPatch.ActiveMask"/>, which has to subtract, and
    /// <c>TerrainPatch._activeFluid</c> for why. A fluid/air boundary is the fluid's own top sheet; marking it
    /// here as well would be harmless to a reader that only ever ORs the two masks together and fatal to one
    /// that does not, because the shallows are exactly where the two boundaries fall within a cell of each
    /// other.
    /// </para>
    /// </remarks>
    public ReadOnlySpan<ulong> MaterialColumnMask(int localX, int localY) =>
      _material == null ? default : _material.AsSpan((localY * Size + localX) * Words, Words);

    /// <summary>
    /// Scans a decoded chunk. Pure computation over the block and occupancy arrays; safe on a worker thread.
    /// </summary>
    /// <remarks>
    /// <b>Two walks, because occupancy alone cannot see the bed under a fluid.</b> The materialiser fills
    /// everything below the air interface to full - rock and the water above it alike - so a rock/water boundary
    /// is not a change in occupancy and the first walk runs straight past it. That left every lake bed and sea
    /// floor in the world unmeshed: the terrain pass and the water pass are both gated on this mask, so under
    /// water there was no ground surface and no water underside, only a single transparent sheet over nothing.
    ///
    /// <para>
    /// The second walk is over the blocks, marking a boundary only where the two ids either side belong to
    /// different <see cref="BlockAppearance.SurfaceKind"/>s. Marking every id change instead would mark every
    /// bed in the stratigraphy, none of which any pass can find a crossing at.
    /// </para>
    ///
    /// <para>
    /// Both walks stay run-walked, so the cost still tracks surface area rather than volume. Measured on an
    /// eleven-by-eleven view volume: dry rolling terrain is unchanged at 1.97 ms per chunk against 2.04 before,
    /// and a volume where every chunk is sea bed goes from 0.92 ms to 2.71 ms, drawing 629k triangles where it
    /// drew 258k. The baseline was cheap because it was wrong - it drew no ground at all - and 2.71 ms is about
    /// what ordinary land costs, which is the right price for a surface that is now actually there.
    /// </para>
    /// </remarks>
    public static ChunkBands Of(VoxelChunk chunk, BlockAppearance appearance)
    {
      var size = chunk.Size;
      var height = chunk.Height;
      var words = (height + 63) / 64;

      var mask = new ulong[size * size * words];
      ulong[] material = null;
      var occupancy = chunk.Occupancy.AsSpan();
      var blocks = chunk.Blocks.AsSpan();

      var minActive = int.MaxValue;
      var maxActive = -1;

      for (var column = 0; column < size * size; column++)
      {
        var wordBase = column * words;
        var occupancyStrip = occupancy.Slice(column * height, height);
        var blockStrip = blocks.Slice(column * height, height);

        var z = 0;
        while (z < height)
        {
          // One call per run rather than one comparison per cell. Occupancy is zero exactly where the block is
          // air, so this finds material boundaries and the air interface in the same walk.
          var rest = occupancyStrip.Slice(z).IndexOfAnyExcept(occupancyStrip[z]);
          var next = rest < 0 ? height : z + rest;

          if (z > 0)
          {
            Mark(mask, wordBase, height, z);
          }

          z = next;
        }

        z = 0;
        while (z < height)
        {
          var rest = blockStrip.Slice(z).IndexOfAnyExcept(blockStrip[z]);
          var next = rest < 0 ? height : z + rest;

          // Air is excluded by the occupancy test rather than by an id comparison, and the two are the same
          // test: occupancy is zero exactly where the block is air. An undeclared id reads as Terrain, so air
          // over water would otherwise enter this mask as a ground/fluid boundary and take the sea's own top
          // sheet out with it.
          if (next < height
              && occupancyStrip[z] != 0 && occupancyStrip[next] != 0
              && appearance.SurfaceOf(blockStrip[z]) != appearance.SurfaceOf(blockStrip[next]))
          {
            material ??= new ulong[size * size * words];
            Mark(material, wordBase, height, next);
          }

          z = next;
        }
      }

      MarkHorizontalFaces(occupancy, mask, size, height, words);

      for (var column = 0; column < size * size; column++)
      {
        var wordBase = column * words;

        for (var word = 0; word < words; word++)
        {
          // The union of both masks, because this range sizes the gather that TerrainPatch does and every pass
          // reads out of that one patch. A range covering only the occupancy boundaries would leave the terrain
          // pass unable to reach the sea bed it is now the one drawing.
          var bits = mask[wordBase + word] | (material == null ? 0UL : material[wordBase + word]);
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

      return new ChunkBands(size, height, words, mask, material, maxActive < 0 ? -1 : minActive, maxActive);
    }

    /// <summary>
    /// Marks the cells where two horizontally adjacent columns disagree.
    /// </summary>
    /// <remarks>
    /// <b>A vertical rock face has no vertical run boundary anywhere near it</b>, which is the hole the
    /// per-column pass above cannot see. A solid column beside a carved one changes nothing as you walk either
    /// column downward; what changes is the step *between* them, and the loop above never compares two columns.
    ///
    /// <para>
    /// The symptom is a face that thins to nothing as it grows. A cave passage is marked at its floor and its
    /// ceiling, because those are real run boundaries in the carved column, and <see cref="Reach"/> spreads each
    /// by one cell - so a three-metre passage happens to come out solid and a sixteen-metre gallery renders as a
    /// floor and a ceiling with the wall between them missing. That is not a rendering artefact a player would
    /// read as one: it is a hole through into the void, in exactly the places the world puts galleries and
    /// cliffs.
    /// </para>
    ///
    /// <para>
    /// This is the pass that closes it, and it stays within the cost model the class is built on: it walks
    /// <i>runs</i>, not cells. Two adjacent columns are compared by stepping to whichever has the next boundary,
    /// so an interval where neither changes is settled by one comparison however tall it is. Open terrain
    /// therefore costs a handful of steps per pair and marks the one- or two-cell step between neighbouring
    /// surface heights, which the vertical pass had already marked. A cliff costs the same and marks its whole
    /// face - which is the surface, and has to be meshed.
    /// </para>
    ///
    /// <para>
    /// It also replaces the <c>mixedColumns</c> fallback this method used to end with, which filled the entire
    /// mask when no vertical boundary existed anywhere but the columns disagreed. That was the same failure
    /// caught only in its global form and answered with a sledgehammer; a sheer face inside one chunk is now
    /// marked precisely rather than by meshing all 262 144 cells.
    /// </para>
    /// </remarks>
    private static void MarkHorizontalFaces(
      ReadOnlySpan<byte> occupancy, ulong[] mask, int size, int height, int words)
    {
      for (var y = 0; y < size; y++)
      {
        for (var x = 0; x < size; x++)
        {
          var column = y * size + x;
          var here = occupancy.Slice(column * height, height);

          if (x + 1 < size)
          {
            Compare(here, occupancy.Slice((column + 1) * height, height), mask, size, height, words, x, y, 1, 0);
          }

          if (y + 1 < size)
          {
            Compare(here, occupancy.Slice((column + size) * height, height), mask, size, height, words, x, y, 0, 1);
          }
        }
      }
    }

    /// <summary>
    /// Walks one pair of adjacent columns together, marking every stretch over which they disagree.
    /// </summary>
    /// <remarks>
    /// <b>The stretches where the two agree are skipped by <c>CommonPrefixLength</c></b>, which is vectorised,
    /// and only the stretches where they disagree are walked a byte at a time. That asymmetry is the whole
    /// performance argument, and it is the right way round: two adjacent columns agree over nearly their entire
    /// height - the rock below them both and the air above them both - and disagree only across the step
    /// between their surfaces, which is the face that has to be meshed.
    ///
    /// <para>
    /// A run walk was tried first and is what the vertical pass uses, but it does not survive being done per
    /// <i>pair</i>: there are twice as many pairs as columns and each needs both columns walked, so the same
    /// technique that costs ~30 us over a chunk cost 520 us here, against a 500 us budget. Asking "where do
    /// these two first differ" once per agreeing stretch is a handful of calls per pair instead of a handful
    /// per run.
    /// </para>
    ///
    /// <para>
    /// Static, and everything it needs is a parameter. As a capturing local function this and
    /// <see cref="Mark"/> compile to instance calls on a display class allocated per chunk scan, which is real
    /// cost on a method whose whole justification is being too cheap to think about.
    /// </para>
    /// </remarks>
    private static void Compare(
      ReadOnlySpan<byte> a, ReadOnlySpan<byte> b, ulong[] mask,
      int size, int height, int words, int x, int y, int dx, int dy)
    {
      var z = 0;
      while (z < height)
      {
        z += a.Slice(z).CommonPrefixLength(b.Slice(z));

        if (z >= height)
        {
          return;
        }

        var start = z;
        while (z < height && a[z] != b[z])
        {
          z++;
        }

        Mark(mask, size, height, words, x, y, dx, dy, start, z - 1);
      }
    }

    /// <summary>
    /// Marks the cells that can see a difference between one column pair, and no more of them than that.
    /// </summary>
    /// <remarks>
    /// A cell's corners read one column either side of it, so a cell at <c>cx</c> has both members of the pair
    /// <c>(x, x + dx)</c> among its corners only for <c>cx</c> in <c>{x, x + dx}</c>. Across the pair's own axis
    /// it reaches one row further each way, and in z it spreads by <see cref="Reach"/> - the same spread the
    /// vertical pass applies to a run boundary, for the same reason.
    /// </remarks>
    private static void Mark(
      ulong[] mask, int size, int height, int words, int x, int y, int dx, int dy, int lo, int hi)
    {
      var from = Math.Max(0, lo - Reach);
      var to = Math.Min(height - 1, hi + Reach);

      for (var step = 0; step <= 1; step++)
      {
        var cx = x + step * dx;
        var cy = y + step * dy;

        for (var side = -Reach; side <= Reach; side++)
        {
          var sx = cx + side * dy;
          var sy = cy + side * dx;

          if (sx < 0 || sx >= size || sy < 0 || sy >= size)
          {
            continue;
          }

          var wordBase = (sy * size + sx) * words;
          for (var k = from; k <= to; k++)
          {
            mask[wordBase + (k >> 6)] |= 1UL << (k & 63);
          }
        }
      }
    }

    /// <summary>
    /// Whether the boundary between <paramref name="below"/>'s top cells and <paramref name="chunk"/>'s bottom
    /// cells carries any change in occupancy, or any change of surface.
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
    public static bool SeamAtFloor(VoxelChunk chunk, VoxelChunk below, BlockAppearance appearance)
    {
      if (below == null)
      {
        return false;
      }

      var height = chunk.Height;
      var columns = chunk.Size * chunk.Size;

      for (var column = 0; column < columns; column++)
      {
        var here = column * height;
        var there = column * height + height - 1;

        if (chunk.Occupancy[here] != below.Occupancy[there])
        {
          return true;
        }

        // Surface as well as occupancy, for the reason <see cref="Of"/> walks the blocks: a chunk of water
        // standing on a chunk whose top is rock is full either side, so the seabed at a chunk floor would
        // otherwise be missed even once the interior walk finds every other one.
        if (appearance.SurfaceOf(chunk.Blocks[here]) != appearance.SurfaceOf(below.Blocks[there]))
        {
          return true;
        }
      }

      return false;
    }
  }
}
