using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Settlement
{
  public partial class TearDownSettlementCMSG : ICMSG
  {
    public ulong SignEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        TearDownSettlement = new global::Bnet.TearDownSettlementCMSG { SignEntityId = SignEntityId }
      };
    }
  }
}
