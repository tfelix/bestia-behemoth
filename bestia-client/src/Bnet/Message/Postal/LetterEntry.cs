using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>A letter in the played master's mailbox.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file, see <c>ShopEntry</c>.</remarks>
  [GlobalClass]
  public partial class LetterEntry : GodotObject
  {
    [Export] public ulong LetterId { get; set; }
    [Export] public string SenderName { get; set; } = "";
    [Export] public string Subject { get; set; } = "";
    [Export] public string Body { get; set; } = "";
    [Export] public long SentAtEpochSeconds { get; set; }
    [Export] public long ExpiresAtEpochSeconds { get; set; }
    [Export] public bool Read { get; set; }

    public static LetterEntry FromProto(global::Bnet.Letter proto)
    {
      return new LetterEntry
      {
        LetterId = proto.LetterId,
        SenderName = proto.SenderName,
        Subject = proto.Subject,
        Body = proto.Body,
        SentAtEpochSeconds = proto.SentAtEpochSeconds,
        ExpiresAtEpochSeconds = proto.ExpiresAtEpochSeconds,
        Read = proto.Read
      };
    }
  }
}
