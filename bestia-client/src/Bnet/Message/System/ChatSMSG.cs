using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// Server-to-client chat message. Represents chat messages received from the server.
  /// </summary>
  [GlobalClass]
  public partial class ChatSMSG : ISMSG
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
    /// The name of the player who sent the message (optional)
    /// </summary>
    [Export]
    public string SenderName { get; set; } = string.Empty;

    /// <summary>
    /// The name of the player who sent the message (optional)
    /// </summary>
    [Export]
    public ulong SenderEntityId { get; set; } = 0;

    /// <summary>
    /// Creates a new ChatSMSG with default values
    /// </summary>
    public ChatSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create ChatSMSG from protobuf message
    /// </summary>
    /// <param name="protoChatSmsg">The protobuf ChatSMSG message from the server</param>
    /// <returns>A new ChatSMSG instance</returns>
    public static ChatSMSG FromProto(global::Bnet.ChatSMSG protoChatSmsg)
    {
      return new ChatSMSG()
      {
        Text = protoChatSmsg.Text ?? string.Empty,
        ChatMode = protoChatSmsg.Mode,
        SenderName = protoChatSmsg.SenderName ?? string.Empty,
        SenderEntityId = protoChatSmsg.SenderEntityId
      };
    }

    /// <summary>
    /// Checks if this is a whisper message
    /// </summary>
    /// <returns>True if this is a whisper message</returns>
    public bool IsWhisper()
    {
      return ChatMode == Mode.Whisper;
    }

    /// <summary>
    /// Checks if this is a party message
    /// </summary>
    /// <returns>True if this is a party message</returns>
    public bool IsParty()
    {
      return ChatMode == Mode.Party;
    }

    /// <summary>
    /// Checks if this is a guild message
    /// </summary>
    /// <returns>True if this is a guild message</returns>
    public bool IsGuild()
    {
      return ChatMode == Mode.Guild;
    }

    /// <summary>
    /// Checks if this is a public message
    /// </summary>
    /// <returns>True if this is a public message</returns>
    public bool IsPublic()
    {
      return ChatMode == Mode.Public;
    }

    /// <summary>
    /// Checks if this is an error message
    /// </summary>
    /// <returns>True if this is an error message</returns>
    public bool IsError()
    {
      return ChatMode == Mode.Error;
    }

    /// <summary>
    /// Checks if this is a GM (Game Master) message
    /// </summary>
    /// <returns>True if this is a GM message</returns>
    public bool IsGM()
    {
      return ChatMode == Mode.Gm;
    }

    /// <summary>
    /// Checks if this is a broadcast message
    /// </summary>
    /// <returns>True if this is a broadcast message</returns>
    public bool IsBroadcast()
    {
      return ChatMode == Mode.Broadcast;
    }
  }
}