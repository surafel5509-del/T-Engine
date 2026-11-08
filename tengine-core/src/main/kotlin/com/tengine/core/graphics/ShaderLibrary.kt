package com.tengine.core.graphics

/**
 * Built-in GLSL ES 2.0 / 3.0 Shader Library written from scratch for T-Engine:
 * 1. Unlit Textured Sprite Batch Shader
 * 2. Dynamic 2D Multi-Light Sprite Shader (Ambient + up to 8 Point Lights!)
 * 3. Immediate/Batched Vector Primitive & Editor Gizmo Shader
 */
object ShaderLibrary {

    const val SPRITE_VERTEX_GLSL = """
        uniform mat4 uProjectionView;
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        attribute vec4 aColor;
        varying vec2 vTexCoord;
        varying vec4 vColor;
        varying vec2 vWorldPos;
        void main() {
            vTexCoord = aTexCoord;
            vColor = aColor;
            vWorldPos = aPosition;
            gl_Position = uProjectionView * vec4(aPosition.x, aPosition.y, 0.0, 1.0);
        }
    """

    const val SPRITE_FRAGMENT_GLSL = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTexCoord;
        varying vec4 vColor;
        void main() {
            vec4 texel = texture2D(uTexture, vTexCoord);
            gl_FragColor = texel * vColor;
        }
    """

    const val LIT_SPRITE_FRAGMENT_GLSL = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform vec4 uAmbientColor;
        uniform int uLightCount;
        uniform vec3 uLightPosRadius[8];
        uniform vec4 uLightColorIntensity[8];
        varying vec2 vTexCoord;
        varying vec4 vColor;
        varying vec2 vWorldPos;
        void main() {
            vec4 texel = texture2D(uTexture, vTexCoord) * vColor;
            if (texel.a <= 0.004) {
                discard;
            }
            vec3 totalLight = uAmbientColor.rgb * uAmbientColor.a;
            for (int i = 0; i < 8; i++) {
                if (i >= uLightCount) break;
                vec2 lightPos = uLightPosRadius[i].xy;
                float radius = max(0.01, uLightPosRadius[i].z);
                float dist = distance(vWorldPos, lightPos);
                if (dist < radius) {
                    float norm = 1.0 - (dist / radius);
                    float attenuation = norm * norm * (3.0 - 2.0 * norm);
                    totalLight += uLightColorIntensity[i].rgb * (uLightColorIntensity[i].a * attenuation);
                }
            }
            gl_FragColor = vec4(texel.rgb * totalLight, texel.a);
        }
    """

    const val PRIMITIVE_VERTEX_GLSL = """
        uniform mat4 uProjectionView;
        attribute vec2 aPosition;
        attribute vec4 aColor;
        varying vec4 vColor;
        void main() {
            vColor = aColor;
            gl_Position = uProjectionView * vec4(aPosition.x, aPosition.y, 0.0, 1.0);
        }
    """

    const val PRIMITIVE_FRAGMENT_GLSL = """
        precision mediump float;
        varying vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """
}
