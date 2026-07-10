using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// The full merged skill list (regular/fixed catalog + individually learned) of an entity.
  /// </summary>
  [GlobalClass]
  public partial class SkillListSMSG : EntitySMSG
  {
    [Export] public Godot.Collections.Array<SkillListEntry> Skills { get; set; } = [];

    public SkillListSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create SkillListSMSG from protobuf message
    /// </summary>
    /// <param name="protoSkillList">The protobuf SkillListSMSG message from the server</param>
    /// <returns>A new SkillListSMSG instance</returns>
    public static SkillListSMSG FromProto(global::Bnet.SkillListSMSG protoSkillList)
    {
      var skillList = new SkillListSMSG()
      {
        EntityId = protoSkillList.EntityId
      };

      foreach (var protoSkill in protoSkillList.Skills)
      {
        skillList.Skills.Add(SkillListEntry.FromProto(protoSkill));
      }

      return skillList;
    }

    public override string ToString()
    {
      return $"SkillListSMSG(EntityId={EntityId}, SkillCount={Skills.Count})";
    }
  }
}
