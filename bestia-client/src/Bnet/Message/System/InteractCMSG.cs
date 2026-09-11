using Bnet;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// Asks the server to deal with an entity. What that means is the server's decision - talking to
  /// a townsperson today. Answered with a ConversationSMSG, or with nothing at all if the target is
  /// not something this player can interact with.
  /// </summary>
  public partial class InteractCMSG : ICMSG
  {
    public ulong EntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        Interact = new global::Bnet.InteractCMSG
        {
          EntityId = EntityId
        }
      };
    }
  }
}
