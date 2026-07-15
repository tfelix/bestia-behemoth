using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Server-driven logout countdown for the local player's master. Re-sent periodically; the client
  /// interpolates locally between updates. Not an EntitySMSG on purpose so it can be routed straight
  /// to the logout UI rather than through the per-entity component pipeline.
  /// </summary>
  [GlobalClass]
  public partial class LogoutIntentSMSG : ISMSG
  {
    [Export] public ulong EntityId { get; set; }
    [Export] public float RemainingSeconds { get; set; }

    public LogoutIntentSMSG()
    {
    }

    public static LogoutIntentSMSG FromProto(global::Bnet.LogoutIntentSMSG proto)
    {
      return new LogoutIntentSMSG()
      {
        EntityId = proto.EntityId,
        RemainingSeconds = proto.RemainingSeconds
      };
    }

    public override string ToString()
    {
      return $"LogoutIntentSMSG(EntityId={EntityId}, RemainingSeconds={RemainingSeconds})";
    }
  }
}
