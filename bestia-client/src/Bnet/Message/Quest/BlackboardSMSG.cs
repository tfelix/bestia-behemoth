using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>A blackboard's window: every open quest as the played master sees it. Every board shows the same list.</summary>
  [GlobalClass]
  public partial class BlackboardSMSG : ISMSG
  {
    [Export] public ulong BoardEntityId { get; set; }
    [Export] public string PlaceName { get; set; } = "";
    [Export] public Godot.Collections.Array<QuestEntry> Quests { get; set; } = [];

    public static BlackboardSMSG FromProto(global::Bnet.BlackboardSMSG proto)
    {
      var board = new BlackboardSMSG { BoardEntityId = proto.BoardEntityId, PlaceName = proto.PlaceName };
      foreach (var quest in proto.Quests)
      {
        board.Quests.Add(QuestEntry.FromProto(quest));
      }

      return board;
    }
  }
}
