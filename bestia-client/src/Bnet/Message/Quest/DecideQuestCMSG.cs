using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>Picks one choice at the decide step of a quest. A pick is final.</summary>
  public partial class DecideQuestCMSG : ICMSG
  {
    public ulong QuestId { get; set; }

    public string ChoiceId { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      return new Envelope { DecideQuest = new global::Bnet.DecideQuestCMSG { QuestId = QuestId, ChoiceId = ChoiceId } };
    }
  }
}
