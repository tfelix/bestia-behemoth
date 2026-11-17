using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>The played master's whole quest log: what it accepted and what it posted.</summary>
  [GlobalClass]
  public partial class QuestLogSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<QuestEntry> Quests { get; set; } = [];

    public static QuestLogSMSG FromProto(global::Bnet.QuestLogSMSG proto)
    {
      var quests = new Godot.Collections.Array<QuestEntry>();
      foreach (var quest in proto.Quests)
      {
        quests.Add(QuestEntry.FromProto(quest));
      }

      return new QuestLogSMSG { Quests = quests };
    }
  }
}
