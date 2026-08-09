using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// The world's shape and identity. Arrives once, right after authentication, before any chunk.
  /// </summary>
  /// <remarks>
  /// Needed before a chunk payload means anything: a position cannot become a chunk address without
  /// <see cref="ChunkSize"/>, and a voxel index cannot become an elevation without
  /// <see cref="VoxelSizeMetres"/> plus the fact that index zero is sea level.
  ///
  /// <para>
  /// There is no seed here and there should not be. This client does not generate base terrain, so it has no
  /// use for one, and a seed is exactly what would let it precompute every ore deposit in the world.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class WorldInfoSMSG : MapSMSG
  {
    [Export] public string Name { get; set; } = "";

    [Export] public int WidthCells { get; set; }
    [Export] public int HeightCells { get; set; }
    [Export] public double CellSizeMetres { get; set; }

    [Export] public int ChunkSize { get; set; }
    [Export] public int ChunkHeight { get; set; }
    [Export] public double VoxelSizeMetres { get; set; }
    [Export] public double SeaLevelMetres { get; set; }

    [Export] public bool WrapX { get; set; }
    [Export] public bool WrapY { get; set; }

    /// <summary>
    /// The chunk encoding and block palette this server speaks, as one number.
    /// </summary>
    /// <remarks>
    /// Compared against <c>ChunkEngine.Version</c>. The server tracks the encoding, the palette and the
    /// generation pipeline separately because it has to invalidate them separately; this client either reads
    /// what arrives or must be updated, so it is told the one thing it can act on.
    /// </remarks>
    [Export] public uint ChunkEngineVersion { get; set; }

    [Export] public int ViewRadiusChunks { get; set; }

    /// <summary>
    /// Bestia-seconds elapsed since the world began, as of the moment this message was built.
    /// </summary>
    /// <remarks>
    /// An anchor rather than a reading. <c>WorldClock</c> runs it forward at <see cref="TimeSpeedFactor"/>,
    /// so the HUD ticks smoothly off one message per connection instead of one per second per player.
    ///
    /// <para>
    /// Elapsed time and not a wall-clock instant, deliberately: nothing here consults the local clock, so a
    /// machine whose date is wrong still shows the same in-game date as everyone else.
    /// </para>
    /// </remarks>
    [Export] public double WorldAgeBestiaSeconds { get; set; }

    /// <summary>How many times faster Bestia time runs than real time.</summary>
    [Export] public double TimeSpeedFactor { get; set; }

    /// <summary>
    /// What the calendar rolls over at.
    /// </summary>
    /// <remarks>
    /// Sent rather than compiled in, for the reason the block palette's ordinals are a stated wire format: a
    /// client carrying its own copy of these would go on displaying a plausible wrong date after the server's
    /// changed, which is the failure nobody reports.
    /// </remarks>
    [Export] public int HoursPerDay { get; set; }

    [Export] public int DaysPerMonth { get; set; }
    [Export] public int MonthsPerYear { get; set; }

    /// <summary>Bestia-hours of night at the start of each day: hours <c>[0, NightHours)</c> are dark.</summary>
    [Export] public int NightHours { get; set; }

    public static WorldInfoSMSG FromProto(global::Bnet.WorldInfoSMSG proto)
    {
      return new WorldInfoSMSG
      {
        WorldAgeBestiaSeconds = proto.WorldAgeBestiaSeconds,
        TimeSpeedFactor = proto.TimeSpeedFactor,
        HoursPerDay = proto.HoursPerDay,
        DaysPerMonth = proto.DaysPerMonth,
        MonthsPerYear = proto.MonthsPerYear,
        NightHours = proto.NightHours,
        Name = proto.Name,
        WidthCells = proto.WidthCells,
        HeightCells = proto.HeightCells,
        CellSizeMetres = proto.CellSizeMetres,
        ChunkSize = proto.ChunkSize,
        ChunkHeight = proto.ChunkHeight,
        VoxelSizeMetres = proto.VoxelSizeMetres,
        SeaLevelMetres = proto.SeaLevelMetres,
        WrapX = proto.WrapX,
        WrapY = proto.WrapY,
        ChunkEngineVersion = proto.ChunkEngineVersion,
        ViewRadiusChunks = proto.ViewRadiusChunks
      };
    }

    public override string ToString()
    {
      var widthKm = WidthCells * CellSizeMetres / 1000.0;
      var heightKm = HeightCells * CellSizeMetres / 1000.0;

      return $"{Name} {widthKm:F0}x{heightKm:F0} km, chunks {ChunkSize}x{ChunkSize}x{ChunkHeight} " +
             $"@{VoxelSizeMetres:F1}m, sea level {SeaLevelMetres:F0}m, wrapX={WrapX} wrapY={WrapY}, " +
             $"chunk engine v{ChunkEngineVersion}, view radius {ViewRadiusChunks}";
    }
  }
}
