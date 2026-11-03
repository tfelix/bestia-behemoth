using System;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// A pose the client could not have worked out for itself, as the server's AnimationKind in capitals: "SLEEP",
  /// or an emote such as "CHEER". Each visual decides which clips that means for its body.
  /// <para>
  /// Deliberately narrow: walking is not on this list, because Entity plays its walk clip off its own
  /// movement prediction and always did. "IDLE" here means "nothing overriding" - hand the pose back
  /// to that local heuristic - rather than "stand still".
  /// </para>
  /// </summary>
  [GlobalClass]
  public partial class AnimationComponentSMSG : EntitySMSG
  {
    public const string None = "IDLE";

    [Export]
    public string Kind { get; set; } = None;

    public static AnimationComponentSMSG FromProto(ulong entityId, global::Bnet.AnimationComponentSMSG protoAnimation)
    {
      return new AnimationComponentSMSG
      {
        EntityId = entityId,
        Kind = KindName(protoAnimation.Kind)
      };
    }

    /// <summary>
    /// The proto's own spelling: "PICK_UP", not the "PICKUP" that upper-casing the C# member <c>PickUp</c> welds
    /// together, which no visual matched, so nobody was ever seen bending down - see <see cref="EnumName"/>. A value
    /// from a newer server reads as IDLE, which plays nothing rather than something wrong.
    /// </summary>
    public static string KindName(global::Bnet.AnimationKind kind)
    {
      return Enum.IsDefined(kind) ? EnumName.Of(kind).ToUpperInvariant() : None;
    }
  }
}
