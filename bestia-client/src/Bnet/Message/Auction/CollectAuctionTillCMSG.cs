using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>The owner empties the till of the house they stand at.</summary>
  public partial class CollectAuctionTillCMSG : ICMSG
  {
    public ulong HouseEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CollectAuctionTill = new global::Bnet.CollectAuctionTillCMSG
        {
          HouseEntityId = HouseEntityId
        }
      };
    }
  }
}
