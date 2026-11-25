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

    /// <summary>Only goods holding an essence of <see cref="EssenceBestiaIds"/> (empty: any) in <see cref="EssenceTier"/> (empty: any).</summary>
    public bool WithEssence { get; set; }

    public Godot.Collections.Array<long> EssenceBestiaIds { get; set; } = [];

    public string EssenceTier { get; set; } = string.Empty;

    public override Envelope ToEnvelope()
    {
      var search = new global::Bnet.SearchAuctionsCMSG
      {
        HouseEntityId = HouseEntityId,
        Category = (AuctionCategory)Category,
        MinLevel = MinLevel,
        MaxLevel = MaxLevel,
        Mine = Mine,
        Page = Page,
        WithEssence = WithEssence,
        EssenceTier = EssenceTier
      };
      foreach (var itemId in ItemIds)
      {
        search.ItemIds.Add((ulong)itemId);
      }
      foreach (var bestiaId in EssenceBestiaIds)
      {
        search.EssenceBestiaIds.Add((ulong)bestiaId);
      }

      return new Envelope { SearchAuctions = search };
    }
  }
}
