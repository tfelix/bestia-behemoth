using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message for updating an entity's animation component.
  /// Contains the entity ID and the animation clip name it should currently play
  /// (matches the AnimationPlayer clip names used by the visual scenes, e.g. "Idle"/"Walk").
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
          global::Bnet.AnimationKind.Walk => "Walk",
          _ => "Idle"
        }
      };
    }
  }
}
