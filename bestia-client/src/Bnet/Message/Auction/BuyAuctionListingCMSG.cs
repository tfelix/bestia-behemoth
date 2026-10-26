using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Buys a whole listing at its price.</summary>
  public partial class BuyAuctionListingCMSG : ICMSG
  {
    public ulong HouseEntityId { get; set; }

    public ulong ListingId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        BuyAuctionListing = new global::Bnet.BuyAuctionListingCMSG
        {
          HouseEntityId = HouseEntityId,
          ListingId = ListingId
        }
      };
    }
  }
}
