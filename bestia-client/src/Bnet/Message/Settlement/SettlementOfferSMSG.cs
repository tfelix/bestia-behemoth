using Godot;

namespace BestiaBehemothClient.Bnet.Message.Settlement
{
  /// <summary>A founder offers this master their town.</summary>
  [GlobalClass]
  public partial class SettlementOfferSMSG : ISMSG
  {
    [Export] public ulong SettlementId { get; set; }
    [Export] public string Name { get; set; } = "";
    [Export] public string FromName { get; set; } = "";

    public static SettlementOfferSMSG FromProto(global::Bnet.SettlementOfferSMSG proto)
    {
      return new SettlementOfferSMSG { SettlementId = proto.SettlementId, Name = proto.Name, FromName = proto.FromName };
    }
  }
}
