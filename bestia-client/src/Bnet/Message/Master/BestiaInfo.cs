using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Godot-friendly wrapper for BestiaInfo protobuf data containing information about a player's bestia
  /// </summary>
  [GlobalClass]
  public partial class BestiaInfo : GodotObject
  {
    [Export]
    public ulong EntityId { get; set; }

    /// <summary>Names the bestia in its orders.</summary>
    [Export]
    public ulong PlayerBestiaId { get; set; }

    [Export]
    public uint MobId { get; set; }

    [Export]
    public string Name { get; set; } = "";

    [Export]
    public uint Level { get; set; }

    [Export]
    public Vector3 Position { get; set; }

    /// <summary>The <c>BestiaSex</c> ordinal: 0 unknown, 1 male, 2 female.</summary>
    [Export]
    public int Sex { get; set; }

    [Export]
    public IvReadingInfo Ivs { get; set; } = new();

    /// <summary>The <c>BestiaOrigin</c> ordinal: 1 caught, 2 bred, 3 granted.</summary>
    [Export]
    public int Origin { get; set; }

    /// <summary>Who caught, bred or was given it.</summary>
    [Export]
    public string OriginMasterName { get; set; } = "";

    /// <summary>What it does now, for its status line; null when the server sent none.</summary>
    [Export]
    public BestiaActivityInfo Activity { get; set; }

    /// <summary>
    /// Creates a BestiaInfo message from protobuf data
    /// </summary>
    /// <param name="protoBestiaInfo">The protobuf BestiaInfo object</param>
    /// <returns>Godot-friendly BestiaInfo object</returns>
    public static BestiaInfo FromProto(global::Bnet.BestiaInfo protoBestiaInfo)
    {
      var bestiaInfo = new BestiaInfo
      {
        EntityId = protoBestiaInfo.EntityId,
        PlayerBestiaId = protoBestiaInfo.PlayerBestiaId,
        MobId = protoBestiaInfo.MobId,
        Name = protoBestiaInfo.Name,
        Level = protoBestiaInfo.Level,
        Sex = (int)protoBestiaInfo.Sex,
        Ivs = IvReadingInfo.FromProto(protoBestiaInfo.Ivs),
        Origin = (int)protoBestiaInfo.Origin,
        OriginMasterName = protoBestiaInfo.OriginMasterName,
        Activity = protoBestiaInfo.Activity == null ? null : BestiaActivityInfo.FromProto(protoBestiaInfo.Activity)
      };

      // Convert Vec3 to Godot Vector3 with null check (swap Y and Z: server z-up to Godot y-up)
      if (protoBestiaInfo.Position != null)
      {
        bestiaInfo.Position = new Vector3(
          (float)protoBestiaInfo.Position.X,
          (float)protoBestiaInfo.Position.Z,
          (float)protoBestiaInfo.Position.Y
        );
      }

      return bestiaInfo;
    }
  }
}
