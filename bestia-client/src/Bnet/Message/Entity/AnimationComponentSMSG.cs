using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// A pose the client could not have worked out for itself, as an AnimationPlayer clip name.
  /// <para>
  /// Deliberately narrow: walking is not on this list, because Entity plays its walk clip off its own
  /// movement prediction and always did. "Idle" here means "nothing overriding" - hand the pose back
  /// to that local heuristic - rather than "stand still".
  /// </para>
  /// </summary>
  [GlobalClass]
  public partial class AnimationComponentSMSG : EntitySMSG
  {
    [Export]
    public string Kind { get; set; } = "Idle";

    public static AnimationComponentSMSG FromProto(global::Bnet.AnimationComponentSMSG protoAnimation)
    {
      return new AnimationComponentSMSG
      {
        EntityId = protoAnimation.EntityId,
        Kind = protoAnimation.Kind switch
        {
          global::Bnet.AnimationKind.Sleep => "Sleep",
          _ => "Idle"
        }
      };
    }
  }
}
