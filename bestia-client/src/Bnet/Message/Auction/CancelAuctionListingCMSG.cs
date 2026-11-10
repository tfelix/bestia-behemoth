using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Takes the sender's own listing down. The goods come back; the fee does not.</summary>
  public partial class CancelAuctionListingCMSG : ICMSG
  {
    public ulong HouseEntityId { get; set; }

    public ulong ListingId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CancelAuctionListing = new global::Bnet.CancelAuctionListingCMSG
        {
          HouseEntityId = HouseEntityId,
          ListingId = ListingId
        }
      };
    }
  }
}
