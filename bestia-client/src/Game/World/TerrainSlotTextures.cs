using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Assembles the terrain shader's texture array from whatever art exists, and invents the rest.
  /// </summary>
  /// <remarks>
  /// <b>Slot art is found by filename, not by assignment.</b> A slot's texture is
  /// <c>Game/World/Shader/Slots/&lt;ordinal&gt;_*.png</c>, RGB albedo with height in the alpha; anything not found
  /// is generated. That is the same directory-scan idiom the client already uses for the attack and item
  /// catalogues, and it is what makes dropping in real art a file copy rather than a code change.
  ///
  /// <para>
  /// <b>Every slot renders from the first day, and that is the point of the generated ones.</b> Seven of the
  /// eight have no art, and the alternative to inventing something is a black world or a shader that has to
  /// branch on whether a layer exists. A neutral grey with structure in it is neither, and it is honest about
  /// being unfinished in a way a wrong-but-detailed texture would not be.
  /// </para>
  ///
  /// <para>
  /// <b>The reference tint is measured here rather than typed into the material.</b> It is not an aesthetic
  /// knob - it is a statement about what colour a texture already is, which the shader divides out so the
  /// per-vertex tint lands on the palette's colour instead of darkening it. Typed in, it would be a number
  /// nobody rechecks when the art changes, and the symptom of it being stale is a world that has gone
  /// subtly muddy - the kind of wrong that gets argued about rather than found.
  /// </para>
  /// </remarks>
  public static class TerrainSlotTextures
  {
    private const string SlotDirectory = "res://Game/World/Shader/Slots/";

    /// <summary>
    /// Edge length of every layer.
    /// </summary>
    /// <remarks>
    /// All layers of a <c>Texture2DArray</c> must agree on size and format, so this is not per slot. 512 puts
    /// the eight layers at about 11 MB with mipmaps, which is small enough not to need block compression while
    /// the art is placeholder - and detailed enough that nothing here is what a texture looks blurry because of.
    /// </remarks>
    private const int Size = 512;

    /// <summary>Slot ordinal to the name its file carries, for the log and for finding it.</summary>
    private static readonly string[] Names =
    {
      "neutral", "grass", "dry_grass", "sand", "soil", "rock", "snow", "reserved"
    };

    /// <summary>The two arrays, and the mean colour of each albedo layer.</summary>
    public readonly struct Assembled
    {
      /// <summary>RGB albedo, A height.</summary>
      public Texture2DArray Albedo { get; init; }

      /// <summary>RG tangent normal, B roughness, A ambient occlusion.</summary>
      public Texture2DArray Surface { get; init; }

      /// <summary>Per-layer mean albedo - what the shader divides the vertex tint by.</summary>
      public Color[] ReferenceTints { get; init; }
    }

    public static Assembled Build()
    {
      var albedo = new Godot.Collections.Array<Image>();
      var surface = new Godot.Collections.Array<Image>();
      var tints = new Color[BlockAppearance.Slots];

      var authoredAlbedo = 0;
      var authoredSurface = 0;

      for (var slot = 0; slot < BlockAppearance.Slots; slot++)
      {
        var colour = LoadAuthored(slot, "");
        var maps = LoadAuthored(slot, "_n");

        if (colour != null)
        {
          authoredAlbedo++;
        }

        if (maps != null)
        {
          authoredSurface++;
        }

        colour ??= Generate(slot);
        maps ??= FlatSurface();

        // Measured before mipmaps are generated: the mean wants the full-resolution layer, and the levels are
        // appended to the same buffer MeanColour reads.
        tints[slot] = MeanColour(colour);

        // Not optional. The shader asks for anisotropic mipmapped filtering, but a Texture2DArray only has the
        // mip levels its source images had, and neither the imported PNGs nor the generated ones carry any. The
        // result would be terrain that crawls with aliasing at any distance - and it would look like a shader
        // problem rather than a missing pyramid.
        colour.GenerateMipmaps();
        maps.GenerateMipmaps();

        albedo.Add(colour);
        surface.Add(maps);
      }

      var albedoArray = new Texture2DArray();
      albedoArray.CreateFromImages(albedo);

      var surfaceArray = new Texture2DArray();
      surfaceArray.CreateFromImages(surface);

      GD.Print(
        $"[terrain] slot textures {Size}x{Size} x{BlockAppearance.Slots}: " +
        $"albedo {authoredAlbedo} authored, surface {authoredSurface} authored, rest generated");

      return new Assembled { Albedo = albedoArray, Surface = surfaceArray, ReferenceTints = tints };
    }

    /// <summary>
    /// The surface maps of a material nobody has authored: flat, fully rough, unoccluded.
    /// </summary>
    /// <remarks>
    /// Deliberately featureless even where <see cref="Generate"/> invents structure for the albedo. A normal map
    /// invented to match a texture that was itself invented would light the world with detail that is not in the
    /// silhouette or the collision - a placeholder that lies rather than one that waits.
    /// </remarks>
    private static Image FlatSurface()
    {
      var data = new byte[Size * Size * 4];

      for (var at = 0; at < data.Length; at += 4)
      {
        // (0.5, 0.5) unpacks to a tangent normal of (0, 0, 1): straight out of the surface.
        data[at] = 128;
        data[at + 1] = 128;
        data[at + 2] = 255;
        data[at + 3] = 255;
      }

      return Image.CreateFromData(Size, Size, false, Image.Format.Rgba8, data);
    }

    /// <summary>
    /// The slot's art, or null if nobody has made it yet.
    /// </summary>
    /// <remarks>
    /// Goes through the imported texture rather than reading the PNG, so the art keeps whatever compression and
    /// mipmap settings the importer was told to use. Decompressed straight back out again because the array is
    /// assembled from raw images - which costs a moment at load and saves having to keep the slot PNGs on a
    /// different import preset from every other texture in the project.
    /// </remarks>
    private static Image LoadAuthored(int slot, string suffix)
    {
      var path = $"{SlotDirectory}{slot}_{Names[slot]}{suffix}.png";

      if (!ResourceLoader.Exists(path))
      {
        return null;
      }

      var texture = GD.Load<Texture2D>(path);
      var image = texture?.GetImage();

      if (image == null)
      {
        GD.PushWarning($"[terrain] slot {slot} texture {path} exists but produced no image");
        return null;
      }

      if (image.IsCompressed())
      {
        image.Decompress();
      }

      image.Convert(Image.Format.Rgba8);

      if (image.GetWidth() != Size || image.GetHeight() != Size)
      {
        image.Resize(Size, Size, Image.Interpolation.Lanczos);
      }

      return image;
    }

    /// <summary>
    /// A stand-in texture for a slot with no art: neutral grey with enough structure to read as a surface.
    /// </summary>
    /// <remarks>
    /// Value noise over three octaves, seeded from the slot so each is its own texture and two placeholder slots
    /// meeting still show a boundary. Kept close to mid grey and deliberately colourless, because the shader
    /// divides by the mean and multiplies by the vertex tint - so a placeholder that had a hue of its own would
    /// fight the palette, and terrain that is meant to be sand would come out sand-times-something.
    ///
    /// <para>
    /// The same noise goes in the alpha as height, so height blending has something to bite on. Without it every
    /// placeholder boundary would be a straight linear fade and the feature would look broken rather than idle.
    /// </para>
    /// </remarks>
    private static Image Generate(int slot)
    {
      // Written as a flat buffer rather than through SetPixel, which is a marshalled call per pixel: six
      // generated layers at this size is a million and a half of them, and this runs while the player is
      // waiting to see the world.
      var data = new byte[Size * Size * 4];

      // Slot 0 is what every unmapped block falls back to, so it is the flattest: it stands for "no decision
      // has been made about this material", and structure would read as a decision.
      var contrast = slot == (int)BlockAppearance.SurfaceSlot.Neutral ? 0.06f : 0.16f;

      var at = 0;

      for (var y = 0; y < Size; y++)
      {
        for (var x = 0; x < Size; x++)
        {
          var noise =
            0.60f * Value(x, y, 8, slot) +
            0.30f * Value(x, y, 24, slot + 101) +
            0.10f * Value(x, y, 64, slot + 211);

          var level = (byte)Mathf.Clamp(
            Mathf.RoundToInt((0.5f + (noise - 0.5f) * 2.0f * contrast) * 255.0f), 0, 255);

          data[at++] = level;
          data[at++] = level;
          data[at++] = level;
          data[at++] = level;
        }
      }

      return Image.CreateFromData(Size, Size, false, Image.Format.Rgba8, data);
    }

    /// <summary>
    /// Tiling value noise at a given cell count across the texture.
    /// </summary>
    /// <remarks>
    /// Wraps on <paramref name="cells"/> so the layer still tiles seamlessly, which matters because triplanar
    /// mapping repeats it every few metres and a seam would be the most visible thing on screen.
    /// </remarks>
    private static float Value(int x, int y, int cells, int seed)
    {
      var scale = (float)cells / Size;
      var fx = x * scale;
      var fy = y * scale;

      var x0 = Mathf.FloorToInt(fx);
      var y0 = Mathf.FloorToInt(fy);

      var tx = Smooth(fx - x0);
      var ty = Smooth(fy - y0);

      var c00 = Hash(x0, y0, cells, seed);
      var c10 = Hash(x0 + 1, y0, cells, seed);
      var c01 = Hash(x0, y0 + 1, cells, seed);
      var c11 = Hash(x0 + 1, y0 + 1, cells, seed);

      return Mathf.Lerp(Mathf.Lerp(c00, c10, tx), Mathf.Lerp(c01, c11, tx), ty);
    }

    private static float Smooth(float t) => t * t * (3.0f - 2.0f * t);

    private static float Hash(int x, int y, int period, int seed)
    {
      // Wrapped so opposite edges of the texture read the same lattice corner.
      var wx = ((x % period) + period) % period;
      var wy = ((y % period) + period) % period;

      var h = wx * 374761393 + wy * 668265263 + seed * 1442695040;
      h = (h ^ (h >> 13)) * 1274126177;
      h ^= h >> 16;

      return (h & 0xFFFF) / 65535.0f;
    }

    /// <summary>
    /// The layer's average colour, straight off the pixel buffer.
    /// </summary>
    /// <remarks>
    /// <c>GetData</c> rather than <c>GetPixel</c> for the reason <see cref="Generate"/> writes one: eight layers
    /// of a quarter-million pixels is two million marshalled calls, and this is all on the way to the first
    /// frame.
    ///
    /// <para>
    /// The mean is taken in the texture's own encoding rather than in linear light, which is the same space the
    /// shader's division happens in - so the two agree, which matters more here than either being physically
    /// right.
    /// </para>
    /// </remarks>
    private static Color MeanColour(Image image)
    {
      var data = image.GetData();

      double r = 0.0, g = 0.0, b = 0.0;

      for (var at = 0; at + 3 < data.Length; at += 4)
      {
        r += data[at];
        g += data[at + 1];
        b += data[at + 2];
      }

      var pixels = (double)Size * Size * 255.0;

      // Never zero: the shader divides by this, and a black texture would otherwise take the whole world with it.
      return new Color(
        Mathf.Max((float)(r / pixels), 0.01f),
        Mathf.Max((float)(g / pixels), 0.01f),
        Mathf.Max((float)(b / pixels), 0.01f));
    }
  }
}
