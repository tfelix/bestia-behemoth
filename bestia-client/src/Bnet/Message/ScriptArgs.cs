using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// The named values a client gathers before asking the server to run a script - a placement position, a
  /// facing, something that was clicked.
  /// </summary>
  /// <remarks>
  /// A GodotObject with one setter per wire type so GDScript can fill a bag without touching protobuf.
  /// Keys come from <c>ScriptArgKeys</c> on both sides; see the <c>ScriptArgs</c> proto for why the
  /// parameters are a bag rather than fields on each request.
  /// <para>
  /// <see cref="SetPosition"/> takes a <b>tile coordinate</b>, not a raw world position - it hands the value
  /// to <see cref="Vec3Convert.ToProto"/>, which rounds. Floor a raycast hit with
  /// <c>TileSpace.world_to_tile</c> first.
  /// </para>
  /// </remarks>
  public partial class ScriptArgs : GodotObject
  {
    private readonly global::Bnet.ScriptArgs _args = new();

    public ScriptArgs SetInt(string key, long value)
    {
      _args.Args.Add(new global::Bnet.ScriptArg { Key = key, IntValue = value });
      return this;
    }

    public ScriptArgs SetFloat(string key, double value)
    {
      _args.Args.Add(new global::Bnet.ScriptArg { Key = key, FloatValue = value });
      return this;
    }

    public ScriptArgs SetText(string key, string value)
    {
      _args.Args.Add(new global::Bnet.ScriptArg { Key = key, TextValue = value });
      return this;
    }

    public ScriptArgs SetBool(string key, bool value)
    {
      _args.Args.Add(new global::Bnet.ScriptArg { Key = key, BoolValue = value });
      return this;
    }

    /// <summary>Adds a tile coordinate. See the remarks on this class about rounding.</summary>
    public ScriptArgs SetPosition(string key, Vector3 tilePosition)
    {
      _args.Args.Add(new global::Bnet.ScriptArg { Key = key, VecValue = Vec3Convert.ToProto(tilePosition) });
      return this;
    }

    public global::Bnet.ScriptArgs ToProto()
    {
      return _args;
    }
  }
}
