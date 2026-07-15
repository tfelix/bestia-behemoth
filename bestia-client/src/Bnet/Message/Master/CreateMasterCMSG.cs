using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Message for creating a new master (character) on the server.
  /// The result is reported back via OperationSuccess / OperationError.
  /// </summary>
  public partial class CreateMasterCMSG : ICMSG
  {
    [Export]
    public string Name { get; set; } = "";

    /// <summary>Selected body type, matching the proto BodyType enum value.</summary>
    [Export]
    public int Body { get; set; } = 0;

    /// <summary>Selected face, matching the proto Face enum value.</summary>
    [Export]
    public int Face { get; set; } = 0;

    /// <summary>Selected hairstyle, matching the proto Hairstyle enum value.</summary>
    [Export]
    public int Hair { get; set; } = 0;

    [Export]
    public Godot.Color HairColor { get; set; } = Colors.Black;

    [Export]
    public Godot.Color SkinColor { get; set; } = Colors.Black;

    public override Envelope ToEnvelope()
    {
      var createMaster = new global::Bnet.CreateMasterCMSG
      {
        Name = Name,
        Body = (global::Bnet.BodyType)Body,
        Face = (global::Bnet.Face)Face,
        Hair = (global::Bnet.Hairstyle)Hair,
        SkinColor = ToProtoColor(SkinColor),
        HairColor = ToProtoColor(HairColor)
      };

      return new Envelope
      {
        CreateMaster = createMaster
      };
    }

    /// <summary>
    /// Converts a Godot Color (float channels 0..1) into a protobuf Color (0..255 channels).
    /// </summary>
    private static global::Bnet.Color ToProtoColor(Godot.Color color)
    {
      return new global::Bnet.Color
      {
        R = (uint)Mathf.Clamp(Mathf.RoundToInt(color.R * 255f), 0, 255),
        G = (uint)Mathf.Clamp(Mathf.RoundToInt(color.G * 255f), 0, 255),
        B = (uint)Mathf.Clamp(Mathf.RoundToInt(color.B * 255f), 0, 255)
      };
    }
  }
}
