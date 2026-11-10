using Godot;

namespace BestiaBehemothClient.Bnet.Message.Settlement
{
  /// <summary>Opens the city sign window: the answer to clicking a standing sign.</summary>
  [GlobalClass]
  public partial class SettlementSignSMSG : ISMSG
  {
    [Export] public ulong SignEntityId { get; set; }
    [Export] public ulong SettlementId { get; set; }
    [Export] public string Name { get; set; } = "";
    [Export] public string FounderName { get; set; } = "";
    [Export] public bool IsFounder { get; set; }
    [Export] public int RadiusMetres { get; set; }

    /// <summary>Building points times ten: a wall piece is worth a tenth.</summary>
    [Export] public int BuildingPointsTenths { get; set; }

    [Export] public int SignDefense { get; set; }

    /// <summary>No post office or auction house stands in the sphere.</summary>
    [Export] public bool CanTearDown { get; set; }

    public static SettlementSignSMSG FromProto(global::Bnet.SettlementSignSMSG proto)
    {
      return new SettlementSignSMSG
      {
        SignEntityId = proto.SignEntityId,
        SettlementId = proto.SettlementId,
        Name = proto.Name,
        FounderName = proto.FounderName,
        IsFounder = proto.IsFounder,
        RadiusMetres = (int)proto.RadiusMetres,
        BuildingPointsTenths = (int)proto.BuildingPointsTenths,
        SignDefense = (int)proto.SignDefense,
        CanTearDown = proto.CanTearDown
      };
    }
  }
}
