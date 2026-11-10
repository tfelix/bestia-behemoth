using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Puts goods up at a fixed price. Answered by the seller's own listings.</summary>
  public partial class ListAuctionItemCMSG : ICMSG
  {
    public ulong HouseEntityId { get; set; }

    /// <summary>The held pile or copy, as InventoryItem.player_item_id names it.</summary>
    public ulong PlayerItemId { get; set; }

    public int Amount { get; set; }

    /// <summary>Coins for the whole lot: the fixed price, or an auction's starting bid.</summary>
    public long Price { get; set; }

    /// <summary>0 for a fixed price; 1, 2 or 5 for an auction of that many days.</summary>
    public int DurationDays { get; set; }

    /// <summary>0 for none.</summary>
    public long Buyout { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        ListAuctionItem = new global::Bnet.ListAuctionItemCMSG
        {
          HouseEntityId = HouseEntityId,
          PlayerItemId = PlayerItemId,
          Amount = Amount,
          Price = Price,
          DurationDays = DurationDays,
          Buyout = Buyout
        }
      };
    }
  }
}
