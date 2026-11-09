using System;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// Whether one step of a walk is allowed, by the rule the server walks by, so a click is only predicted as far as
  /// the server will let it go.
  /// </summary>
  /// <remarks>
  /// A mirror of the server's <c>WalkableTile</c> and <c>ChunkWalkQuery.canStep</c>, and it has to stay one. A
  /// client that predicts a step the server refuses walks on into the slope or the sea it was refused at, and the
  /// server's answer then has to pull it back - which is the snap this exists to remove. So:
  ///
  /// <list type="bullet">
  /// <item>A <b>floor</b> is a solid voxel with <see cref="AgentHeight"/> voxels above it that are open, or water
  /// adding up to no more than <see cref="MaxWadeDepth"/>. Anything else above it - rock, lava - is no headroom.
  /// Its surface is the voxel's z plus its occupancy, as the server measures it.</item>
  /// <item>A <b>step</b> starts on the floor nearest the walker's z and lands on any floor of the next column whose
  /// surface is within <see cref="MaxStep"/> of it: the server's <c>AgentProfile.forMaxSlope</c> at its default
  /// sixty degrees and one-metre voxels.</item>
  /// </list>
  ///
  /// <para>
  /// <b>Unknown is allowed.</b> The server lets a step through where it holds no walkability for either end, and
  /// so does this where either column is not streamed: refusing ground nobody has looked at would stop every walk
  /// at the edge of the view.
  /// </para>
  /// </remarks>
  public static class WalkRules
  {
    /// <summary>Voxels of headroom a walker needs above a floor: the server's <c>AgentProfile.height</c>.</summary>
    public const int AgentHeight = 2;

    /// <summary>Water a walker may stand in, in voxels: the server's <c>AgentProfile.maxWadeDepth</c>.</summary>
    public const double MaxWadeDepth = 1.0;

    /// <summary>Rise or drop one step may take, in voxels: one voxel times tan(60°), as the server's default.</summary>
    public static readonly double MaxStep = Math.Tan(60.0 * Math.PI / 180.0);

    /// <summary>
    /// Rise or drop of a step onto or off a built cell: the server's <c>AgentProfile.maxStructureStep</c>, plus the one
    /// fill quantum by which a half cell is taller than half.
    /// </summary>
    public const double MaxStructureStep = 0.5 + 1.0 / 255.0;

    /// <summary>How far from the walker's z the floor under it is looked for, in voxels.</summary>
    /// <remarks>The server's <c>standingZ</c> rounds a surface and lifts it to sea level, so the floor under a
    /// standing z can be a few voxels below it in the sea's margin; a step only ever looks within a voxel or two of
    /// that floor.</remarks>
    private const int FootingSearch = 6;

    public enum Step
    {
      /// <summary>The server would take it; the height is the floor it lands on.</summary>
      Allowed,

      /// <summary>The server would refuse it.</summary>
      Refused,

      /// <summary>A column is not held here, so this cannot tell - and the server would let it through.</summary>
      Unknown
    }

    private enum Pass
    {
      Open,
      Swimmable,
      Blocked
    }

    private static readonly bool[] Solid = new bool[256];
    private static readonly Pass[] Passes = BuildPasses();

    private static Pass[] BuildPasses()
    {
      // An id the palette does not declare is not a material the server knows either, and its BlockType.ofOrNull
      // turns that into no headroom - so does this.
      var passes = new Pass[256];
      Array.Fill(passes, Pass.Blocked);
      passes[VoxelChunk.AirBlockId] = Pass.Open;

      foreach (var block in BlockAppearance.Palette)
      {
        Solid[block.Id] = block.Solid;
        passes[block.Id] = block.Solid ? Pass.Blocked
          : block.Surface == BlockAppearance.SurfaceKind.Water ? Pass.Swimmable
          : block.Surface == BlockAppearance.SurfaceKind.Lava ? Pass.Blocked
          : Pass.Open;
      }

      return passes;
    }

    /// <summary>
    /// Whether a walker standing at (<paramref name="fromX"/>, <paramref name="fromY"/>) at height
    /// <paramref name="fromZ"/> may step into the column (<paramref name="toX"/>, <paramref name="toY"/>), and
    /// onto which floor.
    /// </summary>
    /// <param name="landing">The surface the step lands on, in voxels, when it is <see cref="Step.Allowed"/>.</param>
    /// <param name="cells">The building pieces held here, or null for terrain alone.</param>
    /// <remarks>A diagonal also needs both straight steps beside it, as on the server, or it cuts a corner.</remarks>
    public static Step Check(
      IChunkSource source, ChunkWrap wrap, int chunkSize, int chunkHeight,
      long fromX, long fromY, double fromZ, long toX, long toY, out double landing, StructureCells cells = null)
    {
      landing = double.NaN;

      var isDiagonal = fromX != toX && fromY != toY;
      if (isDiagonal)
      {
        var besideX = StraightCheck(source, wrap, chunkSize, chunkHeight, cells, fromX, fromY, fromZ, toX, fromY, out _);
        var besideY = StraightCheck(source, wrap, chunkSize, chunkHeight, cells, fromX, fromY, fromZ, fromX, toY, out _);
        if (besideX == Step.Refused || besideY == Step.Refused)
        {
          return Step.Refused;
        }
      }

      return StraightCheck(source, wrap, chunkSize, chunkHeight, cells, fromX, fromY, fromZ, toX, toY, out landing);
    }

    private static Step StraightCheck(
      IChunkSource source, ChunkWrap wrap, int chunkSize, int chunkHeight, StructureCells cells,
      long fromX, long fromY, double fromZ, long toX, long toY, out double landing)
    {
      landing = double.NaN;

      var origin = NearestFloor(
        source, wrap, chunkSize, chunkHeight, cells, fromX, fromY, fromZ, FootingSearch, out var held, out var fromBuilt);
      if (!held)
      {
        return Step.Unknown;
      }

      // The server finds no footing either and refuses; there is no floor to step off.
      if (double.IsNaN(origin))
      {
        return Step.Refused;
      }

      var crossing = StructurePieces.EdgesCrossedBy(toX - fromX, toY - fromY);
      if ((WallsAround(cells, fromX, fromY, origin) & crossing) != 0)
      {
        return Step.Refused;
      }

      var range = (int)Math.Ceiling(MaxStep) + 1;
      var best = double.NaN;
      var bestRise = double.MaxValue;
      var top = (int)Math.Floor(origin) + range;
      var bottom = (int)Math.Floor(origin) - range;

      for (var z = top; z >= bottom; z--)
      {
        var floor = FloorAt(source, wrap, chunkSize, chunkHeight, cells, toX, toY, z, out var targetHeld, out var toBuilt);
        if (!targetHeld)
        {
          return Step.Unknown;
        }

        if (double.IsNaN(floor))
        {
          continue;
        }

        var rise = Math.Abs(floor - origin);
        var limit = fromBuilt || toBuilt ? Math.Min(MaxStep, MaxStructureStep) : MaxStep;
        if (rise <= limit && rise < bestRise)
        {
          bestRise = rise;
          best = floor;
        }
      }

      if (double.IsNaN(best))
      {
        return Step.Refused;
      }

      if ((WallsAround(cells, toX, toY, best) & StructurePieces.OppositeEdges(crossing)) != 0)
      {
        return Step.Refused;
      }

      landing = best;
      return Step.Allowed;
    }

    /// <summary>
    /// The walls a body standing on <paramref name="surface"/> overlaps, from its feet to the top of its head: the
    /// server's <c>WalkableTile</c> span walls.
    /// </summary>
    private static int WallsAround(StructureCells cells, long x, long y, double surface)
    {
      if (cells == null)
      {
        return 0;
      }

      var feet = (long)Math.Floor(surface);
      var head = (long)Math.Ceiling(surface + AgentHeight) - 1;
      var walls = 0;
      for (var z = feet; z <= head; z++)
      {
        walls |= cells.EdgesAt(x, y, z);
      }

      return walls;
    }

    /// <summary>The floor in a column nearest <paramref name="nearZ"/>, within <paramref name="search"/> voxels.</summary>
    /// <param name="built">Whether that floor is a built cell rather than terrain.</param>
    private static double NearestFloor(
      IChunkSource source, ChunkWrap wrap, int chunkSize, int chunkHeight, StructureCells cells,
      long x, long y, double nearZ, int search, out bool held, out bool built)
    {
      held = true;
      built = false;
      var centre = (int)Math.Round(nearZ);
      var best = double.NaN;
      var bestGap = double.MaxValue;

      for (var z = centre + search; z >= centre - search; z--)
      {
        var floor = FloorAt(source, wrap, chunkSize, chunkHeight, cells, x, y, z, out held, out var floorBuilt);
        if (!held)
        {
          return double.NaN;
        }

        if (double.IsNaN(floor))
        {
          continue;
        }

        var gap = Math.Abs(floor - nearZ);
        if (gap < bestGap)
        {
          bestGap = gap;
          best = floor;
          built = floorBuilt;
        }
      }

      return best;
    }

    /// <summary>
    /// The surface of the floor at voxel (<paramref name="x"/>, <paramref name="y"/>, <paramref name="z"/>), or NaN
    /// when that voxel is not one. <paramref name="held"/> is false when a chunk the answer needs is not streamed.
    /// </summary>
    /// <remarks>A built cell is a floor and no headroom, and wins over the voxel it sits in, as on the server.</remarks>
    private static double FloorAt(
      IChunkSource source, ChunkWrap wrap, int chunkSize, int chunkHeight, StructureCells cells,
      long x, long y, int z, out bool held, out bool built)
    {
      held = true;

      var block = BlockAt(source, wrap, chunkSize, chunkHeight, x, y, z, out var occupancy);
      if (block < 0)
      {
        held = false;
        built = false;
        return double.NaN;
      }

      var builtFill = cells?.FillAt(x, y, z) ?? 0;
      built = builtFill > 0;
      if (!built && !Solid[block])
      {
        return double.NaN;
      }

      var wade = 0.0;
      for (var above = z + 1; above <= z + AgentHeight; above++)
      {
        var headroom = BlockAt(source, wrap, chunkSize, chunkHeight, x, y, above, out var fill);
        if (headroom < 0)
        {
          held = false;
          return double.NaN;
        }

        if ((cells?.FillAt(x, y, above) ?? 0) > 0)
        {
          return double.NaN;
        }

        switch (Passes[headroom])
        {
          case Pass.Open:
            break;
          case Pass.Swimmable:
            wade += fill / 255.0;
            if (wade > MaxWadeDepth)
            {
              return double.NaN;
            }

            break;
          default:
            return double.NaN;
        }
      }

      return z + (built ? builtFill : occupancy) / 255.0;
    }

    /// <summary>The block id at a global voxel, or -1 when its chunk is not held.</summary>
    private static int BlockAt(
      IChunkSource source, ChunkWrap wrap, int chunkSize, int chunkHeight, long x, long y, int z, out int occupancy)
    {
      occupancy = 0;

      var chunkX = FloorDiv(x, chunkSize);
      var chunkY = FloorDiv(y, chunkSize);
      var chunkZ = FloorDiv(z, chunkHeight);
      var chunk = source.Get(wrap.Normalise(new ChunkKey((int)chunkX, (int)chunkY, (int)chunkZ)));
      if (chunk == null)
      {
        return -1;
      }

      var localX = (int)(x - chunkX * chunkSize);
      var localY = (int)(y - chunkY * chunkSize);
      var localZ = (int)(z - chunkZ * chunkHeight);
      occupancy = chunk.OccupancyAt(localX, localY, localZ);
      return chunk.BlockAt(localX, localY, localZ);
    }

    private static long FloorDiv(long value, long divisor)
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
