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

    [Export]
    public uint MobId { get; set; }

    [Export]
    public string Name { get; set; } = "";

    [Export]
    public uint Level { get; set; }

    [Export]
    public Vector3 Position { get; set; }

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
        MobId = protoBestiaInfo.MobId,
        Name = protoBestiaInfo.Name,
        Level = protoBestiaInfo.Level
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
