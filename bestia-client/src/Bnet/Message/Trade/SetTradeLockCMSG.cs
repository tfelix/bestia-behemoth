using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// Locks or unlocks our side. Any change to either offer clears both locks again.
  /// </summary>
  public partial class SetTradeLockCMSG : ICMSG
  {
    public ulong TradeId { get; set; }

    public bool Locked { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetTradeLock = new global::Bnet.SetTradeLockCMSG
        {
          TradeId = TradeId,
          Locked = Locked
        }
      };
    }
  }
}
