using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Represents a single active buff/debuff in an entity's buff list.
  /// </summary>
  [GlobalClass]
  public partial class StatusEffectListEntry : GodotObject
  {
    [Export] public uint EffectId { get; set; }
    [Export] public uint Level { get; set; }
    [Export] public float RemainingSeconds { get; set; }
    [Export] public bool Debuff { get; set; }

    /// <summary>
    /// Creates a BuffListEntry from protobuf data
    /// </summary>
    public static StatusEffectListEntry FromProto(global::Bnet.StatusEffectEntry protoEntry)
    {
      return new StatusEffectListEntry
      {
        EffectId = protoEntry.EffectId,
        Level = protoEntry.Level,
        RemainingSeconds = protoEntry.RemainingSeconds,
        Debuff = protoEntry.Debuff
      };
    }
  }
}
