using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// One thing said, as a translation key and its arguments.
  ///
  /// Never a sentence - the server does not know any language. Resolved exactly the way a dialog's
  /// text is, through <c>tr()</c> and then <c>String.format</c>; see <c>Game/UI/Dialog/dialog_text.gd</c>.
  /// </summary>
  [GlobalClass]
  public partial class ConversationLine : GodotObject
  {
    [Export]
    public string Key { get; set; } = string.Empty;

    /// <summary>
    /// Placeholder values, keyed by <see cref="DialogArg.Name"/>. An array rather than a dictionary
    /// for <see cref="DialogSMSG.Args"/>'s reason: Godot dictionaries of GodotObject do not survive
    /// the marshalling as cleanly.
    /// </summary>
    [Export]
    public Godot.Collections.Array<DialogArg> Args { get; set; } = new();

    public static ConversationLine FromProto(global::Bnet.Line proto)
    {
      var args = new Godot.Collections.Array<DialogArg>();
      foreach (var entry in proto.Args)
      {
        args.Add(DialogArg.FromProto(entry.Key, entry.Value));
      }

      return new ConversationLine { Key = proto.Key ?? string.Empty, Args = args };
    }
  }
}
