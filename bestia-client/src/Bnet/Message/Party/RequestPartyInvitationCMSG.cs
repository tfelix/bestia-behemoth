using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The party owner invites another player by master name.</summary>
  public partial class RequestPartyInvitationCMSG : ICMSG
  {
    public string InvitedMasterName { get; set; } = string.Empty;

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RequestPartyInvitation = new global::Bnet.RequestPartyInvitationCMSG
        {
          InvitedMasterName = InvitedMasterName
        }
      };
    }
  }
}
