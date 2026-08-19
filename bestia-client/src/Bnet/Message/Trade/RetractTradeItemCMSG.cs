using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// Takes one line back out of our own offer.
  /// </summary>
  public partial class RetractTradeItemCMSG : ICMSG
  {
    public ulong TradeId { get; set; }

    /// <summary>The id the server gave this offer line, not an item id.</summary>
    public ulong OfferSlotId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RetractTradeItem = new global::Bnet.RetractTradeItemCMSG
        {
          TradeId = TradeId,
          OfferSlotId = OfferSlotId
        }
      };
    }
  }
}
