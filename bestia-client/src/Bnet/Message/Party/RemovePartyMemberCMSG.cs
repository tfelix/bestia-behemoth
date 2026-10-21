using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The party owner removes a member from their own party.</summary>
  public partial class RemovePartyMemberCMSG : ICMSG
  {
    public ulong MemberMasterId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RemovePartyMember = new global::Bnet.RemovePartyMemberCMSG
        {
          MemberMasterId = MemberMasterId
        }
      };
    }
  }
}
