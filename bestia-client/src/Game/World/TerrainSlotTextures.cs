using System.Collections.Generic;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Assembles the terrain shader's texture array from whatever art exists, and invents the rest.
  /// </summary>
  /// <remarks>
  /// <b>Slot art is found by filename, not by assignment.</b> A slot's maps are
  /// <c>Game/World/Shader/Slots/&lt;ordinal&gt;_&lt;name&gt;_&lt;map&gt;.png</c>, one file per map exactly as a
  /// texture pack ships them and as <c>StandardMaterial3D</c> would take them:
  ///
  /// <list type="table">
  /// <item><term>_albedo</term><description>RGB base colour, sRGB.</description></item>
  /// <item><term>_normal</term><description>Tangent normal, OpenGL convention (+Y up), the one Godot
  /// expects.</description></item>
  /// <item><term>_height</term><description>Grey displacement. Read by the height blend, not by a parallax
  /// step.</description></item>
  /// <item><term>_ao</term><description>Grey ambient occlusion.</description></item>
  /// <item><term>_smoothness</term><description>Grey, inverted here into roughness. <c>_roughness</c> is taken
  /// directly if that is what the pack ships instead.</description></item>
  /// </list>
  ///
  /// <para>
  /// Every map is optional and each has its own fallback, so a pack that ships three of them works and the
  /// missing two read as "no relief, fully rough, unoccluded" rather than as a hole. The older two-file form -
  /// <c>&lt;ordinal&gt;_&lt;name&gt;.png</c> with height in the alpha, plus <c>_n.png</c> carrying RG normal,
  /// B roughness, A occlusion - is still read when no <c>_albedo</c> or <c>_normal</c> is found, which is what
  /// keeps <c>dry_grass</c> rendering while only <c>grass</c> has been redone.
  /// </para>
  ///
  /// <para>
  /// <b>Metallic is deliberately not read, and neither is an edge or curvature map.</b> Terrain is rough
  /// dielectrics from end to end - the shader has always said so by writing <c>SPECULAR</c> rather than a
  /// metal workflow - so a metalness channel would be zero for every material the palette has. It is not free
  /// to carry: the two arrays below are already full at eight channels, and a third would add up to six
  /// texture fetches per pixel to the two-slot blend. The grass pack's own <c>_metallic</c> is the argument
  /// rather than the exception, at a mean of 0.61 with a -0.45 correlation against its occlusion: it is a
  /// cavity mask under a metalness name, and feeding it to <c>METALLIC</c> would render a lawn as painted tin.
  /// Its <c>_edge</c> correlates 0.78 with the same occlusion map, so it is that information twice.
  /// </para>
  ///
  /// <para>
  /// <b>Every slot renders from the first day, and that is the point of the generated ones.</b> Fourteen of the
  /// sixteen have no art, and the alternative to inventing something is a black world or a shader that has to
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
    /// All layers of a <c>Texture2DArray</c> must agree on size and format, so this is not per slot, and art
    /// that arrives at another size is resampled to it.
    ///
    /// <para>
    /// <b>Still 512 although the grass pack ships at 1024, and the tiling is why.</b> Sixteen layers across two
    /// uncompressed RGBA8 arrays is 32 MiB here and about 43 MiB once the mip chain is on it; at 1024 the same
    /// arrays are 171 MiB. What that would buy is texel density the tiling cannot use - grass repeats every two
    /// metres, so 512 is already 256 texels to the metre, or a texel every four millimetres, on a surface the
    /// camera views from eight metres up and almost entirely at a grazing angle. Raising this is one edit if a
    /// slot ever wants it; paying 128 MiB for detail below a pixel is not.
    /// </para>
    /// </remarks>
    private const int Size = 512;

    /// <summary>A tangent normal pointing straight out, for a slot with no normal map.</summary>
    private const byte FlatNormal = 128;

    /// <summary>Mid height, which the blend reads as a surface with no relief to assert.</summary>
    private const byte NoRelief = 128;

    /// <summary>Terrain's resting state, and what a slot with no roughness map gets.</summary>
    private const byte FullyRough = 255;

    /// <summary>No occlusion, so the ambient reaches the surface unmodified.</summary>
    private const byte Unoccluded = 255;

    /// <summary>Slot ordinal to the name its file carries, for the log and for finding it.</summary>
    /// <remarks>
    /// Keep in step with <c>BlockAppearance.SurfaceSlot</c> - a name here is how the file for that slot is
    /// found, so a mismatch means authored art silently never loads and the slot renders as generated noise.
    /// Slot 7 read <c>reserved</c> long after it became <c>Wetland</c>, which is exactly that bug sitting
    /// harmlessly in place because nobody had drawn the texture yet.
    ///
    /// <para>
    /// Slots 9 to 15 are unnamed on purpose. They exist because the enum went to sixteen for
    /// <c>Scorched</c>, and naming them now would invite art for materials nobody has decided on; an unnamed
    /// slot loads as generated grey and no block maps to it, so it costs a texture layer and nothing else.
    /// </para>
    /// </remarks>
    private static readonly string[] Names =
    {
      "neutral", "grass", "dry_grass", "sand", "soil", "rock", "snow", "wetland", "scorched",
      "trodden", "bloodied",
      "slot11", "slot12", "slot13", "slot14", "slot15"
    };

    /// <summary>The two arrays, and the mean colour of each albedo layer.</summary>
    public readonly struct Assembled
    {
      /// <summary>RGB albedo, A height.</summary>
      public Texture2DArray Albedo { get; init; }

      /// <summary>RG tangent normal, B roughness, A ambient occlusion.</summary>
      public Texture2DArray Surface { get; init; }

      /// <summary>
      /// Per-layer mean albedo - what the shader divides the vertex tint by.
      /// </summary>
      /// <remarks>
      /// <b><see cref="Vector3"/> and not <see cref="Color"/>, and that is load bearing.</b> The uniform on the
      /// other side is <c>vec3 slot_reference_tint[8]</c>, and Godot fills a <c>vec3[]</c> from a
      /// <c>PackedVector3Array</c> alone - a <c>PackedColorArray</c> is not converted, it is dropped, and the
      /// uniform stays at its zeroed default.
      ///
      /// <para>
      /// Nothing reports that. <c>SetShaderParameter</c> succeeds, <c>GetShaderParameter</c> reads the colours
      /// straight back, and the material looks correctly configured from every angle except the one that matters.
      /// What the shader then computes is <c>tint / max(0.0, 0.0001)</c>, so every albedo comes out ten thousand
      /// times too bright and the entire world renders as untextured white.
      /// </para>
      /// </remarks>
      public Vector3[] ReferenceTints { get; init; }
    }

    public static Assembled Build()
    {
      var albedo = new Godot.Collections.Array<Image>();
      var surface = new Godot.Collections.Array<Image>();
      var tints = new Vector3[BlockAppearance.Slots];

      var report = new List<string>();

      for (var slot = 0; slot < BlockAppearance.Slots; slot++)
      {
        var found = new List<string>();

        // The legacy pair is looked up only where its replacement is absent, and is kept in its own local
        // rather than merged into the new one. Its extra channels are not the new maps' extra channels - the
        // old _n packed roughness in B and occlusion in A, where a real normal map has the tangent Z and an
        // opaque alpha there. Reading those as roughness would set every slot on the new convention to 0.98
        // rough, which is close enough to right to survive a look and wrong for a reason nobody would find.
        var colour = LoadMap(slot, "_albedo", found);
        var legacyColour = colour == null ? LoadMap(slot, "", found) : null;

        var normal = LoadMap(slot, "_normal", found);
        var legacySurface = normal == null ? LoadMap(slot, "_n", found) : null;

        var height = LoadMap(slot, "_height", found);
        var occlusion = LoadMap(slot, "_ao", found);
        var smoothness = LoadMap(slot, "_smoothness", found);
        var roughness = smoothness == null ? LoadMap(slot, "_roughness", found) : null;

        var source = colour ?? legacyColour ?? Generate(slot);
        var tangent = normal ?? legacySurface;

        var albedoData = new byte[Size * Size * 4];
        var surfaceData = new byte[Size * Size * 4];

        for (var at = 0; at < albedoData.Length; at += 4)
        {
          albedoData[at] = source[at];
          albedoData[at + 1] = source[at + 1];
          albedoData[at + 2] = source[at + 2];

          // Height, in order: its own map; then flat, if the albedo came from a file whose alpha is just
          // opacity; then the alpha of whatever did supply the colour, which is where both the legacy
          // convention and Generate put their height.
          albedoData[at + 3] =
            height != null ? height[at] :
            colour != null ? NoRelief : source[at + 3];

          surfaceData[at] = tangent != null ? tangent[at] : FlatNormal;
          surfaceData[at + 1] = tangent != null ? tangent[at + 1] : FlatNormal;

          // Smoothness is the same measurement upside down, and is what this pack ships. Inverting it here
          // rather than in the shader keeps the array's meaning single: B is roughness, whatever the art
          // called it.
          surfaceData[at + 2] =
            smoothness != null ? (byte)(255 - smoothness[at]) :
            roughness != null ? roughness[at] :
            legacySurface != null ? legacySurface[at + 2] : FullyRough;

          surfaceData[at + 3] =
            occlusion != null ? occlusion[at] :
            legacySurface != null ? legacySurface[at + 3] : Unoccluded;
        }

        var albedoImage = Image.CreateFromData(Size, Size, false, Image.Format.Rgba8, albedoData);
        var surfaceImage = Image.CreateFromData(Size, Size, false, Image.Format.Rgba8, surfaceData);

        // Measured before mipmaps are generated: the mean wants the full-resolution layer, and the levels are
        // appended to the same buffer MeanColour reads.
        tints[slot] = MeanColour(albedoImage);

        // Not optional. The shader asks for anisotropic mipmapped filtering, but a Texture2DArray only has the
        // mip levels its source images had, and neither the imported PNGs nor the generated ones carry any. The
        // result would be terrain that crawls with aliasing at any distance - and it would look like a shader
        // problem rather than a missing pyramid.
        albedoImage.GenerateMipmaps();
        surfaceImage.GenerateMipmaps();

        albedo.Add(albedoImage);
        surface.Add(surfaceImage);

        if (found.Count > 0)
        {
          report.Add($"{slot} {Names[slot]} [{string.Join(' ', found)}]");
        }
      }

      var albedoArray = new Texture2DArray();
      albedoArray.CreateFromImages(albedo);

      var surfaceArray = new Texture2DArray();
      surfaceArray.CreateFromImages(surface);

      // Every map that was found, named. The failure this exists for is silent by construction: a misspelt
      // suffix or a slot renamed out from under its files loads nothing, generates grey, and reports success -
      // so the only way to see that _smoothness never arrived is for the log to list what did.
      GD.Print(
        $"[terrain] slot textures {Size}x{Size} x{BlockAppearance.Slots}: " +
        $"{(report.Count == 0 ? "none authored" : string.Join(", ", report))}; " +
        $"{BlockAppearance.Slots - report.Count} generated");

      return new Assembled { Albedo = albedoArray, Surface = surfaceArray, ReferenceTints = tints };
    }

    /// <summary>
    /// One of a slot's maps as raw RGBA8 at <see cref="Size"/>, or null if that file does not exist.
    /// </summary>
    /// <remarks>
    /// Goes through the imported texture rather than reading the PNG, so the art keeps whatever compression and
    /// mipmap settings the importer was told to use. Decompressed straight back out again because the array is
    /// assembled from raw images - which costs a moment at load and saves having to keep the slot PNGs on a
    /// different import preset from every other texture in the project.
    ///
    /// <para>
    /// Bytes rather than an <c>Image</c>, because every caller wants one or two channels out of it and
    /// <c>GetPixel</c> is a marshalled call. A quarter of a million texels times six maps times sixteen slots is
    /// not a loop to make that way.
    /// </para>
    /// </remarks>
    private static byte[] LoadMap(int slot, string suffix, List<string> found)
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

      found.Add(suffix.Length == 0 ? "albedo(legacy)" : suffix.TrimStart('_'));

      return image.GetData();
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
    private static byte[] Generate(int slot)
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

      return data;
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
    /// <b>Averaged in linear light, not in the texture's own encoding.</b> The albedo sampler is declared
    /// <c>source_color</c>, so what the shader divides by this is sRGB-decoded - and decoding is convex, so the
    /// mean of the decoded texture is strictly greater than the decode of its mean. Averaging in storage space
    /// therefore hands the shader a reference that is too small, and every material comes out brighter than the
    /// palette colour it was supposed to be corrected to. The error is small, entirely invisible as a bug, and
    /// wrong in the same direction for everything, which is the worst combination available.
    /// </para>
    /// </remarks>
    /// <summary>sRGB byte to linear float, because doing it per pixel is two million calls to Pow.</summary>
    private static readonly float[] Linear = BuildLinearTable();

    private static float[] BuildLinearTable()
    {
      var table = new float[256];

      for (var i = 0; i < table.Length; i++)
      {
        var c = i / 255.0f;

        table[i] = c <= 0.04045f ? c / 12.92f : Mathf.Pow((c + 0.055f) / 1.055f, 2.4f);
      }

      return table;
    }

    private static Vector3 MeanColour(Image image)
    {
      var data = image.GetData();

      double r = 0.0, g = 0.0, b = 0.0;

      for (var at = 0; at + 3 < data.Length; at += 4)
      {
        r += Linear[data[at]];
        g += Linear[data[at + 1]];
        b += Linear[data[at + 2]];
      }

      var pixels = (double)Size * Size;

      // Never zero: the shader divides by this, and a black texture would otherwise take the whole world with it.
      return new Vector3(
        Mathf.Max((float)(r / pixels), 0.01f),
        Mathf.Max((float)(g / pixels), 0.01f),
        Mathf.Max((float)(b / pixels), 0.01f));
    }
  }
}
