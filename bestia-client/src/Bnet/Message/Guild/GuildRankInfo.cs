using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>One rank of a <see cref="GuildInfoSMSG"/>. Its own file because Godot registers one <c>[GlobalClass]</c> per file.</summary>
  [GlobalClass]
  public partial class GuildRankInfo : GodotObject
  {
    [Export] public ulong RankId { get; set; }

    /// <summary>0 is the leader's rank; a higher number is a lower rank.</summary>
    [Export] public uint Position { get; set; }

    [Export] public string Name { get; set; } = string.Empty;

    [Export] public uint TaxPercent { get; set; }

    [Export] public bool CanInvite { get; set; }

    [Export] public bool CanKick { get; set; }

    [Export] public bool CanManageRanks { get; set; }

    [Export] public bool CanUseGuildSkills { get; set; }

    [Export] public bool CanEditMotd { get; set; }

    public static GuildRankInfo FromProto(global::Bnet.GuildRank proto)
    {
      return new GuildRankInfo
      {
        RankId = proto.RankId,
        Position = proto.Position,
        Name = proto.Name,
        TaxPercent = proto.TaxPercent,
        CanInvite = proto.CanInvite,
        CanKick = proto.CanKick,
        CanManageRanks = proto.CanManageRanks,
        CanUseGuildSkills = proto.CanUseGuildSkills,
        CanEditMotd = proto.CanEditMotd
      };
    }
  }
}
