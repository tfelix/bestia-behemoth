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
  ///
  /// <para><b>Vertically as well as horizontally, and that is not symmetry for its own sake.</b> A chunk draws
  /// the surface at its own floor, so the slab below is a genuine input rather than a nicety - and over open sea
  /// it is the <i>only</i> input, because uniform water under uniform air has no interior boundary for either
  /// slab to find alone. An absent slab below that went unrecorded is therefore not a flat seam that nobody
  /// notices; it is a chunk of missing ocean that never repairs itself, because nothing tells the renderer to
  /// look again when the water arrives.
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

    /// <summary>
    /// The occupancy boundaries with the solid/fluid ones folded in, which only the terrain pass draws.
    /// </summary>
    /// <remarks>
    /// Merged once here rather than per column in the mesher's own loop, and aliased to the plain occupancy
    /// mask outright on a patch with no such boundary - which is most of them. See
    /// <see cref="ChunkBands.MaterialColumnMask"/> for why the two kinds are kept apart at all.
    /// </remarks>
    private readonly ulong[] _activeTerrain;

    /// <summary>The plain occupancy boundaries, for the fluid passes.</summary>
    /// <remarks>
    /// <b>This used to be the occupancy mask with the dilated solid/fluid boundaries subtracted, and the
    /// history is worth keeping because the reasoning was half right.</b> The problem it attacked is real: the
    /// ground is masked out of the fluid field, so the bed under a fluid is a genuine sign change for the fluid
    /// pass too, and the water was given an underside exactly on top of the sea floor the terrain pass had just
    /// drawn - two coincident sheets, a doubly blended sea z-fighting the opaque bed, triangulated differently
    /// because they came from different cells. That is what the triangles along a shoreline were.
    ///
    /// <para>
    /// But a mask decides which cells a pass <i>visits</i>, and that is a blunter instrument than the question
    /// being asked. Where the water is a voxel or two deep the cell carrying its own top sheet <i>is</i> the
    /// cell carrying the bed, so subtracting the bed removed the surface as well and the waterline receded in a
    /// ragged one-voxel step - and <see cref="SurfaceNets"/> silently drops any quad with a missing corner, so
    /// it did so without complaint. The question belongs on the lattice edge rather than on the cell, and that
    /// is where it is now asked: a crossing is a free surface only if the empty side is empty of the ground
    /// too. See <see cref="BlockAppearance.BackedMaskOf"/>.
    /// </para>
    /// </remarks>
    private readonly ulong[] _activeFluid;

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
      byte[] blocks, byte[] occupancy, ulong[] activeTerrain, ulong[] activeFluid,
      ChunkKey[] missingNeighbours)
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
      _activeTerrain = activeTerrain;
      _activeFluid = activeFluid;
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
        if (_activeTerrain[column + word] != 0)
        {
          return true;
        }
      }

      return false;
    }

    /// <summary>The active bits of one padded column, indexed by patch z.</summary>
    /// <param name="drawsTheBed">
    /// Whether this pass is the one that draws the solid/fluid interface. True for the terrain pass, which has
    /// those cells added; false for the fluid passes, which have them removed - see <see cref="_activeFluid"/>
    /// for why removing them is not the same as never adding them.
    /// </param>
    public ReadOnlySpan<ulong> ActiveMask(int localX, int localY, bool drawsTheBed) =>
      (drawsTheBed ? _activeTerrain : _activeFluid)
        .AsSpan(((localY + ApronLow) * Width + (localX + ApronLow)) * Words, Words);

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
    ///
    /// <para>
    /// <b><c>null</c> means "no surface given what is held", not "no surface".</b> When the slab below has not
    /// arrived the answer can flip once it does, so the caller has to record that dependency rather than take
    /// the emptiness as settled - see <see cref="SurfaceNets.Build"/>, which is where that debt is raised.
    /// </para>
    /// </remarks>
    public static TerrainPatch Gather(
      IChunkSource source, ChunkKey key, ChunkWrap wrap, BlockAppearance appearance)
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
      var seamAtFloor = ChunkBands.SeamAtFloor(chunk, below, appearance);

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
      // bands can widen the range this chunk has to gather. Only the eight horizontal neighbours here: a vertical
      // one contributes through the floor seam above instead. That is about the *range* to gather - the vertical
      // neighbours still have to be reported as dependencies, which GatherStrip does as it crosses into them.
      for (var dy = -1; dy <= 1; dy++)
      {
        for (var dx = -1; dx <= 1; dx++)
        {
          if (dx == 0 && dy == 0)
          {
            continue;
          }

          var neighbour = source.BandsOf(wrap.Normalise(new ChunkKey(key.X + dx, key.Y + dy, key.Z)));
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

      return Build(source, key, wrap, appearance, size, height, quadZLo, quadZHi, seamAtFloor);
    }

    private static TerrainPatch Build(
      IChunkSource source, ChunkKey key, ChunkWrap wrap, BlockAppearance appearance,
      int size, int height, int quadZLo, int quadZHi, bool seamAtFloor)
    {
      var width = size + ApronLow + ApronHigh;
      var depth = (quadZHi - quadZLo + 1) + ApronLow + ApronHigh;
      var words = (depth + 63) / 64;

      var blocks = new byte[width * width * depth];
      var occupancy = new byte[width * width * depth];
      var raw = new ulong[width * width * words];
      var rawMaterial = new ulong[width * width * words];

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

          // Only the address is folded across the seam, never the offset within it. localX above is taken
          // from the unwrapped division and is the same either way round, because a wrapped axis is a whole
          // number of chunks wide - so folding after taking the offset keeps the two consistent. Recording
          // the folded address matters as much as looking it up: MissingNeighbours is compared against the
          // keys the server sends, which are already canonical.
          var neighbour = wrap.Normalise(new ChunkKey(chunkX, chunkY, key.Z));
          chunkX = neighbour.X;
          chunkY = neighbour.Y;

          // A horizontal neighbour that is not held at all: read this chunk's own edge column instead, which
          // continues the terrain flat rather than cutting it off with a cliff into nothing.
          if ((chunkX != key.X || chunkY != key.Y) && source.Get(neighbour) == null)
          {
            if (!missing.Contains(neighbour))
            {
              missing.Add(neighbour);
            }

            chunkX = key.X;
            chunkY = key.Y;
            localX = Math.Clamp(px - ApronLow, 0, size - 1);
            localY = Math.Clamp(py - ApronLow, 0, size - 1);
          }

          var stripBase = (py * width + px) * depth;

          GatherStrip(
            source, chunkX, chunkY, localX, localY, height, baseVoxelZ, depth,
            blocks.AsSpan(stripBase, depth), occupancy.AsSpan(stripBase, depth), missing);

          GatherMask(
            source, chunkX, chunkY, localX, localY, key.Z, height,
            quadZLo, depth, seamAtFloor,
            raw.AsSpan((py * width + px) * words, words),
            rawMaterial.AsSpan((py * width + px) * words, words));
        }
      }

      MarkLateralMaterialFaces(blocks, occupancy, rawMaterial, appearance, width, depth, words);

      var active = Dilate(raw, width, words);
      var (activeTerrain, activeFluid) = SplitMaterial(active, rawMaterial, width, words);

      return new TerrainPatch(
        key, size, height, width, depth, words, quadZLo, quadZHi,
        blocks, occupancy, activeTerrain, activeFluid, missing.ToArray());
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
      Span<byte> blocks, Span<byte> occupancy, List<ChunkKey> missing)
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

        var vertical = new ChunkKey(chunkX, chunkY, chunkZ);
        var held = source.Get(vertical);

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
        else if (!missing.Contains(vertical))
        {
          // The strip crossed into a slab that is not held, and the extension at the end of this method will
          // paper over the gap. That makes the patch provisional in exactly the way an absent horizontal
          // neighbour does, and it has to be said out loud for the same reason - otherwise the guess is never
          // revisited. It is not a rare case: a surface on a chunk floor is drawn from the two slabs together,
          // and the sea is uniform water under uniform air, so the waterline is nothing *but* this case.
          missing.Add(vertical);
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
      int chunkZ, int height, int quadZLo, int depth, bool seamAtFloor,
      Span<ulong> mask, Span<ulong> material)
    {
      var bands = source.BandsOf(new ChunkKey(chunkX, chunkY, chunkZ));
      var columnMask = bands == null ? default : bands.ColumnMask(localX, localY);
      var materialMask = bands == null ? default : bands.MaterialColumnMask(localX, localY);

      for (var patchZ = 0; patchZ < depth; patchZ++)
      {
        var localZ = patchZ + quadZLo - ApronLow;

        // Outside the chunk, or with nothing held, every cell is active: the patch cannot rule a crossing out
        // where it has no scan to consult.
        var unknown = localZ < 0 || localZ >= height || bands == null;

        var active = unknown
                     || (seamAtFloor && localZ == 0)
                     || (columnMask[localZ >> 6] & (1UL << (localZ & 63))) != 0;

        if (active)
        {
          mask[patchZ >> 6] |= 1UL << (patchZ & 63);
        }

        if (!unknown && !materialMask.IsEmpty
                     && (materialMask[localZ >> 6] & (1UL << (localZ & 63))) != 0)
        {
          material[patchZ >> 6] |= 1UL << (patchZ & 63);
        }
      }
    }

    /// <summary>
    /// Records every cell that meets a different surface kind sideways, so the terrain pass can reach it.
    /// </summary>
    /// <remarks>
    /// <b>The submerged bank was never meshed at all, and this is where that is fixed.</b>
    /// <see cref="ChunkBands"/> finds material boundaries by walking each column vertically, so for a river it
    /// records the bed and the waterline and nothing in between; its horizontal pass compares <i>occupancy</i>,
    /// and the materialiser fills ground and fluid alike to 255, so two columns either side of a bank agree
    /// over the whole submerged span and disagree only above the waterline. The cells between therefore
    /// entered neither mask, the terrain pass never visited them, and the only geometry standing at a river's
    /// edge was the water's own side wall - with nothing opaque behind it, which is why the depth fade read it
    /// as a hard dark slab hanging in the air.
    ///
    /// <para>
    /// Done here rather than in <see cref="ChunkBands"/>, which is where it would otherwise belong. Bands see
    /// one chunk: their loops stop at <c>x + 1 &lt; size</c> and the class says outright that only boundaries
    /// inside the chunk are recorded. A lateral interface falling on a chunk's own edge - water in one, rock in
    /// the next - would be recorded by neither, and unlike the occupancy case there is no vertical run boundary
    /// at those z to cover for it. That is a one-cell gap in the bank every 32 m. A patch has already gathered
    /// its neighbours into its apron by the time this runs, so here the interface is simply visible.
    /// </para>
    ///
    /// <para>
    /// A missing neighbour invents nothing: the gather clamps the apron column to this chunk's own edge column
    /// and extends the outermost known cell vertically, so an apron cell holds the same block id as the cell it
    /// mirrors and the comparison finds no difference. The same argument the occupancy mask already rests on.
    /// </para>
    ///
    /// <para>
    /// Only the near cell of each pair is marked, and only <c>pz ± 1</c> around it: <see cref="Dilate"/> spreads
    /// every column's bits into its whole 3x3 horizontal neighbourhood afterwards, which covers the far cell and
    /// the perpendicular row for free.
    /// </para>
    /// </remarks>
    private static void MarkLateralMaterialFaces(
      byte[] blocks, byte[] occupancy, ulong[] material, BlockAppearance appearance,
      int width, int depth, int words)
    {
      for (var py = 0; py < width; py++)
      {
        for (var px = 0; px < width; px++)
        {
          var column = py * width + px;
          var cellBase = column * depth;
          var wordBase = column * words;
          var east = px + 1 < width ? (column + 1) * depth : -1;
          var north = py + 1 < width ? (column + width) * depth : -1;

          if (east < 0 && north < 0)
          {
            continue;
          }

          for (var pz = 0; pz < depth; pz++)
          {
            var here = cellBase + pz;
            if (occupancy[here] == 0)
            {
              continue;
            }

            var mine = appearance.SurfaceOf(blocks[here]);

            if (Differs(blocks, occupancy, appearance, east, pz, mine)
                || Differs(blocks, occupancy, appearance, north, pz, mine))
            {
              MarkAround(material, wordBase, depth, pz);
            }
          }
        }
      }
    }

    /// <summary>
    /// Whether the cell at <paramref name="stripBase"/> holds material of a different surface from
    /// <paramref name="mine"/>.
    /// </summary>
    /// <remarks>
    /// Both sides must hold material. A material/air step is the fluid's own free face and belongs in the
    /// occupancy mask, which already carries it; folding it in here would take the sea's own top sheet out.
    /// </remarks>
    private static bool Differs(
      byte[] blocks, byte[] occupancy, BlockAppearance appearance,
      int stripBase, int pz, BlockAppearance.SurfaceKind mine)
    {
      if (stripBase < 0)
      {
        return false;
      }

      var there = stripBase + pz;
      return occupancy[there] != 0 && appearance.SurfaceOf(blocks[there]) != mine;
    }

    /// <summary>Sets a column's bit and the one either side of it, mirroring <c>ChunkBands.Reach</c>.</summary>
    private static void MarkAround(ulong[] material, int wordBase, int depth, int pz)
    {
      var lo = Math.Max(0, pz - 1);
      var hi = Math.Min(depth - 1, pz + 1);

      for (var z = lo; z <= hi; z++)
      {
        material[wordBase + (z >> 6)] |= 1UL << (z & 63);
      }
    }

    /// <summary>
    /// <paramref name="active"/> with the dilated solid/fluid boundaries added, and with them removed.
    /// </summary>
    /// <remarks>
    /// Dilated once and spent twice, because the two kinds of pass want the same cells with opposite signs. The
    /// aliasing when a patch holds no such boundary is worth keeping rather than tidying into the loop: most of
    /// the world is dry, and a dry patch then allocates nothing here at all.
    /// </remarks>
    private static (ulong[] Terrain, ulong[] Fluid) SplitMaterial(
      ulong[] active, ulong[] rawMaterial, int width, int words)
    {
      var any = false;
      foreach (var word in rawMaterial)
      {
        if (word == 0)
        {
          continue;
        }

        any = true;
        break;
      }

      if (!any)
      {
        return (active, active);
      }

      var dilated = Dilate(rawMaterial, width, words);
      var terrain = new ulong[active.Length];

      for (var i = 0; i < terrain.Length; i++)
      {
        terrain[i] = active[i] | dilated[i];
      }

      // The fluid pass keeps the plain occupancy mask. The subtraction that used to happen here is gone: it
      // was a 3x3x3-granular stand-in for "this crossing is not a free surface", and a mask cannot express
      // that, because at a shoreline the cell carrying the water's own top sheet is the *same cell* as the one
      // carrying the bed - so removing the bed took the sheet with it and the waterline receded in a ragged
      // one-voxel step. `SurfaceNets` now asks the question exactly, per lattice edge, against a field that
      // holds this fluid plus the ground. See `BlockAppearance.BackedMaskOf`.
      return (terrain, active);
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
