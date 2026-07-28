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
  public partial class WorldInfoSMSG : ISMSG
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

    [Export] public ulong PipelineVersion { get; set; }
    [Export] public ulong BlockPaletteVersion { get; set; }

    /// <summary>
    /// The chunk encoding version. The one component of the version vector this client must honour: a
    /// pipeline or palette mismatch only matters to a client that generates its own terrain, but a format
    /// mismatch cannot be decoded at all.
    /// </summary>
    [Export] public uint ChunkFormatVersion { get; set; }

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
        PipelineVersion = proto.PipelineVersion,
        BlockPaletteVersion = proto.BlockPaletteVersion,
        ChunkFormatVersion = proto.ChunkFormatVersion,
        ViewRadiusChunks = proto.ViewRadiusChunks
      };
    }

    public override string ToString()
    {
      var widthKm = WidthCells * CellSizeMetres / 1000.0;
      var heightKm = HeightCells * CellSizeMetres / 1000.0;

      return $"{Name} {widthKm:F0}x{heightKm:F0} km, chunks {ChunkSize}x{ChunkSize}x{ChunkHeight} " +
             $"@{VoxelSizeMetres:F1}m, sea level {SeaLevelMetres:F0}m, wrapX={WrapX} wrapY={WrapY}, " +
             $"pipeline 0x{PipelineVersion:X}/palette 0x{BlockPaletteVersion:X}/format {ChunkFormatVersion}, " +
             $"view radius {ViewRadiusChunks}";
    }
  }
}
