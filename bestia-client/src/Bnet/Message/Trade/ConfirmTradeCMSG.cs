using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// The final commitment, accepted only once both sides are locked. The exchange runs on the second one.
  /// </summary>
  public partial class ConfirmTradeCMSG : ICMSG
  {
    public ulong TradeId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        ConfirmTrade = new global::Bnet.ConfirmTradeCMSG
        {
          TradeId = TradeId
        }
      };
    }
  }
}
