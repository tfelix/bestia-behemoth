using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// Calls the whole trade off.
  /// </summary>
  public partial class CancelTradeCMSG : ICMSG
  {
    public ulong TradeId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CancelTrade = new global::Bnet.CancelTradeCMSG
        {
          TradeId = TradeId
        }
      };
    }
  }
}
