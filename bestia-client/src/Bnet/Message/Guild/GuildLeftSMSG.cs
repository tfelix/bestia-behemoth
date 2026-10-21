using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>This player is no longer in the guild.</summary>
  [GlobalClass]
  public partial class GuildLeftSMSG : GuildSMSG
  {
    [Export] public ulong GuildId { get; set; }

    /// <summary><c>bnet.GuildLeftReason</c> as a snake_case name: <c>left</c>, <c>kicked</c> or <c>disbanded</c>.</summary>
    [Export] public string ReasonName { get; set; } = string.Empty;

    public static GuildLeftSMSG FromProto(global::Bnet.GuildLeftSMSG proto)
    {
      return new GuildLeftSMSG
      {
        GuildId = proto.GuildId,
        ReasonName = EnumName.Of(proto.Reason)
      };
    }
  }
}
