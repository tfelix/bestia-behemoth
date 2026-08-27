using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// A player-owned entity is lying dead where it fell, awaiting a respawn. Unlike a wild mob - which
  /// is destroyed outright and arrives as a VanishEntitySMSG of kind DEATH - a player body stays in
  /// the world, so this is entity state and everyone in range receives it. Getting back up arrives as
  /// this same message with Removed = true.
  /// </summary>
  [GlobalClass]
  public partial class DeadComponentSMSG : EntitySMSG
  {
    [Export] public bool Removed { get; set; }

    public static DeadComponentSMSG FromProto(global::Bnet.DeadComponentSMSG proto)
    {
      return new DeadComponentSMSG()
      {
        EntityId = proto.EntityId,
        Removed = proto.Removed
      };
    }

    public override string ToString()
    {
      return $"DeadComponentSMSG(EntityId={EntityId}, Removed={Removed})";
    }
  }
}
