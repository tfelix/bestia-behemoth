using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>Accepts a party invitation from a <see cref="PartyInvitationSMSG"/>.</summary>
  public partial class AcceptPartyInviteCMSG : ICMSG
  {
    public ulong InvitationId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        AcceptPartyInvite = new global::Bnet.AcceptPartyInviteCMSG
        {
          InvitationId = InvitationId
        }
      };
    }
  }
}
