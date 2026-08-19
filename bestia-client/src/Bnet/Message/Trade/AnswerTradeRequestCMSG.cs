using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// Answers the prompt a TradeRequestSMSG raised.
  /// </summary>
  public partial class AnswerTradeRequestCMSG : ICMSG
  {
    public ulong TradeId { get; set; }

    public bool Accept { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        AnswerTradeRequest = new global::Bnet.AnswerTradeRequestCMSG
        {
          TradeId = TradeId,
          Accept = Accept
        }
      };
    }
  }
}
