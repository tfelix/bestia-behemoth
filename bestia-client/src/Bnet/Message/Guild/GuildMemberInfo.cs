using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>One member of a <see cref="GuildInfoSMSG"/>. Its own file because Godot registers one <c>[GlobalClass]</c> per file.</summary>
  [GlobalClass]
  public partial class GuildMemberInfo : GodotObject
  {
    [Export] public ulong MasterId { get; set; }

    [Export] public string MasterName { get; set; } = string.Empty;

    [Export] public uint Level { get; set; }

    [Export] public ulong RankId { get; set; }

    [Export] public bool IsOnline { get; set; }

    /// <summary>Tax this member has paid into the guild.</summary>
    [Export] public ulong ExpContributed { get; set; }

    [Export] public bool IsLeader { get; set; }

    public static GuildMemberInfo FromProto(global::Bnet.GuildMember proto)
    {
      return new GuildMemberInfo
      {
        MasterId = proto.MasterId,
        MasterName = proto.MasterName,
        Level = proto.Level,
        RankId = proto.RankId,
        IsOnline = proto.Online,
        ExpContributed = proto.ExpContributed,
        IsLeader = proto.IsLeader
      };
    }
  }
}
