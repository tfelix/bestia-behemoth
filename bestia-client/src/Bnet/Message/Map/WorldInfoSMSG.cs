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

    public static WorldInfoSMSG FromProto(global::Bnet.WorldInfoSMSG proto)
    {
      return new WorldInfoSMSG
      {
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
