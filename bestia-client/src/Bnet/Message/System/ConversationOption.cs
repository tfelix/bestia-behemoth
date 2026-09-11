using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// One thing the player may say next.
  ///
  /// <see cref="TopicId"/> is a topic and not a position in the list, so it is what must be sent
  /// back - the server keeps no conversation state and cannot resolve "the second one".
  /// </summary>
  [GlobalClass]
  public partial class ConversationOption : GodotObject
  {
    [Export]
    public int TopicId { get; set; } = 0;

    [Export]
    public ConversationLine Line { get; set; } = new();

    [Export]
    public global::Bnet.OptionKind Kind { get; set; } = global::Bnet.OptionKind.Talk;

    /// <summary>
    /// <see cref="Kind"/> as lowercase snake_case, because GDScript cannot see a C# enum's members.
    /// See <see cref="DialogArg.KindName"/> for the bug this convention exists to avoid.
    /// </summary>
    [Export]
    public string KindName { get; set; } = "talk";

    public static ConversationOption FromProto(global::Bnet.ConversationOption proto)
    {
      return new ConversationOption
      {
        TopicId = proto.TopicId,
        Line = ConversationLine.FromProto(proto.Line),
        Kind = proto.Kind,
        KindName = EnumName.Of(proto.Kind)
      };
    }
  }
}
