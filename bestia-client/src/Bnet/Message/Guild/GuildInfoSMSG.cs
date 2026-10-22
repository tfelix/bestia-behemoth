using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>
  /// The whole guild as this player sees it, pushed whenever any of it changes. <see cref="GuildId"/> 0 with no
  /// members means this player is in no guild.
  /// </summary>
  [GlobalClass]
  public partial class GuildInfoSMSG : GuildSMSG
  {
    [Export] public ulong GuildId { get; set; }

    [Export] public string Name { get; set; } = string.Empty;

    [Export] public uint Level { get; set; }

    [Export] public ulong Exp { get; set; }

    /// <summary>0 once the guild is at its highest level.</summary>
    [Export] public ulong ExpNextLevel { get; set; }

    [Export] public uint SkillPoints { get; set; }

    [Export] public uint MaxMembers { get; set; }

    /// <summary>The leader's rank first, then down the hierarchy.</summary>
    [Export] public Godot.Collections.Array<GuildRankInfo> Ranks { get; set; } = [];

    [Export] public Godot.Collections.Array<GuildMemberInfo> Members { get; set; } = [];

    /// <summary>This player's own master, so the window can tell which member it is.</summary>
    [Export] public ulong OwnMasterId { get; set; }

    [Export] public uint MaxRanks { get; set; }

    [Export] public uint MinTaxPercent { get; set; }

    [Export] public uint MaxTaxPercent { get; set; }

    /// <summary>The whole guild skill tree with the guild's level in each skill.</summary>
    [Export] public Godot.Collections.Array<GuildSkillInfo> Skills { get; set; } = [];

    /// <summary>The guild's message of the day, plain text; both empty when unset.</summary>
    [Export] public string MotdTitle { get; set; } = string.Empty;

    [Export] public string MotdBody { get; set; } = string.Empty;

    [Export] public uint MotdTitleMaxLength { get; set; }

    [Export] public uint MotdBodyMaxLength { get; set; }

    /// <summary>Names the guild's emblem at the api-service; empty while it has none.</summary>
    [Export] public string EmblemHash { get; set; } = string.Empty;

    /// <summary>Masters kicked out, newest first; none of them can be invited until it is lifted.</summary>
    [Export] public Godot.Collections.Array<GuildExpulsionInfo> Expulsions { get; set; } = [];

    public static GuildInfoSMSG FromProto(global::Bnet.GuildInfoSMSG proto)
    {
      var ranks = new Godot.Collections.Array<GuildRankInfo>();
      foreach (var rank in proto.Ranks)
      {
        ranks.Add(GuildRankInfo.FromProto(rank));
      }

      var members = new Godot.Collections.Array<GuildMemberInfo>();
      foreach (var member in proto.Members)
      {
        members.Add(GuildMemberInfo.FromProto(member));
      }

      var expulsions = new Godot.Collections.Array<GuildExpulsionInfo>();
      foreach (var expulsion in proto.Expulsions)
      {
        expulsions.Add(GuildExpulsionInfo.FromProto(expulsion));
      }

      var skills = new Godot.Collections.Array<GuildSkillInfo>();
      foreach (var skill in proto.Skills)
      {
        skills.Add(GuildSkillInfo.FromProto(skill));
      }

      return new GuildInfoSMSG
      {
        GuildId = proto.GuildId,
        Name = proto.Name,
        Level = proto.Level,
        Exp = proto.Exp,
        ExpNextLevel = proto.ExpNextLevel,
        SkillPoints = proto.SkillPoints,
        MaxMembers = proto.MaxMembers,
        Ranks = ranks,
        Members = members,
        OwnMasterId = proto.OwnMasterId,
        MaxRanks = proto.MaxRanks,
        MinTaxPercent = proto.MinTaxPercent,
        MaxTaxPercent = proto.MaxTaxPercent,
        Skills = skills,
        MotdTitle = proto.MotdTitle,
        MotdBody = proto.MotdBody,
        MotdTitleMaxLength = proto.MotdTitleMaxLength,
        MotdBodyMaxLength = proto.MotdBodyMaxLength,
        EmblemHash = proto.EmblemHash,
        Expulsions = expulsions
      };
    }
  }
}
