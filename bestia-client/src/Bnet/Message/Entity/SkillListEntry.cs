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

    /// <summary>The Potency band of a learned skill: 0 for none, then Faint = 1 to Overwhelming = 5.</summary>
    [Export] public uint PotencyBand { get; set; }

    /// <summary>Held for a single cast, like a spell copied with Plagiarism, rather than learned.</summary>
    [Export] public bool Temporary { get; set; }

    /// <summary>
    /// Creates a SkillListEntry from protobuf data
    /// </summary>
    public static SkillListEntry FromProto(global::Bnet.SkillListEntry protoEntry)
    {
      return new SkillListEntry
      {
        SkillId = protoEntry.SkillId,
        Level = protoEntry.Level,
        PotencyBand = protoEntry.PotencyBand,
        Temporary = protoEntry.Temporary
      };
    }
  }
}
