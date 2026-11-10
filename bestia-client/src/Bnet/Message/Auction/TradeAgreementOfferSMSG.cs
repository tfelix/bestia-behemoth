using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>An auction house owner offers this master a Trade Agreement.</summary>
  [GlobalClass]
  public partial class TradeAgreementOfferSMSG : ISMSG
  {
    [Export] public ulong OfferId { get; set; }
    [Export] public string FromName { get; set; } = "";

    public static TradeAgreementOfferSMSG FromProto(global::Bnet.TradeAgreementOfferSMSG proto)
    {
      return new TradeAgreementOfferSMSG { OfferId = proto.OfferId, FromName = proto.FromName };
    }
  }
}
