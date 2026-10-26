using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>One page of the shared market, cheapest per unit first.</summary>
  [GlobalClass]
  public partial class AuctionListingsSMSG : ISMSG
  {
    [Export] public int Page { get; set; }
    [Export] public int PageCount { get; set; }

    /// <summary>The sender's own listings, for the Sell tab, rather than a search.</summary>
    [Export] public bool Mine { get; set; }

    [Export] public Godot.Collections.Array<AuctionListingEntry> Listings { get; set; } = [];

    public static AuctionListingsSMSG FromProto(global::Bnet.AuctionListingsSMSG proto)
    {
      var listings = new Godot.Collections.Array<AuctionListingEntry>();
      foreach (var listing in proto.Listings)
      {
        listings.Add(AuctionListingEntry.FromProto(listing));
      }

      return new AuctionListingsSMSG
      {
        Page = proto.Page,
        PageCount = proto.PageCount,
        Mine = proto.Mine,
        Listings = listings
      };
    }
  }
}
