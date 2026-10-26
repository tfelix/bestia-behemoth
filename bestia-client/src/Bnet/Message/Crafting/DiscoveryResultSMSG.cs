using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>What a discovery attempt found. See <c>discovery_result_smsg.proto</c>.</summary>
  [GlobalClass]
  public partial class DiscoveryResultSMSG : ISMSG
  {
    /// <summary>"nothing", "learned", "slipped" or "beyond_skill", so GDScript can match without the enum.</summary>
    [Export] public string OutcomeName { get; set; } = "nothing";

    /// <summary>The blueprint learned; 0 for every other outcome.</summary>
    [Export] public ulong RecipeId { get; set; }

    [Export] public Godot.Collections.Array<AxisHint> Hints { get; set; } = [];

    public static DiscoveryResultSMSG FromProto(global::Bnet.DiscoveryResultSMSG proto)
    {
      var message = new DiscoveryResultSMSG
      {
        OutcomeName = proto.Outcome switch
        {
          global::Bnet.DiscoveryOutcome.Learned => "learned",
          global::Bnet.DiscoveryOutcome.Slipped => "slipped",
          global::Bnet.DiscoveryOutcome.BeyondSkill => "beyond_skill",
          _ => "nothing"
        },
        RecipeId = proto.RecipeId
      };

      foreach (var hint in proto.Hints)
      {
        message.Hints.Add(new AxisHint { Axis = (int)hint.Axis, TooHigh = hint.TooHigh });
      }

      return message;
    }

    public override string ToString() => $"DiscoveryResultSMSG(Outcome={OutcomeName}, RecipeId={RecipeId})";
  }
}
