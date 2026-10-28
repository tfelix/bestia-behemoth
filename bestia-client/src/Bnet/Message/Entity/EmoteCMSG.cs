using System;
using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Asks for an emote on the entity the player controls. The answer is that entity's
  /// <see cref="AnimationComponentSMSG"/>, which everyone around it sees.
  /// </summary>
  public partial class EmoteCMSG : ICMSG
  {
    /// <summary>The emote's AnimationKind name, as <see cref="AnimationComponentSMSG.Kind"/> spells it: "CHEER".</summary>
    [Export] public string Kind { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        Emote = new global::Bnet.EmoteCMSG { Kind = ToKind(Kind) }
      };
    }

    public static AnimationKind ToKind(string name)
    {
      if (Enum.TryParse(name, ignoreCase: true, out AnimationKind kind) && Enum.IsDefined(kind))
      {
        return kind;
      }

      throw new ArgumentException($"'{name}' is not an AnimationKind");
    }
  }
}
