using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// How far along something being built is. Broadcast to everyone in range, so bystanders watch it go up
  /// too. The site finishing or being destroyed arrives as this same message with Removed = true.
  /// </summary>
  /// <remarks>
  /// <see cref="Active"/> is why this is not <see cref="CastingComponentSMSG"/>, which a craft does reuse:
  /// construction advances only while somebody works on it, so the client may only interpolate
  /// RemainingSeconds between corrections while this is true.
  /// </remarks>
  [GlobalClass]
  public partial class ConstructionComponentSMSG : EntitySMSG
  {
    [Export] public float RemainingSeconds { get; set; }
    [Export] public float TotalSeconds { get; set; }
    [Export] public bool Active { get; set; }
    [Export] public bool Removed { get; set; }

    /// <summary>How much of the work is done, 0 to 1.</summary>
    public float Progress =>
      TotalSeconds > 0f ? Mathf.Clamp((TotalSeconds - RemainingSeconds) / TotalSeconds, 0f, 1f) : 1f;

    public static ConstructionComponentSMSG FromProto(global::Bnet.ConstructionComponentSMSG proto)
    {
      return new ConstructionComponentSMSG()
      {
        EntityId = proto.EntityId,
        RemainingSeconds = proto.RemainingSeconds,
        TotalSeconds = proto.TotalSeconds,
        Active = proto.Active,
        Removed = proto.Removed
      };
    }

    public override string ToString()
    {
      return $"ConstructionComponentSMSG(EntityId={EntityId}, RemainingSeconds={RemainingSeconds}, TotalSeconds={TotalSeconds}, Active={Active}, Removed={Removed})";
    }
  }
}
