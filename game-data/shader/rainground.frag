vec4 circle(vec2 texCoord)
{
    float dist = texCoord.x * texCoord.x + texCoord.y * texCoord.y;
    if(dist < 1.0)
        return vec4(1.0, 1.0, 1.0, 1.0);
    else
        return vec4(0, 0, 0, 1.0);
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
	vec2 uv = fragCoord.xy / iResolution.xy;
	fragColor = circle(uv);
        
}
