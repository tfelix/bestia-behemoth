using Godot;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// Somebody nearby wants to trade. Raises the accept/decline prompt; answered with an
  /// <see cref="AnswerTradeRequestCMSG"/> carrying the same <see cref="TradeId"/>.
  ///
  /// Carries the asker's name because nothing else would let the client resolve one from an entity id: a
  /// request can outlive the asker walking out of view, taking their visual with them.
  /// </summary>
  [GlobalClass]
  public partial class TradeRequestSMSG : ISMSG
  {
    [Export] public ulong TradeId { get; set; }

    [Export] public ulong FromEntityId { get; set; }

    [Export] public string FromMasterName { get; set; } = string.Empty;

    public static TradeRequestSMSG FromProto(global::Bnet.TradeRequestSMSG proto)
    {
      return new TradeRequestSMSG
      {
        TradeId = proto.TradeId,
        FromEntityId = proto.FromEntityId,
        FromMasterName = proto.FromMasterName
      };
    }
  }
}
