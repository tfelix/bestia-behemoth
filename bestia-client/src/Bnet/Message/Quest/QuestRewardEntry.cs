using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  [GlobalClass]
  public partial class QuestRewardEntry : GodotObject
  {
    [Export] public string KindName { get; set; } = "";
    [Export] public long Amount { get; set; }

    public static QuestRewardEntry FromProto(global::Bnet.QuestReward proto)
    {
      return new QuestRewardEntry { KindName = EnumName.Of(proto.Kind), Amount = proto.Amount };
    }
  }
}
