using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>One quest in the log changed. <see cref="Quest"/> is null when it left the log.</summary>
  [GlobalClass]
  public partial class QuestUpdatedSMSG : ISMSG
  {
    [Export] public ulong QuestId { get; set; }
    [Export] public QuestEntry Quest { get; set; }

    public static QuestUpdatedSMSG FromProto(global::Bnet.QuestUpdatedSMSG proto)
    {
      return new QuestUpdatedSMSG
      {
        QuestId = proto.QuestId,
        Quest = proto.Quest != null ? QuestEntry.FromProto(proto.Quest) : null
      };
    }
  }
}
