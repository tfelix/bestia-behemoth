using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Puts the weather onto everything that is not terrain, by laying <c>weather_overlay.gdshader</c> over it.
  /// </summary>
  /// <remarks>
  /// An overlay is a second draw per object, so it is attached only while the ground is wet or snow is
  /// falling or lying somewhere, and taken off again in dry weather. Objects register once when they are built;
  /// <see cref="WeatherState"/> switches the whole group.
  /// </remarks>
  public static class WeatherOverlay
  {
    private const string ShaderPath = "res://Game/World/Shader/weather_overlay.gdshader";

    private static readonly StringName Group = "weather_overlay";
    private static readonly StringName MovingMeta = "weather_overlay_moving";
    private static readonly StringName SnowShare = "snow_share";
    private static readonly StringName CapMetres = "cap_metres";

    private static ShaderMaterial _still;
    private static ShaderMaterial _moving;

    /// <summary>Whether the overlay is on, which every newly registered object follows too.</summary>
    public static bool Active { get; private set; }

    /// <summary>
    /// Lets every mesh under <paramref name="root"/> get wet and snowy.
    /// </summary>
    /// <param name="moving">For what walks through the snow: it gets a frost rather than a cap.</param>
    public static void Register(Node root, bool moving)
    {
      if (root is GeometryInstance3D geometry)
      {
        geometry.AddToGroup(Group);
        geometry.SetMeta(MovingMeta, moving);
        geometry.MaterialOverlay = Active ? MaterialFor(moving) : null;
      }

      foreach (var child in root.GetChildren())
      {
        Register(child, moving);
      }
    }

    public static void SetActive(SceneTree tree, bool active)
    {
      if (active == Active)
      {
        return;
      }

      Active = active;

      foreach (var node in tree.GetNodesInGroup(Group))
      {
        if (node is GeometryInstance3D geometry)
        {
          geometry.MaterialOverlay = active ? MaterialFor(geometry.GetMeta(MovingMeta).AsBool()) : null;
        }
      }
    }

    private static ShaderMaterial MaterialFor(bool moving)
    {
      if (_still == null)
      {
        var shader = GD.Load<Shader>(ShaderPath);

        _still = new ShaderMaterial { Shader = shader };

        _moving = new ShaderMaterial { Shader = shader };
        _moving.SetShaderParameter(SnowShare, 0.3f);
        _moving.SetShaderParameter(CapMetres, 0.0f);
      }

      return moving ? _moving : _still;
    }
  }
}
