namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The world geometry this client is built against, and the slope it will draw as cliff.
  /// </summary>
  /// <remarks>
  /// These arrived on <c>WorldInfoSMSG</c> until they did not: eight fields describing a shape that has never
  /// been anything else. Every one is a per-world setting the server <i>could</i> vary and never has - only
  /// <c>worldgen</c>'s own unit tests build a chunk that is not 32x32x256, and nothing in the repository has
  /// ever built a voxel that is not a metre.
  ///
  /// <para>
  /// Compiled in rather than sent, on the same trade <see cref="Mesh.BlockAppearance"/> already made for the
  /// block palette: a value the client cannot adapt to at runtime buys nothing by arriving at runtime. What it
  /// costs is that a server could now disagree, so the server refuses to boot a world whose geometry is not
  /// this one - see <c>ClientWorldContract</c>, which holds the same numbers and is what that check reads.
  /// </para>
  ///
  /// <para>
  /// The exception is the calendar, which stays on the wire deliberately. A date is a thing a player reads and
  /// believes, so a stale copy of it here would show a plausible wrong date rather than fail where anyone can
  /// see it. Geometry has no such failure mode: get it wrong and terrain lands in the wrong place, which the
  /// chunk-header check in <see cref="RleCodec"/> turns into a refusal.
  /// </para>
  ///
  /// <para>
  /// <b>Must equal <c>ClientWorldContract</c> in <c>zone-server</c>.</b> The two move in the same commit, and
  /// the server's <c>ClientWorldContractTest</c> fails the build naming this file when they drift.
  /// </para>
  /// </remarks>
  public static class WorldLayout
  {
    /// <summary>Voxels per chunk edge.</summary>
    public const int ChunkSize = 32;

    /// <summary>Voxels per chunk column, so one chunk spans 256 m of elevation.</summary>
    public const int ChunkHeight = 256;

    /// <summary>
    /// Edge length of one voxel in metres.
    /// </summary>
    /// <remarks>
    /// One, which is why a server position unit and a voxel index are the same number - see the server's
    /// <c>ChunkCoords.VOXELS_PER_POSITION_UNIT</c>. Entity positions are not scaled by this, so a world that
    /// changed it would put entities and terrain in different places.
    /// </remarks>
    public const double VoxelSizeMetres = 1.0;

    /// <summary>
    /// Edge length of one world-tier raster cell in metres, which the world's extent is quoted in.
    /// </summary>
    /// <remarks>
    /// A kilometre, so the width the server sends in cells is a width in kilometres. This is the heightfield's
    /// resolution and has nothing to do with a voxel or a chunk: a cell is 1000 voxels and 31.25 chunks across.
    /// Only <see cref="ChunkWrap"/> reads it, to turn the world's extent into a chunk count.
    /// </remarks>
    public const double CellSizeMetres = 1000.0;

    /// <summary>Whether the world has an eastern seam rather than an eastern edge.</summary>
    public const bool WrapX = true;

    /// <summary>Whether the world has a northern seam rather than a northern edge.</summary>
    public const bool WrapY = true;

    /// <summary>
    /// The steepest ground a player can walk on, in degrees, and therefore the angle drawn as bare rock.
    /// </summary>
    /// <remarks>
    /// The server refuses a step whose rise exceeds <c>tan</c> of this over one voxel of run, and it refuses it
    /// silently - a click into a hillside simply stops short. So this is also what
    /// <see cref="TerrainMaterials.SetWalkableSlope"/> and <see cref="TerrainGrass.MinUpright"/> derive their
    /// thresholds from: the point cover gives way to rock is the point walking stops, and a player who can see
    /// one knows the other.
    /// </remarks>
    public const double MaxWalkSlopeDegrees = 60.0;
  }
}
