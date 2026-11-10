using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>The owner tears down their house while no listing is up in it.</summary>
  public partial class TearDownAuctionHouseCMSG : ICMSG
  {
    public ulong HouseEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        TearDownAuctionHouse = new global::Bnet.TearDownAuctionHouseCMSG
        {
          HouseEntityId = HouseEntityId
        }
      };
    }
  }
}
