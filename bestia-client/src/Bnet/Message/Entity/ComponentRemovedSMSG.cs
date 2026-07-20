using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Local mirror of the proto RemovableComponent enum. Keep in sync with
  /// component_removed_smsg.proto and the server-side RemovableComponentType.
  /// </summary>
  public enum RemovableComponent
  {
    Unspecified = 0,
    LogoutIntent = 1,
    Casting = 2
  }

  /// <summary>
  /// Generic "a synced component was removed from an entity" notification (as opposed to the whole
  /// entity vanishing). For logout, a removed LOGOUT_INTENT means the pending logout was cancelled.
  /// </summary>
  [GlobalClass]
  public partial class ComponentRemovedSMSG : EntitySMSG
  {
    [Export] public RemovableComponent Component { get; set; }

    public static ComponentRemovedSMSG FromProto(global::Bnet.ComponentRemovedSMSG proto)
    {
      return new ComponentRemovedSMSG()
      {
        EntityId = proto.EntityId,
        Component = MapComponentFromProto(proto.Component)
      };
    }

    /// <summary>True if this is the removal of the logout-intent component (= logout aborted).
    /// Exposed as a method because nested C# enums are awkward to reference from GDScript.</summary>
    public bool IsLogoutIntent() => Component == RemovableComponent.LogoutIntent;

    /// <summary>True if this ends a cast — either completed or interrupted, which look identical on
    /// the wire since visually both just remove the cast bar.</summary>
    public bool IsCasting() => Component == RemovableComponent.Casting;

    private static RemovableComponent MapComponentFromProto(global::Bnet.RemovableComponent proto)
    {
      return proto switch
      {
        global::Bnet.RemovableComponent.LogoutIntent => RemovableComponent.LogoutIntent,
        global::Bnet.RemovableComponent.Casting => RemovableComponent.Casting,
        _ => RemovableComponent.Unspecified
      };
    }

    public override string ToString()
    {
      return $"ComponentRemovedSMSG(EntityId={EntityId}, Component={Component})";
    }
  }
}
