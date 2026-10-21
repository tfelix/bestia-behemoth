using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Removes a member of a lower rank from the guild.</summary>
  public partial class KickGuildMemberCMSG : ICMSG
  {
    public ulong MasterId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        KickGuildMember = new global::Bnet.KickGuildMemberCMSG
        {
          MasterId = MasterId
        }
      };
    }
  }
}
