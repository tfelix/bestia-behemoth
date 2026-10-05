using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The party owner removes a member.</summary>
  public partial class RemovePartyMemberCMSG : ICMSG
  {
    public ulong PartyId { get; set; }

    public ulong MemberAccountId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RemovePartyMember = new global::Bnet.RemovePartyMemberCMSG
        {
          PartyId = PartyId,
          MemberAccountId = MemberAccountId
        }
      };
    }
  }
}
