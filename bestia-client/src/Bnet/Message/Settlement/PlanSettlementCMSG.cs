using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Settlement
{
  /// <summary>Names the town the next Founder cast plants. Answered by a SettlementPlannedSMSG.</summary>
  public partial class PlanSettlementCMSG : ICMSG
  {
    public string Name { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      return new Envelope { PlanSettlement = new global::Bnet.PlanSettlementCMSG { Name = Name } };
    }
  }
}
