using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  public partial class GetSkillsCMSG : ICMSG
  {

    public override Envelope ToEnvelope()
    {
      var getSkills = new global::Bnet.GetSkillsCMSG();

      return new Envelope
      {
        GetSkills = getSkills
      };
    }
  }
}
