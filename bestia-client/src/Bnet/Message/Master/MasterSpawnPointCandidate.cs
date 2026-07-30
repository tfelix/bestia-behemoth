using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Godot-friendly wrapper for one settlement spawn point candidate a new master could choose to
  /// start life near.
  /// </summary>
  [GlobalClass]
  public partial class MasterSpawnPointCandidate : GodotObject
  {
    [Export]
    public uint Id { get; set; }

    [Export]
    public string SettlementName { get; set; } = string.Empty;

    [Export]
    public string Tier { get; set; } = string.Empty;

    public static MasterSpawnPointCandidate FromProto(global::Bnet.MasterSpawnPointCandidate protoCandidate)
    {
      return new MasterSpawnPointCandidate
      {
        Id = protoCandidate.Id,
        SettlementName = protoCandidate.SettlementName,
        Tier = protoCandidate.Tier
      };
    }
  }
}
