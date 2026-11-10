using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>The owner ends a Trade Agreement at one of their houses.</summary>
  public partial class CutTradeLinkCMSG : ICMSG
  {
    public ulong HouseEntityId { get; set; }

    public ulong PartnerMasterId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CutTradeLink = new global::Bnet.CutTradeLinkCMSG
        {
          HouseEntityId = HouseEntityId,
          PartnerMasterId = PartnerMasterId
        }
      };
    }
  }
}
