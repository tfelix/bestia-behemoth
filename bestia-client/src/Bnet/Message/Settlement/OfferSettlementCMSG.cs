using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Settlement
{
  /// <summary>Offers the founder's town to a master standing nearby, named by master name.</summary>
  public partial class OfferSettlementCMSG : ICMSG
  {
    public ulong SignEntityId { get; set; }

    public string MasterName { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        OfferSettlement = new global::Bnet.OfferSettlementCMSG { SignEntityId = SignEntityId, MasterName = MasterName }
      };
    }
  }
}
