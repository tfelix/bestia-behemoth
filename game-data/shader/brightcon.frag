void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    float Contrast = 0.4;
    float Brightness = -0.60;
    
	vec2 uv = fragCoord.xy / iResolution.xy;
   
    vec4 pixelColor = texture(iChannel0, uv);
    pixelColor.rgb /= pixelColor.a;

    // Apply contrast.
    pixelColor.rgb = ((pixelColor.rgb - 0.5f) * max(Contrast, 0.0)) + 0.5f;
    
    // Apply brightness.
    pixelColor.rgb += Brightness;

    // Return final pixel color.
    pixelColor.rgb *= pixelColor.a;
    
    fragColor = pixelColor;
}

