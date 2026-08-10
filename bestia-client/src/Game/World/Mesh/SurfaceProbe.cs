using System;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// Where the terrain surface is, according to the same reconstruction <see cref="SurfaceNets"/> draws it with.
  /// </summary>
  /// <remarks>
  /// This exists because an entity's height on the wire is a whole voxel and the ground under it is not. The
  /// server rounds a sub-voxel elevation to the nearest integer <c>z</c> (<c>ChunkCoords.standingZ</c>), which is
  /// as close as an integer can get and still leaves half a metre of daylight under a model's feet. Meshing the
  /// terrain a second way to close that gap would be the wrong fix twice over: the drawn surface is not the
  /// generator's analytic height either, because corner averaging box-blurs it, so agreeing with the generator
  /// would still not agree with the triangle the player is looking at.
  ///
  /// <para>
  /// So this reads the field the mesher reads, with the mesher's own isolevel, and inverts the same linear
  /// interpolation. A corner is the mean of the eight cells meeting at it, exactly as
  /// <see cref="SurfaceNets"/> documents; a surface written at 40.3 m gives corner values 0.65 at lattice 40 and
  /// 0.15 at lattice 41, and <c>40 + (0.65-0.5)/(0.65-0.15)</c> is 40.3 again. What comes back is the height the
  /// mesh has at that spot, not the height the world was generated at, and for standing something on the ground
  /// that is the number that matters.
  /// </para>
  ///
  /// <para>
  /// <b>The search is local by design.</b> It walks down from a caller-supplied hint rather than from the top of
  /// the column, which is what makes it cheap - a handful of byte reads instead of a 256-voxel scan - but also
  /// what makes it correct under a bridge deck or inside a cave. The surface you are standing on is the one near
  /// your feet, not the highest one in the world above you. An entity further than <see cref="SearchVoxels"/>
  /// from any surface gets <c>NaN</c> and should be left where the server put it.
  /// </para>
  ///
  /// <para>
  /// <b>Terrain only.</b> Occupancy is masked to the terrain surface the same way the mesher masks it, so a
  /// bestia wading through a shallow lake stands on the lake bed rather than on the water sheet.
  /// </para>
  ///
  /// <para>
  /// Reads chunks without locking, on the contract <see cref="ClientChunkStore"/> already sets out: byte reads do
  /// not tear, so the worst a probe racing a patch can see is one frame of the old ground.
  /// </para>
  /// </remarks>
  public static class SurfaceProbe
  {
    /// <summary>How far above and below the hint a crossing is looked for, in voxels.</summary>
    /// <remarks>
    /// Four covers the half-voxel the server's rounding can be out by, plus the centimetres corner averaging
    /// moves a slope by, plus enough slack for a step the client has not been told about yet. Much wider and a
    /// swimming or falling entity would be yanked down onto ground it is nowhere near; much narrower and a probe
    /// would start failing on ordinary rough terrain.
    /// </remarks>
    public const int SearchVoxels = 4;

    /// <summary>
    /// Voxel-space z of the drawn terrain surface at (<paramref name="voxelX"/>, <paramref name="voxelY"/>).
    /// </summary>
    /// <remarks>
    /// Bilinear across the four lattice lines around the point, because between them is where the mesh's own
    /// triangles are. Lattice lines carrying no weight are not sampled at all, which is not just an optimisation:
    /// an entity standing on exact integer coordinates - which is every entity that is not mid-step - sits
    /// exactly on a lattice line, and requiring its neighbours to have a surface too would fail every probe taken
    /// on the lip of a cliff.
    /// </remarks>
    /// <param name="source">The chunks held.</param>
    /// <param name="appearance">The palette, for the terrain include mask.</param>
    /// <param name="voxelX">Position along the server's x axis, in voxels.</param>
    /// <param name="voxelY">Position along the server's y axis, in voxels.</param>
    /// <param name="nearZ">Roughly where the surface is expected, in voxels. The server's height, normally.</param>
    /// <param name="chunkSize">Voxels per horizontal chunk axis.</param>
    /// <param name="chunkHeight">Voxels per vertical chunk axis.</param>
    /// <returns>The surface z, or <c>double.NaN</c> if a needed chunk is not held or nothing was found near
    /// <paramref name="nearZ"/>.</returns>
    public static double SurfaceAt(
      IChunkSource source, BlockAppearance appearance,
      double voxelX, double voxelY, double nearZ, int chunkSize, int chunkHeight)
    {
      // Finiteness is checked rather than assumed because the coordinates come in from a caller's scene position,
      // and a NaN reaching Math.Floor would be cast to an integer lattice index rather than rejected.
      if (source == null || appearance == null || chunkSize <= 0 || chunkHeight <= 0 ||
          !double.IsFinite(voxelX) || !double.IsFinite(voxelY) || !double.IsFinite(nearZ))
      {
        return double.NaN;
      }

      var mask = appearance.MaskOf(BlockAppearance.SurfaceKind.Terrain);

      // Cell n spans [n, n+1], so lattice point i sits at voxel coordinate i exactly - see the vertex positions
      // SurfaceNets emits, which add a [0,1] offset to the cell index.
      var latticeX = (int)Math.Floor(voxelX);
      var latticeY = (int)Math.Floor(voxelY);
      var tx = voxelX - latticeX;
      var ty = voxelY - latticeY;

      var cursor = new Cursor();

      var height = 0.0;
      var total = 0.0;

      for (var corner = 0; corner < 4; corner++)
      {
        var dx = corner & 1;
        var dy = corner >> 1;
        var weight = (dx == 0 ? 1.0 - tx : tx) * (dy == 0 ? 1.0 - ty : ty);

        if (weight <= 1e-9)
        {
          continue;
        }

        var sample = LatticeSurfaceAt(
          source, mask, latticeX + dx, latticeY + dy, nearZ, chunkSize, chunkHeight, ref cursor);

        if (double.IsNaN(sample))
        {
          return double.NaN;
        }

        height += sample * weight;
        total += weight;
      }

      return total <= 0.0 ? double.NaN : height / total;
    }

    /// <summary>
    /// Where the surface crosses the vertical lattice line at (<paramref name="latticeX"/>,
    /// <paramref name="latticeY"/>), searching downward from <paramref name="nearZ"/>.
    /// </summary>
    /// <remarks>
    /// Downward rather than upward so that an entity standing on the roof of something gets the roof. The first
    /// crossing at or below the hint is the ground it is on.
    /// </remarks>
    private static double LatticeSurfaceAt(
      IChunkSource source, byte[] mask, int latticeX, int latticeY, double nearZ,
      int chunkSize, int chunkHeight, ref Cursor cursor)
    {
      var top = (int)Math.Floor(nearZ) + SearchVoxels;
      var bottom = (int)Math.Floor(nearZ) - SearchVoxels;

      // A corner pairs the cell layer below it with the one at it, so walking down reuses half of every read.
      // Two layers are primed rather than one, so the corner just above the search window is known and the
      // topmost level in it can be tested like any other instead of being silently skipped.
      var upper = LayerAt(source, mask, latticeX, latticeY, top + 1, chunkSize, chunkHeight, ref cursor);
      var here = LayerAt(source, mask, latticeX, latticeY, top, chunkSize, chunkHeight, ref cursor);

      if (upper < 0 || here < 0)
      {
        return double.NaN;
      }

      var above = (here + upper) / (8.0 * 255.0);
      upper = here;

      for (var latticeZ = top; latticeZ >= bottom; latticeZ--)
      {
        var lower = LayerAt(source, mask, latticeX, latticeY, latticeZ - 1, chunkSize, chunkHeight, ref cursor);
        if (lower < 0)
        {
          return double.NaN;
        }

        var corner = (lower + upper) / (8.0 * 255.0);

        // The inside test is >= Iso, matching SurfaceNets, so a tie breaks toward solid on both sides.
        if (corner >= SurfaceNets.Iso && above < SurfaceNets.Iso)
        {
          return latticeZ + (corner - SurfaceNets.Iso) / (corner - above);
        }

        above = corner;
        upper = lower;
      }

      return double.NaN;
    }

    /// <summary>
    /// Masked occupancy summed over the four cells of one lattice line's horizontal neighbourhood at
    /// <paramref name="cellZ"/>, or -1 if any of them is in a chunk that is not held.
    /// </summary>
    private static int LayerAt(
      IChunkSource source, byte[] mask, int latticeX, int latticeY, int cellZ,
      int chunkSize, int chunkHeight, ref Cursor cursor)
    {
      var sum = 0;

      for (var dy = -1; dy <= 0; dy++)
      {
        for (var dx = -1; dx <= 0; dx++)
        {
          var cell = CellAt(source, mask, latticeX + dx, latticeY + dy, cellZ, chunkSize, chunkHeight, ref cursor);
          if (cell < 0)
          {
            return -1;
          }

          sum += cell;
        }
      }

      return sum;
    }

    /// <summary>One cell's occupancy, masked to the terrain surface, or -1 if its chunk is not held.</summary>
    private static int CellAt(
      IChunkSource source, byte[] mask, int voxelX, int voxelY, int voxelZ,
      int chunkSize, int chunkHeight, ref Cursor cursor)
    {
      var chunkX = FloorDiv(voxelX, chunkSize);
      var chunkY = FloorDiv(voxelY, chunkSize);
      var chunkZ = FloorDiv(voxelZ, chunkHeight);

      var chunk = cursor.Get(source, new ChunkKey(chunkX, chunkY, chunkZ));
      if (chunk == null)
      {
        return -1;
      }

      var index = chunk.Index(
        voxelX - chunkX * chunkSize,
        voxelY - chunkY * chunkSize,
        voxelZ - chunkZ * chunkHeight);

      return chunk.Occupancy[index] & mask[chunk.Blocks[index]];
    }

    /// <summary>
    /// Remembers the last chunk looked up, because a probe reads dozens of cells and nearly all of them are in
    /// the same one.
    /// </summary>
    /// <remarks>
    /// A struct passed by reference rather than a field, so the probe stays static and thread-safe: the terrain
    /// is read from the main thread here and from the mesher's workers elsewhere, and a shared cache would be a
    /// race for the sake of saving a dictionary lookup. Holding a miss as well as a hit matters - an entity at
    /// the edge of the streamed disc probes the same absent chunk four times per lattice line.
    /// </remarks>
    private struct Cursor
    {
      private ChunkKey _key;
      private VoxelChunk _chunk;
      private bool _valid;

      internal VoxelChunk Get(IChunkSource source, ChunkKey key)
      {
        if (_valid && _key.Equals(key))
        {
          return _chunk;
        }

        _key = key;
        _chunk = source.Get(key);
        _valid = true;

        return _chunk;
      }
    }

    private static int FloorDiv(int value, int divisor)
    {
      var quotient = value / divisor;

      if (value % divisor != 0 && (value < 0) != (divisor < 0))
      {
        quotient--;
      }

      return quotient;
    }
  }
}
