using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// Takes one line back out of our own offer: an item line by its offer slot, or a bestia line by its bestia.
  /// </summary>
  public partial class RetractTradeItemCMSG : ICMSG
  {
    public ulong TradeId { get; set; }

    /// <summary>The id the server gave this offer line, not an item id.</summary>
    public ulong OfferSlotId { get; set; }

    /// <summary>A bestia line to take back instead; 0 for an item line.</summary>
    public ulong PlayerBestiaId { get; set; }

    public override Envelope ToEnvelope()
    {
      var retract = new global::Bnet.RetractTradeItemCMSG { TradeId = TradeId };
      if (PlayerBestiaId != 0)
      {
        retract.PlayerBestiaId = PlayerBestiaId;
      }
      else
      {
        retract.OfferSlotId = OfferSlotId;
      }

      return new Envelope { RetractTradeItem = retract };
    }
  }
}
