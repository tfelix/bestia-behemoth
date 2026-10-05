using Godot;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>Tells the inviter what became of an invitation they sent.</summary>
  [GlobalClass]
  public partial class PartyInvitationCreatedSMSG : PartySMSG
  {
    [Export] public ulong InvitationId { get; set; }

    [Export] public ulong InvitedAccountId { get; set; }

    /// <summary><c>bnet.InvitationStatus</c> as a snake_case name, for the GDScript side.</summary>
    [Export] public string StatusName { get; set; } = string.Empty;

    public static PartyInvitationCreatedSMSG FromProto(global::Bnet.PartyInvitationCreatedSMSG proto)
    {
      return new PartyInvitationCreatedSMSG
      {
        InvitationId = proto.InvitationId,
        InvitedAccountId = proto.InvitedAccountId,
        StatusName = EnumName.Of(proto.Status)
      };
    }
  }
}
