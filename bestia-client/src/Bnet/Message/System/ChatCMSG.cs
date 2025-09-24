using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// Client-to-server chat message. Used to send chat messages to the server.
  /// </summary>
  [GlobalClass]
  public partial class ChatCMSG : ICMSG
  {
    /// <summary>
    /// The text content of the chat message
    /// </summary>
    [Export]
    public string Text { get; set; } = string.Empty;

    /// <summary>
    /// The chat mode (Party, Guild, Whisper, Public, etc.)
    /// </summary>
    [Export]
    public Mode ChatMode { get; set; } = Mode.Public;

    /// <summary>
    /// Target player name for whisper messages (optional)
    /// </summary>
    [Export]
    public string TargetPlayerName { get; set; } = string.Empty;

    /// <summary>
    /// Creates a new ChatCMSG with default values
    /// </summary>
    public ChatCMSG()
    {
    }

    /// <summary>
    /// Converts the message to a protobuf Envelope for network transmission
    /// </summary>
    /// <returns>The Envelope containing this chat message</returns>
    public override Envelope ToEnvelope()
    {
      var chatCmsg = new global::Bnet.ChatCMSG
      {
        Text = Text,
        Mode = ChatMode
      };

      // Only set target player name for whisper messages
      if (ChatMode == Mode.Whisper && !string.IsNullOrEmpty(TargetPlayerName))
      {
        chatCmsg.TargetPlayerName = TargetPlayerName;
      }

      return new Envelope
      {
        ChatCmsg = chatCmsg
      };
    }
  }
}