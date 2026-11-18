using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// One text, as a translation key and its arguments.
  ///
  /// Never a sentence - the server does not know any language. Resolved through <c>tr()</c> and then
  /// <c>String.format</c>; see <c>Game/UI/Dialog/dialog_text.gd</c>.
  /// </summary>
  [GlobalClass]
  public partial class TextRef : GodotObject
  {
    [Export]
    public string Key { get; set; } = string.Empty;

    /// <summary>
    /// Placeholder values, keyed by <see cref="TextArg.Name"/>. An array rather than a dictionary
    /// for <see cref="DialogSMSG.Args"/>'s reason: Godot dictionaries of GodotObject do not survive
    /// the marshalling as cleanly.
    /// </summary>
    [Export]
    public Godot.Collections.Array<TextArg> Args { get; set; } = new();

    /// <summary>
    /// A text written for this world, to load in the player's language. Empty when there is only the template.
    /// </summary>
    [Export]
    public string TextId { get; set; } = string.Empty;

    public static TextRef FromProto(global::Bnet.TextRef proto)
    {
      var args = new Godot.Collections.Array<TextArg>();
      foreach (var entry in proto.Args)
      {
        args.Add(TextArg.FromProto(entry.Key, entry.Value));
      }

      return new TextRef { Key = proto.Key ?? string.Empty, Args = args, TextId = proto.TextId ?? string.Empty };
    }
  }
}
