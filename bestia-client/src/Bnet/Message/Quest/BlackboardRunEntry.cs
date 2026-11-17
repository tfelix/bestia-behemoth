using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>One run a quest may be posted for, and its fee in percent of the pay.</summary>
  [GlobalClass]
  public partial class BlackboardRunEntry : GodotObject
  {
    [Export] public int Days { get; set; }
    [Export] public int FeePercent { get; set; }

    public static BlackboardRunEntry FromProto(global::Bnet.BlackboardRun proto)
    {
      return new BlackboardRunEntry { Days = (int)proto.Days, FeePercent = (int)proto.FeePercent };
    }
  }
}
