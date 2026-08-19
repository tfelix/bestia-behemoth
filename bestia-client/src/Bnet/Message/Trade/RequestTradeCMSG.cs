using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// Asks the player behind an entity to trade. The server answers with a TradeRequestSMSG on their side and, once they say yes or no, a TradeStateSMSG or an OperationError on ours.
  /// </summary>
  public partial class RequestTradeCMSG : ICMSG
  {
    public ulong TargetEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RequestTrade = new global::Bnet.RequestTradeCMSG
        {
          TargetEntityId = TargetEntityId
        }
      };
    }
  }
}
