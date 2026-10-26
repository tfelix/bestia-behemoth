using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Asks one page of the shared market. Names live on the client, so a name arrives as item ids.</summary>
  public partial class SearchAuctionsCMSG : ICMSG
  {
    public ulong HouseEntityId { get; set; }

    /// <summary>An <c>AuctionCategory</c> value; 0 is any.</summary>
    public int Category { get; set; }

    /// <summary>Inclusive; 0 leaves that end open.</summary>
    public int MinLevel { get; set; }

    public int MaxLevel { get; set; }

    public Godot.Collections.Array<long> ItemIds { get; set; } = [];

    public bool Mine { get; set; }

    public int Page { get; set; }

    public override Envelope ToEnvelope()
    {
      var search = new global::Bnet.SearchAuctionsCMSG
      {
        HouseEntityId = HouseEntityId,
        Category = (AuctionCategory)Category,
        MinLevel = MinLevel,
        MaxLevel = MaxLevel,
        Mine = Mine,
        Page = Page
      };
      foreach (var itemId in ItemIds)
      {
        search.ItemIds.Add((ulong)itemId);
      }

      return new Envelope { SearchAuctions = search };
    }
  }
}
