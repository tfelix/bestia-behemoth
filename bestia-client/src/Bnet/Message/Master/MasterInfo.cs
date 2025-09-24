using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Godot-friendly wrapper for MasterInfo protobuf data
  /// </summary>
  [GlobalClass]
  public partial class MasterInfo : GodotObject
  {
    [Export]
    public ulong MasterId { get; set; }

    [Export]
    public string Name { get; set; } = string.Empty;

    [Export]
    public uint Level { get; set; }

    [Export]
    public Vector3 Position { get; set; }

    [Export]
    public int BodyType { get; set; }

    [Export]
    public int Face { get; set; }

    [Export]
    public Godot.Color SkinColor { get; set; }

    [Export]
    public Godot.Color HairColor { get; set; }

    [Export]
    public Godot.Collections.Array<BestiaInfo> Bestias { get; set; } = new();

    /// <summary>
    /// Creates a MasterInfo from protobuf data
    /// </summary>
    /// <param name="protoMasterInfo">The protobuf MasterInfo object</param>
    /// <returns>Godot-friendly MasterInfo object</returns>
    public static MasterInfo FromProto(global::Bnet.MasterInfo protoMasterInfo)
    {
      var masterInfo = new MasterInfo
      {
        MasterId = protoMasterInfo.MasterId,
        Name = protoMasterInfo.Name,
        Level = protoMasterInfo.Level,
        BodyType = (int)protoMasterInfo.Body,
        Face = (int)protoMasterInfo.Face
      };

      // Convert Vec3 to Godot Vector3 (swap Y and Z: server z-up to Godot y-up)
      if (protoMasterInfo.Position != null)
      {
        masterInfo.Position = new Vector3(
          (float)protoMasterInfo.Position.X,
          (float)protoMasterInfo.Position.Z,
          (float)protoMasterInfo.Position.Y
        );
      }

      // Convert protobuf Color to Godot Color
      if (protoMasterInfo.SkinColor != null)
      {
        masterInfo.SkinColor = new Godot.Color(
          protoMasterInfo.SkinColor.R / 255.0f,
          protoMasterInfo.SkinColor.G / 255.0f,
          protoMasterInfo.SkinColor.B / 255.0f
        );
      }

      if (protoMasterInfo.HairColor != null)
      {
        masterInfo.HairColor = new Godot.Color(
          protoMasterInfo.HairColor.R / 255.0f,
          protoMasterInfo.HairColor.G / 255.0f,
          protoMasterInfo.HairColor.B / 255.0f
        );
      }

      // Convert bestias
      foreach (var protoBestia in protoMasterInfo.Bestias)
      {
        masterInfo.Bestias.Add(BestiaInfo.FromProto(protoBestia));
      }

      return masterInfo;
    }
  }
}