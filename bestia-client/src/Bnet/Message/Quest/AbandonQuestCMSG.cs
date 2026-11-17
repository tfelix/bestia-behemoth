using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>Drops an accepted quest from the log, or clears one that has ended.</summary>
  public partial class AbandonQuestCMSG : ICMSG
  {
    public ulong QuestId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { AbandonQuest = new global::Bnet.AbandonQuestCMSG { QuestId = QuestId } };
    }
  }
}
