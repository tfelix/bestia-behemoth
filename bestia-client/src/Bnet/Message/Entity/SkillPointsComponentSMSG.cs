using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Skill points component message from server containing a bestia master's unspent skill points.
  /// </summary>
  [GlobalClass]
  public partial class SkillPointsComponentSMSG : EntitySMSG
  {
    [Export] public uint Points { get; set; }

    public SkillPointsComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create SkillPointsComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoSkillPoints">The protobuf SkillPointsSMSG message from the server</param>
    /// <returns>A new SkillPointsComponentSMSG instance</returns>
    public static SkillPointsComponentSMSG FromProto(ulong entityId, global::Bnet.SkillPointsSMSG protoSkillPoints)
    {
      return new SkillPointsComponentSMSG()
      {
        EntityId = entityId,
        Points = protoSkillPoints.Points
      };
    }

    public override string ToString()
    {
      return $"SkillPointsComponentSMSG(EntityId={EntityId}, Points={Points})";
    }
  }
}
