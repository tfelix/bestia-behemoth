using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// Puts one item into our side of the trade window. The server takes it out of the inventory at once, so nothing else can spend it while it is offered.
  /// </summary>
  public partial class OfferTradeItemCMSG : ICMSG
  {
    public ulong TradeId { get; set; }

    public uint ItemId { get; set; }

    /// <summary>The exact instance, or 0 for a plain stack.</summary>
    public ulong UniqueId { get; set; }

    public uint Amount { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        OfferTradeItem = new global::Bnet.OfferTradeItemCMSG
        {
          TradeId = TradeId,
          ItemId = ItemId,
          UniqueId = UniqueId,
          Amount = Amount
        }
      };
    }
  }
}
