using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>The leader spends a guild skill point on Guild Extension, the only guild skill there is.</summary>
  public partial class InvestGuildSkillCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        InvestGuildSkill = new global::Bnet.InvestGuildSkillCMSG
        {
          Skill = GuildSkill.Extension
        }
      };
    }
  }
}
