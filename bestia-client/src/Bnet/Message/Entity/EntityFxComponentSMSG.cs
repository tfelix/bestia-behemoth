using System;
using System.Reflection;
using Google.Protobuf.Reflection;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// A short effect the server times on an entity's body, as the server's EntityFxKind in capitals:
  /// "WING_VANISH". <c>EntityFxDB</c> maps it to the effect to play, stretched to <see cref="DurationMs"/>.
  /// </summary>
  [GlobalClass]
  public partial class EntityFxComponentSMSG : EntitySMSG
  {
    [Export]
    public string Kind { get; set; } = "";

    [Export]
    public int DurationMs { get; set; }

    public static EntityFxComponentSMSG FromProto(ulong entityId, global::Bnet.EntityFxComponentSMSG proto)
    {
      return new EntityFxComponentSMSG
      {
        EntityId = entityId,
        Kind = KindName(proto.Kind),
        DurationMs = (int)proto.DurationMs
      };
    }

    /// <summary>The name as the proto spells it, which the effect DB is keyed by. Empty for an unknown value.</summary>
    public static string KindName(global::Bnet.EntityFxKind kind)
    {
      if (!Enum.IsDefined(kind))
      {
        return "";
      }

      var field = typeof(global::Bnet.EntityFxKind).GetField(kind.ToString());
      return field?.GetCustomAttribute<OriginalNameAttribute>()?.Name ?? "";
    }
  }
}
