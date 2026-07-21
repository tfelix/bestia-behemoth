using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Server-driven logout countdown for the local player's master. Re-sent periodically; the client
  /// interpolates locally between updates. Cancellation arrives as this same message with
  /// Removed = true. It IS an EntitySMSG (carries EntityId), but is routed straight to the logout UI
  /// rather than through the generic per-entity dispatch — see the ordering note in
  /// connection_manager.gd's message dispatch.
  /// </summary>
  [GlobalClass]
  public partial class LogoutIntentComponentSMSG : EntitySMSG
  {
    [Export] public float RemainingSeconds { get; set; }
    [Export] public bool Removed { get; set; }

    public static LogoutIntentComponentSMSG FromProto(global::Bnet.LogoutIntentSMSG proto)
    {
      return new LogoutIntentComponentSMSG()
      {
        EntityId = proto.EntityId,
        RemainingSeconds = proto.RemainingSeconds,
        Removed = proto.Removed
      };
    }

    public override string ToString()
    {
      return $"LogoutIntentSMSG(EntityId={EntityId}, RemainingSeconds={RemainingSeconds}, Removed={Removed})";
    }
  }
}
