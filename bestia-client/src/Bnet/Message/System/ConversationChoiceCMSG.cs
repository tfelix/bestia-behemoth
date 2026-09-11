using Bnet;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// The option the player picked. Carries the entity as well as the topic because there is no
  /// conversation to have an id - the server re-resolves both ends and re-checks range every step.
  /// </summary>
  public partial class ConversationChoiceCMSG : ICMSG
  {
    public ulong EntityId { get; set; }

    public int TopicId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        ConversationChoice = new global::Bnet.ConversationChoiceCMSG
        {
          EntityId = EntityId,
          TopicId = TopicId
        }
      };
    }
  }
}
