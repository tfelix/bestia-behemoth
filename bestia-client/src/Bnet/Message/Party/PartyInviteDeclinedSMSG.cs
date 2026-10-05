using Godot;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The invited player declined an invitation this player sent.</summary>
  [GlobalClass]
  public partial class PartyInviteDeclinedSMSG : PartySMSG
  {
    [Export] public ulong InvitationId { get; set; }

    public static PartyInviteDeclinedSMSG FromProto(global::Bnet.PartyInviteDeclinedSMSG proto)
    {
      return new PartyInviteDeclinedSMSG { InvitationId = proto.InvitationId };
    }
  }
}
