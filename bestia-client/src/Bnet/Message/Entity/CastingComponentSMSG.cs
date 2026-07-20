using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// An entity is channelling a skill. Broadcast to everyone in range so bystanders see the cast bar
  /// too. Re-sent while the cast runs; the client interpolates RemainingSeconds locally in between.
  /// The end of the cast — completed or interrupted — arrives as a ComponentRemovedSMSG for Casting.
  /// </summary>
  [GlobalClass]
  public partial class CastingComponentSMSG : EntitySMSG
  {
    [Export] public float RemainingSeconds { get; set; }
    [Export] public float TotalSeconds { get; set; }

    public static CastingComponentSMSG FromProto(global::Bnet.CastingComponentSMSG proto)
    {
      return new CastingComponentSMSG()
      {
        EntityId = proto.EntityId,
        RemainingSeconds = proto.RemainingSeconds,
        TotalSeconds = proto.TotalSeconds
      };
    }

    public override string ToString()
    {
      return $"CastingComponentSMSG(EntityId={EntityId}, RemainingSeconds={RemainingSeconds}, TotalSeconds={TotalSeconds})";
    }
  }
}
