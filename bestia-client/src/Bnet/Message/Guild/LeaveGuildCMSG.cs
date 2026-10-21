using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Leaves the guild. The leader cannot leave, only disband.</summary>
  public partial class LeaveGuildCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        LeaveGuild = new global::Bnet.LeaveGuildCMSG()
      };
    }
  }
}
