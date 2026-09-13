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

    /// <summary>
    /// The art for one prop of <paramref name="kind"/>, drawn as something part-built.
    /// </summary>
    /// <remarks>
    /// Used for both halves of placing a thing: the ghost that follows the cursor, which stays at progress 0,
    /// and the construction site itself, whose progress the server drives. One method so the two cannot drift
    /// into looking like different objects.
    /// <para>
    /// The shader needs the mesh's vertical extent, which it cannot see for itself, so it is read off the AABB
    /// here. A kind drawn from a whole scene rather than a single mesh keeps its own materials - no buildable
    /// kind is today.
    /// </para>
    /// </remarks>
    public Node3D BuildConstruction(int kind)
    {
      var node = Build(kind);

      if (node is not MeshInstance3D instance || instance.Mesh == null)
      {
        return node;
      }

      var bounds = instance.Mesh.GetAabb();
      var material = new ShaderMaterial { Shader = ResourceLoader.Load<Shader>(ConstructionShaderPath) };

      material.SetShaderParameter("y_min", bounds.Position.Y);
      material.SetShaderParameter("y_span", bounds.Size.Y);
      material.SetShaderParameter("built_color", PropAppearance.Of(kind).PlaceholderColour);
      material.SetShaderParameter("progress", 0f);

      instance.MaterialOverride = material;

      return node;
    }

    /// <summary>
    /// The <c>StaticEntityKind</c> ordinal of a station a player can build, or -1 for a name this client does
    /// not know.
    /// </summary>
    /// <remarks>
    /// Hand-mirrored, like everything else about that enum on this side - see <see cref="PropAppearance"/>'s
    /// note on why there is no handshake for it. Here rather than in GDScript so a client spells a kind in
    /// exactly one place, and checked against the table below so a drifted ordinal is an error in the log
    /// rather than a magenta box in the world.
    /// </remarks>
    public int KindByName(string name)
    {
      var ordinal = name switch
      {
        "WORKBENCH" => 22,
        "FURNACE" => 23,
        "FORGE" => 24,
        _ => -1
      };

      if (ordinal < 0)
      {
        GD.PushError($"PropVisualBuilder: no buildable kind named {name}");
        return ordinal;
      }

      // Every buildable kind carries a StructureHeight and nothing else does, which makes this a real check
      // on the number above rather than a restatement of it.
      if (PropAppearance.Of(ordinal).StructureHeight <= 0f)
      {
        GD.PushError($"PropVisualBuilder: ordinal {ordinal} is not a buildable kind; StaticEntityKind has drifted");
      }

      return ordinal;
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

    private static Godot.Mesh MeshFor(PropAppearance.Kind appearance)
    {
      if (appearance.HasMesh)
      {
        return ResourceLoader.Load<Godot.Mesh>(appearance.MeshPath);
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

    private const string ConstructionShaderPath =
      "res://Game/Entity/Visual/StructureVisual/construction.gdshader";
  }
}
