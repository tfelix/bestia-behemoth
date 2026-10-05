using Godot;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>Another player invites this one into their party; answered with an accept or a decline.</summary>
  [GlobalClass]
  public partial class PartyInvitationSMSG : PartySMSG
  {
    [Export] public string InvitedByMaster { get; set; } = string.Empty;

    [Export] public ulong PartyId { get; set; }

    [Export] public string PartyName { get; set; } = string.Empty;

    [Export] public ulong InvitationId { get; set; }

    public static PartyInvitationSMSG FromProto(global::Bnet.PartyInvitationSMSG proto)
    {
      return new PartyInvitationSMSG
      {
        InvitedByMaster = proto.InvitedByMaster,
        PartyId = proto.PartyId,
        PartyName = proto.PartyName,
        InvitationId = proto.InvitationId
      };
    }
  }
}
