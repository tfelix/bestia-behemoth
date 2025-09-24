using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message for updating an entity's complete master visual component with all visual properties.
  /// Contains entity ID, visual ID, colors, face, body type, and hairstyle.
  /// </summary>
  [GlobalClass]
  public partial class MasterVisualComponentSMSG : EntitySMSG
  {
    [Export]
    public int VisualId { get; set; } = 0;

    [Export]
    public Godot.Color SkinColor { get; set; } = Colors.White;

    [Export]
    public Godot.Color HairColor { get; set; } = Colors.Black;

    [Export]
    public int Face { get; set; } = 0; // Maps to Face enum

    [Export]
    public int Body { get; set; } = 0; // Maps to BodyType enum

    [Export]
    public int Hair { get; set; } = 0; // Maps to Hairstyle enum

    public static MasterVisualComponentSMSG FromProto(global::Bnet.MasterVisualComponentSMSG protoMasterVisual)
    {
      // Convert protobuf Color to Godot Color
      var skinColor = new Godot.Color(
        protoMasterVisual.SkinColor.R / 255.0f,
        protoMasterVisual.SkinColor.G / 255.0f,
        protoMasterVisual.SkinColor.B / 255.0f
      );

      var hairColor = new Godot.Color(
        protoMasterVisual.HairColor.R / 255.0f,
        protoMasterVisual.HairColor.G / 255.0f,
        protoMasterVisual.HairColor.B / 255.0f
      );

      return new MasterVisualComponentSMSG
      {
        EntityId = protoMasterVisual.EntityId,
        SkinColor = skinColor,
        HairColor = hairColor,
        Face = (int)protoMasterVisual.Face,
        Body = (int)protoMasterVisual.Body,
        Hair = (int)protoMasterVisual.Hair
      };
    }
  }
}
