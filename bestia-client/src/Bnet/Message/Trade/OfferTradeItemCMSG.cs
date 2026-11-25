using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// Puts one item, or one of our party bestias, into our side of the trade window. The server takes an item out
  /// of the inventory at once, so nothing else can spend it while it is offered; a bestia stays ours until the
  /// trade goes through.
  /// </summary>
  public partial class OfferTradeItemCMSG : ICMSG
  {
    public ulong TradeId { get; set; }

    /// <summary>The held pile or copy to offer from, as InventoryItem.PlayerItemId names it.</summary>
    public ulong PlayerItemId { get; set; }

    public uint Amount { get; set; }

    /// <summary>A party bestia to offer instead of an item; 0 for an item.</summary>
    public ulong BestiaEntityId { get; set; }

    /// <summary>The player agreed that what the bestia carries and wears goes with it.</summary>
    public bool AcceptCarriedItems { get; set; }

    public override Envelope ToEnvelope()
    {
      var offer = new global::Bnet.OfferTradeItemCMSG { TradeId = TradeId, AcceptCarriedItems = AcceptCarriedItems };
      if (BestiaEntityId != 0)
      {
        offer.BestiaEntityId = BestiaEntityId;
      }
      else
      {
        offer.PlayerItemId = PlayerItemId;
        offer.Amount = Amount;
      }

      return new Envelope { OfferTradeItem = offer };
    }
  }
}
