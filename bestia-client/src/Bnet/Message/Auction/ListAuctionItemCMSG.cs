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

    /// <summary>Coins for the whole lot.</summary>
    public long Price { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        ListAuctionItem = new global::Bnet.ListAuctionItemCMSG
        {
          HouseEntityId = HouseEntityId,
          PlayerItemId = PlayerItemId,
          Amount = Amount,
          Price = Price
        }
      };
    }
  }
}
