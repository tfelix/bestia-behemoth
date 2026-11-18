using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// Which flavour of value a <see cref="TextArg"/> carries. Everything except
  /// <see cref="Text"/> is a reference the client is expected to resolve to a localized name
  /// itself, so dialog text stays translatable.
  /// </summary>
  public enum TextArgKind
  {
    Text = 0,
    Number = 1,
    Entity = 2,
    Item = 3,
    Skill = 4,

    /// <summary>
    /// A translation key to resolve in turn and substitute. What lets a line say
    /// "{who} spoke of {what}" with {what} localized rather than left in English.
    /// </summary>
    Token = 5,

    /// <summary>
    /// A proper noun, already rendered and deliberately not translated. Generated names are built
    /// from invented stems that belong to no language, so there is nothing in one to translate.
    /// </summary>
    Name = 6,
    Species = 7
  }

  /// <summary>
  /// One placeholder value for a text, e.g. <c>masterName</c> filling <c>{masterName}</c>.
  ///
  /// All the numeric kinds (Number/Entity/Item/Skill/Species) land in <see cref="Number"/> - only
  /// <see cref="Kind"/> says how to interpret it. That keeps the GDScript side to a single
  /// switch; see <c>Game/UI/Dialog/dialog_text.gd</c>.
  /// </summary>
  [GlobalClass]
  public partial class TextArg : GodotObject
  {
    /// <summary>
    /// The placeholder name as declared in the server's dialogs.yml, without braces.
    /// </summary>
    [Export]
    public string Name { get; set; } = string.Empty;

    [Export]
    public TextArgKind Kind { get; set; } = TextArgKind.Text;

    /// <summary>
    /// <see cref="Kind"/> as a lowercase snake_case string, for the GDScript side. GDScript cannot see
    /// a C# enum's members, so matching on this avoids re-declaring the ordinals over there by hand -
    /// which is exactly how the operation-error codes ended up duplicated in
    /// <c>create_new_master.gd</c>. Single words as it happens, which is why this one property survived
    /// the spelling bug <see cref="EnumName"/> now fixes for all five.
    /// </summary>
    [Export]
    public string KindName { get; set; } = "text";

    /// <summary>
    /// Set for the three string kinds: <see cref="TextArgKind.Text"/>,
    /// <see cref="TextArgKind.Token"/> and <see cref="TextArgKind.Name"/>. Only
    /// <see cref="Kind"/> says whether to print it, look it up, or print it untranslated.
    /// </summary>
    [Export]
    public string Text { get; set; } = string.Empty;

    /// <summary>
    /// The value for every non-text kind: a plain number, or an entity/item/skill/species id.
    /// </summary>
    [Export]
    public long Number { get; set; } = 0;

    public static TextArg FromProto(string name, global::Bnet.TextArg proto)
    {
      var arg = new TextArg { Name = name };

      switch (proto.ValueCase)
      {
        case global::Bnet.TextArg.ValueOneofCase.Text:
          arg.SetKind(TextArgKind.Text);
          arg.Text = proto.Text ?? string.Empty;
          break;
        case global::Bnet.TextArg.ValueOneofCase.Number:
          arg.SetKind(TextArgKind.Number);
          arg.Number = proto.Number;
          break;
        case global::Bnet.TextArg.ValueOneofCase.EntityId:
          arg.SetKind(TextArgKind.Entity);
          arg.Number = (long)proto.EntityId;
          break;
        case global::Bnet.TextArg.ValueOneofCase.ItemId:
          arg.SetKind(TextArgKind.Item);
          arg.Number = proto.ItemId;
          break;
        case global::Bnet.TextArg.ValueOneofCase.SkillId:
          arg.SetKind(TextArgKind.Skill);
          arg.Number = proto.SkillId;
          break;
        case global::Bnet.TextArg.ValueOneofCase.SpeciesId:
          arg.SetKind(TextArgKind.Species);
          arg.Number = proto.SpeciesId;
          break;
        case global::Bnet.TextArg.ValueOneofCase.Token:
          arg.SetKind(TextArgKind.Token);
          arg.Text = proto.Token ?? string.Empty;
          break;
        case global::Bnet.TextArg.ValueOneofCase.Name:
          arg.SetKind(TextArgKind.Name);
          arg.Text = proto.Name ?? string.Empty;
          break;
        default:
          // An arg with nothing set at all: keep the name so the placeholder is still replaced
          // (with an empty string) instead of leaving a raw brace on screen.
          GD.PushWarning($"TextArg '{name}' carried no value");
          break;
      }

      return arg;
    }

    private void SetKind(TextArgKind kind)
    {
      Kind = kind;
      KindName = EnumName.Of(kind);
    }
  }
}
