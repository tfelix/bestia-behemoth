using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>The leader spends a guild skill point on one guild skill.</summary>
  public partial class InvestGuildSkillCMSG : ICMSG
  {
    public ulong SkillId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        InvestGuildSkill = new global::Bnet.InvestGuildSkillCMSG
        {
          SkillId = SkillId
        }
      };
    }
  }
}
