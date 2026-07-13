using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Represents a single active buff/debuff in an entity's buff list.
  /// </summary>
  [GlobalClass]
  public partial class BuffListEntry : GodotObject
  {
    [Export] public uint BuffId { get; set; }
    [Export] public uint Level { get; set; }
    [Export] public float RemainingSeconds { get; set; }
    [Export] public bool Debuff { get; set; }

    /// <summary>
    /// Creates a BuffListEntry from protobuf data
    /// </summary>
    public static BuffListEntry FromProto(global::Bnet.BuffEntry protoEntry)
    {
      return new BuffListEntry
      {
        BuffId = protoEntry.BuffId,
        Level = protoEntry.Level,
        RemainingSeconds = protoEntry.RemainingSeconds,
        Debuff = protoEntry.Debuff
      };
    }
  }
}
