using Godot;

namespace BestiaBehemothClient.Bnet.Message.Stall
{
  /// <summary>A Trader's street stall as this viewer sees it. A full snapshot: it opens, updates and closes the window.</summary>
  [GlobalClass]
  public partial class StreetStallSMSG : ISMSG
  {
    /// <summary>The stall entity, which every later stall request names.</summary>
    [Export] public ulong StallEntityId { get; set; }

    [Export] public string SellerName { get; set; } = "";

    /// <summary>The viewer is the seller: they manage the stall rather than buy from it.</summary>
    [Export] public bool Mine { get; set; }

    [Export] public int MaxListings { get; set; }
    [Export] public int ClosesInSeconds { get; set; }

    /// <summary>The stall packed up.</summary>
    [Export] public bool Closed { get; set; }

    [Export] public Godot.Collections.Array<StallListingEntry> Listings { get; set; } = [];

    public static StreetStallSMSG FromProto(global::Bnet.StreetStallSMSG proto)
    {
      var listings = new Godot.Collections.Array<StallListingEntry>();
      foreach (var listing in proto.Listings)
      {
        listings.Add(StallListingEntry.FromProto(listing));
      }

      return new StreetStallSMSG
      {
        StallEntityId = proto.StallEntityId,
        SellerName = proto.SellerName,
        Mine = proto.Mine,
        MaxListings = proto.MaxListings,
        ClosesInSeconds = proto.ClosesInSeconds,
        Closed = proto.Closed,
        Listings = listings
      };
    }
  }
}
