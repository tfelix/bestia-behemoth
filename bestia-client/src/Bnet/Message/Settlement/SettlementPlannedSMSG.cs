using Godot;

namespace BestiaBehemothClient.Bnet.Message.Settlement
{
  /// <summary>The name is free and kept for the next Founder cast.</summary>
  [GlobalClass]
  public partial class SettlementPlannedSMSG : ISMSG
  {
    [Export] public string Name { get; set; } = "";

    public static SettlementPlannedSMSG FromProto(global::Bnet.SettlementPlannedSMSG proto)
    {
      return new SettlementPlannedSMSG { Name = proto.Name };
    }
  }
}
