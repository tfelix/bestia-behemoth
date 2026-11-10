using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Bids on a timed auction; the coin leaves the bag at once.</summary>
  public partial class BidAuctionCMSG : ICMSG
  {
    public ulong HouseEntityId { get; set; }

    public ulong ListingId { get; set; }

    public long Amount { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        BidAuction = new global::Bnet.BidAuctionCMSG
        {
          HouseEntityId = HouseEntityId,
          ListingId = ListingId,
          Amount = Amount
        }
      };
    }
  }
}
