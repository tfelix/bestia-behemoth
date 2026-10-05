using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>Declines a party invitation from a <see cref="PartyInvitationSMSG"/>.</summary>
  public partial class DeclinePartyInviteCMSG : ICMSG
  {
    public ulong InvitationId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        DeclinePartyInvite = new global::Bnet.DeclinePartyInviteCMSG
        {
          InvitationId = InvitationId
        }
      };
    }
  }
}
