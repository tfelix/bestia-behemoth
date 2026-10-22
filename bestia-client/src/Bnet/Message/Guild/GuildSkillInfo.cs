using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>One skill of the guild skill tree in a <see cref="GuildInfoSMSG"/>. Name, icon and description come from the Attack DB.</summary>
  [GlobalClass]
  public partial class GuildSkillInfo : GodotObject
  {
    [Export] public ulong SkillId { get; set; }

    [Export] public uint Level { get; set; }

    [Export] public uint MaxLevel { get; set; }

    /// <summary>Pairs of skill id and required level, flattened because GDScript reads no nested C# types.</summary>
    [Export] public Godot.Collections.Array<ulong> PrerequisiteSkillIds { get; set; } = [];

    [Export] public Godot.Collections.Array<uint> PrerequisiteLevels { get; set; } = [];

    public static GuildSkillInfo FromProto(global::Bnet.GuildSkillNode proto)
    {
      var info = new GuildSkillInfo
      {
        SkillId = proto.SkillId,
        Level = proto.Level,
        MaxLevel = proto.MaxLevel
      };

      foreach (var prerequisite in proto.Prerequisites)
      {
        info.PrerequisiteSkillIds.Add(prerequisite.SkillId);
        info.PrerequisiteLevels.Add(prerequisite.Level);
      }

      return info;
    }
  }
}
