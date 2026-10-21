using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Answers a <see cref="GuildInvitationSMSG"/>.</summary>
  public partial class AnswerGuildInviteCMSG : ICMSG
  {
    public ulong InvitationId { get; set; }

    public bool Accept { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        AnswerGuildInvite = new global::Bnet.AnswerGuildInviteCMSG
        {
          InvitationId = InvitationId,
          Accept = Accept
        }
      };
    }
  }
}
