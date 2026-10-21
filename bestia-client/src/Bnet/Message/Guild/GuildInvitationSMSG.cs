using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Another player invites this one into their guild; answered with an <see cref="AnswerGuildInviteCMSG"/>.</summary>
  [GlobalClass]
  public partial class GuildInvitationSMSG : GuildSMSG
  {
    [Export] public ulong InvitationId { get; set; }

    [Export] public string GuildName { get; set; } = string.Empty;

    [Export] public string InvitedByMaster { get; set; } = string.Empty;

    /// <summary>How long the server keeps the invitation open; an answer after that is refused.</summary>
    [Export] public uint ExpiresInSeconds { get; set; }

    public static GuildInvitationSMSG FromProto(global::Bnet.GuildInvitationSMSG proto)
    {
      return new GuildInvitationSMSG
      {
        InvitationId = proto.InvitationId,
        GuildName = proto.GuildName,
        InvitedByMaster = proto.InvitedByMaster,
        ExpiresInSeconds = proto.ExpiresInSeconds
      };
    }
  }
}
