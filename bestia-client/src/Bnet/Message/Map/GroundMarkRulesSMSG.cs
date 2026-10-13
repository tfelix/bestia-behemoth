using System.IO;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// The numbers the server lays footprints, wear and blood by, sent once so the client can lay the same ones.
  /// </summary>
  /// <remarks>
  /// A <see cref="MapSMSG"/> for <see cref="WorldTimeSMSG"/>'s reason: it describes the world, not an entity, so
  /// <c>EntityManager</c> must never see it. <c>ChunkStreamManager</c> keeps the decoded <see cref="Rules"/>.
  /// </remarks>
  [GlobalClass]
  public partial class GroundMarkRulesSMSG : MapSMSG
  {
    public GroundRules Rules { get; private set; }

    /// <exception cref="InvalidDataException">on a message missing its wear or blood rules, or with numbers no rule can use.</exception>
    public static GroundMarkRulesSMSG FromProto(global::Bnet.GroundMarkRulesSMSG proto)
    {
      return new GroundMarkRulesSMSG { Rules = GroundRules.FromProto(proto) };
    }

    public override string ToString() =>
      $"GroundMarkRulesSMSG(print {Rules.StampLifetimeMillis} ms x{Rules.StampRingCapacity}, " +
      $"wear fade {Rules.Wear.FadeSeconds} s, blood fade {Rules.Blood.FadeSeconds} s)";
  }
}
