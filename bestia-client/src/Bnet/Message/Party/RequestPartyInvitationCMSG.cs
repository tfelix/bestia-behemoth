using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The party owner invites another player.</summary>
  public partial class RequestPartyInvitationCMSG : ICMSG
  {
    public ulong InvitedAccountId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RequestPartyInvitation = new global::Bnet.RequestPartyInvitationCMSG
        {
          InvitedAccountId = InvitedAccountId
        }
      };
    }
  }
}
