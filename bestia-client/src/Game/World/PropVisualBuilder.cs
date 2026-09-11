using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Builds the art for <b>one</b> prop of a given <c>StaticEntityKind</c>.
  /// </summary>
  /// <remarks>
  /// The single-instance counterpart to <see cref="StaticEntityRenderer"/>, which draws the same catalogue
  /// but in per-chunk <c>MultiMesh</c> batches. The two cannot share code: everything in the renderer is
  /// shaped by batching - instance slots, per-column meshes, a shared material per kind - and none of that
  /// applies to a lone entity that needs its own material to fade.
  ///
  /// <para>
  /// Instance methods on a RefCounted rather than a static helper, because <see cref="PropAppearance"/> is a
  /// static C# class and GDScript cannot reach one. Resolving the ordinals on this side is the same choice
  /// <c>CraftableRecipesSMSG</c> makes, and for the same reason.
  /// </para>
  /// </remarks>
  public partial class PropVisualBuilder : RefCounted
  {
    /// <summary>The art for one prop of <paramref name="kind"/>, standing on y = 0.</summary>
    public Node3D Build(int kind)
    {
      var appearance = PropAppearance.Of(kind);

      if (appearance.HasScene)
      {
        return ResourceLoader.Load<PackedScene>(appearance.ScenePath).Instantiate<Node3D>();
      }

      var instance = new MeshInstance3D { Mesh = MeshFor(appearance) };

      // A BoxMesh is centred on its own origin, so a box drawn at ground level would be half underground -
      // the same lift StaticEntityRenderer applies to its placeholder instances.
      if (!appearance.HasMesh)
      {
        instance.Position = new Vector3(0f, HeightOf(appearance) * 0.5f, 0f);
      }

      return instance;
    }

    /// <summary>The box a click target for this kind should cover, in metres.</summary>
    public Vector3 PickSize(int kind)
    {
      var appearance = PropAppearance.Of(kind);
      var width = Mathf.Max(appearance.PlaceholderWidth, MinPickWidth);

      return new Vector3(width, HeightOf(appearance), width);
    }

    /// <summary>How tall this kind stands, in metres.</summary>
    public float Height(int kind)
    {
      return HeightOf(PropAppearance.Of(kind));
    }

    private static Mesh MeshFor(PropAppearance.Kind appearance)
    {
      if (appearance.HasMesh)
      {
        return ResourceLoader.Load<Mesh>(appearance.MeshPath);
      }

      var height = HeightOf(appearance);

      return new BoxMesh
      {
        Size = new Vector3(appearance.PlaceholderWidth, height, appearance.PlaceholderWidth),
        Material = new StandardMaterial3D { AlbedoColor = appearance.PlaceholderColour }
      };
    }

    private static float HeightOf(PropAppearance.Kind appearance)
    {
      if (appearance.StructureHeight > 0f)
      {
        return appearance.StructureHeight;
      }

      return appearance.NaturalHeight > 0f ? appearance.NaturalHeight : 1f;
    }

    /// <summary>
    /// Floor on a click target's width, for the reason <see cref="StaticEntityRenderer"/>'s own floor exists:
    /// a target you have to aim at is worse than one slightly bigger than it looks.
    /// </summary>
    private const float MinPickWidth = 0.6f;
  }
}
