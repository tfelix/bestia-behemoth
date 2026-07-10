using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Represents a single entry in an entity's merged skill list.
  /// </summary>
  [GlobalClass]
  public partial class SkillListEntry : GodotObject
  {
    [Export] public ulong SkillId { get; set; }
    [Export] public uint Level { get; set; }

    /// <summary>
    /// Creates a SkillListEntry from protobuf data
    /// </summary>
    public static SkillListEntry FromProto(global::Bnet.SkillListEntry protoEntry)
    {
      return new SkillListEntry
      {
        SkillId = protoEntry.SkillId,
        Level = protoEntry.Level
      };
    }
  }
}
