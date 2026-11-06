package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.resources.ResourceLocation;
import net.kofllee.pyrovfx.vfx.type.VfxFacingMode;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.slf4j.Logger;
import java.util.Map;
import java.util.HashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** GPU-resident geometry source for the native Minecraft particle shader.
 * Produces Minecraft vertex format for GPU Analytic and CPU batched sprites.
 * Unsupported subsets use the shared Minecraft RenderType material fallback.
 * The GL3.3 transform-feedback pass never reads particle vertices back to the CPU.
 */
public final class VfxNativeGpuGeometryBridge {
    private static final Logger LOG = LogUtils.getLogger();
    // GPU TF output: vec3 position + vec2 UV + packed RGBA8 + two 16-bit light UVs.
    // Four vertices + six cached indices per particle (136 bytes versus 264 bytes originally).
    private static final int STRIDE = 28;
    private static final int VERTICES_PER_PARTICLE = 4;
    private static final int INDICES_PER_PARTICLE = 6;
    private static final int MAX_BATCH = 65_536;
    private static final String VS = """
            #version 330 core
            layout(location=0) in vec3 birth;
            layout(location=1) in vec3 velocity;
            layout(location=2) in float iRandom;
            layout(location=3) in float iLifetime;
            uniform vec3 uCamera, uRight, uUp, uAcceleration, uParametric;
            uniform vec3 uSourcePosition, uSpriteRotation;
            uniform vec4 uSourceRotation;
            uniform vec2 uMotion, uVelocityMotion, uScale;
            uniform vec4 uColor, uUvRect;
            uniform int uLocalSpace, uVelocityFacing, uIndividualLifetimes;
            uniform float uCurrentCohortAge;
            uniform float uPrevAge, uCurrentAge, uLife, uPartial;
            uniform vec2 uAppearancePrevMotion, uAppearanceCurrentMotion, uAppearanceBeforePrevMotion;
            uniform vec2 uAppearancePrevVelocity, uAppearanceCurrentVelocity;
            uniform int uRandomStartFrame, uUvBaseFrame, uUvFrameCount, uUvLoop;
            uniform vec2 uUvStepNormalized;
            uniform int uLightMode;
            uniform uvec2 uSharedLight;
            uniform ivec3 uLightVolumeOrigin;
            uniform sampler3D uLightVolume;
            out vec3 outPosition;
            out vec2 outUv;
            flat out uint outColor;
            flat out uint outLight;
            vec3 rotateSource(vec3 v) {
                vec3 t = 2.0 * cross(uSourceRotation.xyz, v);
                return v + uSourceRotation.w * t + cross(uSourceRotation.xyz, t);
            }
            void main() {
                if (uIndividualLifetimes != 0 && uCurrentCohortAge >= iLifetime) {
                    outPosition = vec3(0.,0.,100000000.);
                    outUv = vec2(0.);
                    outColor = 0u;
                    outLight = 240u | (240u<<16u);
                    return;
                }
                int corner = gl_VertexID % 4;
                vec2 c = corner == 0 ? vec2(-1.,-1.)
                       : corner == 1 ? vec2(1.,-1.)
                       : corner == 2 ? vec2(1.,1.) : vec2(-1.,1.);
                vec3 world = birth + velocity * uMotion.x + uAcceleration * uMotion.y + uParametric;
                if (uLocalSpace != 0)
                    world = uSourcePosition + rotateSource(world);
                vec2 size = uScale;
                vec4 factor = vec4(1.);
                // GPU_APPEARANCE
                vec3 local = vec3(c * size * .5, 0.);
                vec3 angle = radians(uSpriteRotation);
                vec3 sn = sin(angle), cs = cos(angle);
                local = vec3(cs.z*local.x - sn.z*local.y,
                             sn.z*local.x + cs.z*local.y, local.z);
                local = vec3(cs.y*local.x + sn.y*local.z,
                             local.y, -sn.y*local.x + cs.y*local.z);
                local = vec3(local.x, cs.x*local.y - sn.x*local.z,
                             sn.x*local.y + cs.x*local.z);
                vec3 right = uRight, up = uUp;
                if (uVelocityFacing != 0) {
                    vec3 facing = velocity*uVelocityMotion.x + uAcceleration*uVelocityMotion.y;
                    if (uLocalSpace != 0) facing = rotateSource(facing);
                    if (dot(facing,facing)<1e-6) facing = vec3(1.,0.,0.);
                    facing = normalize(facing);
                    vec4 q = facing.z < -0.99999 ? vec4(0.,1.,0.,0.)
                            : normalize(vec4(cross(vec3(0.,0.,1.),facing),1.+facing.z));
                    vec3 t = 2.*cross(q.xyz,vec3(1.,0.,0.));
                    right = vec3(1.,0.,0.) + q.w*t + cross(q.xyz,t);
                    t = 2.*cross(q.xyz,vec3(0.,1.,0.));
                    up = vec3(0.,1.,0.) + q.w*t + cross(q.xyz,t);
                }
                outPosition = world - uCamera + right*local.x + up*local.y
                              + cross(right,up)*local.z;
                int frameOffset = 0;
                if (uRandomStartFrame != 0) {
                    int rf = int(floor(clamp(iRandom,0.,0.99999994)*float(uUvFrameCount)));
                    int frame = uUvBaseFrame + rf;
                    frame = uUvLoop != 0 ? ((frame % uUvFrameCount) + uUvFrameCount)
                                           % uUvFrameCount
                                         : clamp(frame,0,uUvFrameCount-1);
                    frameOffset = frame - uUvBaseFrame;
                }
                vec2 uvShift = float(frameOffset)*uUvStepNormalized;
                vec2 t = (c+1.)*.5;
                outUv = vec2(mix(uUvRect.x,uUvRect.z,t.x)+uvShift.x,
                            1.-mix(uUvRect.y,uUvRect.w,t.y)-uvShift.y);
                uvec4 rgba = uvec4(clamp(uColor*factor,0.,1.)*255.+.5);
                outColor = rgba.r | (rgba.g<<8u) | (rgba.b<<16u) | (rgba.a<<24u);
                uvec2 light = uvec2(240u);
                if (uLightMode == 1) light = uSharedLight;
                else if (uLightMode == 2) {
                    vec2 sampleUv = texelFetch(uLightVolume,
                            ivec3(floor(world)) - uLightVolumeOrigin, 0).rg;
                    light = uvec2(clamp(floor(sampleUv*16.0),vec2(0.),vec2(15.))) * 16u;
                }
                outLight = light.x | (light.y<<16u);
            }
            """;
    /**
     * Native geometry from the existing CPU Dense/Legacy batching contract.
     * 21 floats per instance match VfxNativeSpriteBatcher's staging layout.
     * Vertex positions are camera-relative and the output is Minecraft's
     * POSITION_TEX_COLOR_LIGHTMAP vertex format, not a custom fragment shader.
     */
    private static final String INSTANCE_VS = """
            #version 330 core
            layout(location=0) in vec3 iPosition;
            layout(location=1) in vec2 iSize;
            layout(location=2) in vec4 iColor;
            layout(location=3) in vec4 iUvRect;
            layout(location=4) in vec3 iRotation;
            layout(location=5) in vec2 iLight;
            layout(location=6) in vec3 iVelocity;
            uniform vec3 uRight, uUp;
            uniform int uHasRotation, uVelocityFacing, uUseLight;
            out vec3 outPosition;
            out vec2 outUv;
            flat out uint outColor;
            flat out uint outLight;
            void main() {
                int k = gl_VertexID % 4;
                vec2 c = k == 0 ? vec2(-1.,-1.)
                       : k == 1 ? vec2(1.,-1.)
                       : k == 2 ? vec2(1.,1.) : vec2(-1.,1.);
                vec3 corner = vec3(c * iSize * 0.5, 0.);
                if (uHasRotation != 0) {
                    vec3 radiansRotation = radians(iRotation);
                    vec3 z = sin(radiansRotation), q = cos(radiansRotation);
                    corner = vec3(q.z*corner.x-z.z*corner.y,
                                  z.z*corner.x+q.z*corner.y,corner.z);
                    corner = vec3(q.y*corner.x+z.y*corner.z,
                                  corner.y,-z.y*corner.x+q.y*corner.z);
                    corner = vec3(corner.x,q.x*corner.y-z.x*corner.z,
                                  z.x*corner.y+q.x*corner.z);
                }
                vec3 right = uRight, up = uUp;
                if (uVelocityFacing != 0) {
                    vec3 n = dot(iVelocity,iVelocity) < 1e-6
                            ? vec3(1,0,0) : normalize(iVelocity);
                    vec4 q = n.z < -0.99999 ? vec4(0,1,0,0)
                            : normalize(vec4(cross(vec3(0,0,1),n),1.+n.z));
                    vec3 t = 2.*cross(q.xyz,vec3(1,0,0));
                    right = vec3(1,0,0) + q.w*t + cross(q.xyz,t);
                    t = 2.*cross(q.xyz,vec3(0,1,0));
                    up = vec3(0,1,0) + q.w*t + cross(q.xyz,t);
                }
                outPosition = iPosition + right*corner.x + up*corner.y
                           + cross(right,up)*corner.z;
                vec2 t = (c + 1.) * .5;
                outUv = vec2(mix(iUvRect.x,iUvRect.z,t.x),
                             mix(iUvRect.w,iUvRect.y,t.y));
                uvec4 rgba = uvec4(clamp(iColor,0.,1.)*255. + .5);
                outColor = rgba.r | (rgba.g<<8u) | (rgba.b<<16u) | (rgba.a<<24u);
                if (uUseLight != 0) {
                    uvec2 light = uvec2(clamp(floor(iLight*16.),vec2(0.),vec2(15.)));
                    outLight = (light.x*16u) | ((light.y*16u)<<16u);
                } else outLight = 240u | (240u<<16u);
                gl_Position = vec4(0.,0.,0.,1.);
            }
            """;
    private static final String FS = """
            #version 330 core
            out vec4 color;
            void main(){color=vec4(1.);}
            """;
    private static int generationProgram, instanceGenerationProgram, sourceVao, instanceSourceVao,
            instanceSourceVbo, outputVao, outputVbo, indexVbo, sourceBirthVbo;
    private static int sourceStride;
    private static int capacity, outputBytes;
    private static long uploadedBirth;
    private static Object lastLevel;
    private static int lastGeneration = -1;
    private static boolean unavailable, submittedInCurrentCall;
    private static volatile boolean resetRequested;
    private static int frameDrawCalls, frameInstances, frameUploadedBytes, frameDatasets;
    private static int frameInstanceDrawCalls, frameInstanceUploads;
    private static final Map<String,Integer> analyticExpressionPrograms = new HashMap<>();
    private static final Map<Integer,Map<String,Integer>> analyticLocations = new HashMap<>();
    private static int activeAnalyticProgram;
    private static int lightVolumeTexture;
    private static FloatBuffer lightSamples;
    private static long totalInstanceDrawCalls, totalInstanceUploadBytes;
    private static final Map<String,Integer> instanceLocations = new HashMap<>();
    // Generation is per cohort, but native Minecraft fragment submission is per
    // consecutive compatible material. The output stays entirely GPU-resident.
    private static final int MIN_BATCH_CAPACITY = 1024;
    private static BatchState pendingState;
    private static ResourceLocation pendingTexture;
    private static int pendingParticles;
    private static long frameGenerationCalls, frameNativeSubmissions;

    private static final class BatchState {
        final VfxNativeSpriteBatcher.GlState gl = new VfxNativeSpriteBatcher.GlState();
        final int program, vao, array, tfBuffer, indexedTfBuffer, drawFbo, readFbo;
        final boolean rasterDiscard;
        final ShaderInstance shader;
        final int texture, lightTexture;
        BatchState() {
            gl.capture();
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            tfBuffer = GL11.glGetInteger(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING);
            indexedTfBuffer = GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING, 0);
            drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            rasterDiscard = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
            shader = RenderSystem.getShader();
            texture = RenderSystem.getShaderTexture(0);
            lightTexture = RenderSystem.getShaderTexture(2);
        }
        void restore() {
            if (rasterDiscard) GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            else GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, indexedTfBuffer);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, tfBuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
            if (shader != null) RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderTexture(2, lightTexture);
            gl.restore();
            if (Boolean.getBoolean("pyrovfx.render.stateAudit")) {
                gl.assertRestored();
                if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != drawFbo
                        || GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != readFbo
                        || GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING,0)
                        != indexedTfBuffer || GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)
                        != rasterDiscard)
                    throw new IllegalStateException("Batched native TF bridge leaked GL state");
            }
        }
    }

    public static long generationCallsThisFrame() { return frameGenerationCalls; }
    public static long nativeSubmissionsThisFrame() { return frameNativeSubmissions; }

    public static void resetFrameStats() {
        frameDrawCalls = frameInstances = frameUploadedBytes = frameDatasets = 0;
        frameInstanceDrawCalls = frameInstanceUploads = 0;
        frameGenerationCalls = frameNativeSubmissions = 0;
    }
    public static int drawCalls() { return frameDrawCalls; }
    public static int instanceDrawCalls() { return frameInstanceDrawCalls; }
    public static int instanceUploadedBytes() { return frameInstanceUploads; }
    public static long totalInstanceDrawCalls() { return totalInstanceDrawCalls; }
    public static long totalInstanceUploadBytes() { return totalInstanceUploadBytes; }
    public static int instances() { return frameInstances; }
    public static int uploadedBytes() { return frameUploadedBytes; }
    public static int uploadedDatasets() { return frameDatasets; }
    public static long geometryBytes() { return outputBytes; }
    public static long uploadedBirthBytes() { return uploadedBirth; }
    public static int outputCapacityParticles() { return capacity; }
    /** Invalidate resources at the next render-thread call (safe on reload listener thread). */
    public static void invalidateAfterReload() { resetRequested = true; }

    /** Release native GL resources after a world or shader generation transition. */
    public static void maintain() {
        RenderSystem.assertOnRenderThread();
        var mc = Minecraft.getInstance();
        if (resetRequested || lastLevel != mc.level
                || lastGeneration != VfxGpuBirthBuffers.generation()) {
            flush();
            release();
            lastLevel = mc.level;
            lastGeneration = VfxGpuBirthBuffers.generation();
            resetRequested = false;
            unavailable = false;
        }
    }

    private VfxNativeGpuGeometryBridge() {}

    /** Explicit, lossless feature gate. Unsupported effects stay on the current
     * Minecraft/CPU fallback path instead of silently losing appearance features. */
    public static boolean supports(net.kofllee.pyrovfx.client.vfx.VfxAnalyticParticlePool pool) {
        var d = pool.definition();
        if (pool.liveCount() == 0 || pool.model()
                || d.render().sprite() == null
                || (!pool.gpuBirthAppearance()
                    && (!d.render().appearance().color().isSharedParticleAgeOnly()
                        || !d.render().appearance().scale().isSharedParticleAgeOnly()))) return false;
        var blend = d.render().sprite().blendMode();
        return blend == net.kofllee.pyrovfx.vfx.type.VfxBlendMode.ADDITIVE;
    }

    /** True if submitted; false asks the calling renderer to use its native fallback. */
    public static boolean render(net.kofllee.pyrovfx.client.vfx.VfxAnalyticParticlePool pool,
                                 Camera camera, float partial) {
        RenderSystem.assertOnRenderThread();
        if (!supports(pool) || unavailable) return false;
        maintain();
        if (!org.lwjgl.opengl.GL.getCapabilities().OpenGL33) return false;
        boolean worldLit = pool.definition().render().environmentLighting();
        boolean sharedLight = worldLit && pool.definition().spawnShape().type()
                    == net.kofllee.pyrovfx.vfx.type.VfxSpawnShapeType.POINT
                && pool.definition().motion().mode()
                    == net.kofllee.pyrovfx.vfx.type.VfxMotionMode.STATIC;
        var lightRegion = worldLit && !sharedLight
                ? VfxAnalyticLightVolume.plan(pool.lightBounds(partial), pool.drawCount()) : null;
        if (worldLit && !sharedLight && lightRegion == null) return false;
        submittedInCurrentCall = false;
        try {
            drawPool(pool, camera, partial, sharedLight, lightRegion);
            return true;
        } catch (RuntimeException error) {
            // Preserve already generated earlier cohorts; the failed one
            // still uses the established CPU fallback path when not submitted.
            try { flush(); } catch (RuntimeException flushFailure) {
                error.addSuppressed(flushFailure);
            }
            unavailable = true;
            LOG.error("Native GPU geometry bridge disabled after error; using fallback for later cohorts", error);
            return submittedInCurrentCall;
        }
    }

    private static void drawPool(net.kofllee.pyrovfx.client.vfx.VfxAnalyticParticlePool pool,
            Camera camera, float partial, boolean sharedLight,
            VfxAnalyticLightVolume.Region lightRegion) {
        int n = pool.drawCount();
        if (n == 0) return;
        ResourceLocation texture = pool.definition().render().sprite().texture();
        if (pendingParticles > 0 && !texture.equals(pendingTexture)) flush();
        if (pendingState == null) {
            pendingState = new BatchState();
            pendingTexture = texture;
        }
        try {
            init();
            if (pool.gpuBuffer() == 0
                    || pool.gpuGeneration() != VfxGpuBirthBuffers.generation()
                    || pool.dirty() || !GL15.glIsBuffer(pool.gpuBuffer())) {
                if (pool.gpuBuffer() != 0 && GL15.glIsBuffer(pool.gpuBuffer()))
                    VfxGpuBirthBuffers.retireBuffer(pool.gpuBuffer());
                int buffer = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
                var data = pool.data();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
                pool.setGpuBuffer(buffer, VfxGpuBirthBuffers.generation());
                uploadedBirth += data.remaining();
                frameUploadedBytes += data.remaining();
                frameDatasets++;
            }
            if (sourceBirthVbo != pool.gpuBuffer() || sourceStride != pool.stride()) {
                sourceBirthVbo = pool.gpuBuffer();
                sourceStride = pool.stride();
                GL30.glBindVertexArray(sourceVao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, sourceBirthVbo);
                GL33.glVertexAttribDivisor(0, 1);
                GL33.glVertexAttribDivisor(1, 1);
            }
            activeAnalyticProgram = analyticProgram(pool);
            GL20.glUseProgram(activeAnalyticProgram);
            // Same per-cohort animated appearance evaluation as the existing GPU renderer.
            pool.updateAppearance(partial);
            var coeff = pool.plan().coefficients(pool.age(), partial);
            var accel = pool.effectiveAcceleration();
            var shift = pool.parametricOffset(partial);
            var color = pool.color();
            var rect = pool.uvRect();
            var size = pool.scale();
            Vector3f right = new Vector3f(1,0,0).rotate(camera.rotation());
            Vector3f up = new Vector3f(0,1,0).rotate(camera.rotation());
            var facing = pool.definition().render().facing();
            switch (facing) {
                case WORLD_X -> { right.set(0,0,-1); up.set(0,1,0); }
                case WORLD_Y -> { right.set(1,0,0); up.set(0,0,1); }
                case WORLD_Z -> { right.set(1,0,0); up.set(0,1,0); }
                case CAMERA_HORIZONTAL -> {
                    float angle = (float)Math.toRadians(-camera.getYRot());
                    right.set((float)Math.cos(angle),0,(float)-Math.sin(angle));
                    up.set(0,1,0);
                }
                default -> { }
            }
            uniform3("uCamera",(float)camera.getPosition().x,
                    (float)camera.getPosition().y,(float)camera.getPosition().z);
            uniform3("uRight",right.x,right.y,right.z);
            uniform3("uUp",up.x,up.y,up.z);
            uniform3("uAcceleration",(float)accel.x,(float)accel.y,(float)accel.z);
            uniform3("uParametric",(float)shift.x,(float)shift.y,(float)shift.z);
            var source = pool.sourceTransform();
            var sourceRotation = source.rotation();
            uniform3("uSourcePosition",(float)source.position().x,
                    (float)source.position().y,(float)source.position().z);
            GL20.glUniform4f(loc("uSourceRotation"),sourceRotation.x,
                    sourceRotation.y,sourceRotation.z,sourceRotation.w);
            GL20.glUniform1i(loc("uLocalSpace"),pool.localSpace()?1:0);
            GL20.glUniform1i(loc("uVelocityFacing"),facing==VfxFacingMode.VELOCITY?1:0);
            GL20.glUniform1i(loc("uIndividualLifetimes"),pool.individualLifetimes()?1:0);
            GL20.glUniform1f(loc("uCurrentCohortAge"),pool.age());
            GL20.glUniform2f(loc("uVelocityMotion"),
                    (float)Math.pow(pool.plan().dragMultiplier(),pool.age()),
                    (float)pool.plan().coefficientsAt(pool.age())[0]);
            var spin = pool.spriteRotation(partial);
            uniform3("uSpriteRotation",(float)spin.x,(float)spin.y,(float)spin.z);
            GL20.glUniform2f(loc("uMotion"),(float)coeff[0],(float)coeff[1]);
            GL20.glUniform2f(loc("uScale"),(float)size.x,(float)size.y);
            GL20.glUniform4f(loc("uColor"),(float)color.r(),(float)color.g(),
                    (float)color.b(),(float)color.a());
            GL20.glUniform4f(loc("uUvRect"),rect.u0(),rect.v0(),rect.u1(),rect.v1());
            int lightMode = lightRegion != null ? 2 : sharedLight ? 1 : 0;
            GL20.glUniform1i(loc("uLightMode"),lightMode);
            if (sharedLight) {
                var pos = pool.sharedLightPosition();
                int packed = VfxRenderLight.getParticleLight(pos.x,pos.y,pos.z);
                GL30.glUniform2ui(loc("uSharedLight"),
                        ((packed >> 4) & 15) * 16, ((packed >> 20) & 15) * 16);
            }
            if (lightRegion != null) {
                uploadLightVolume(lightRegion);
                GL20.glUniform1i(loc("uLightVolume"),3);
                GL20.glUniform3i(loc("uLightVolumeOrigin"),
                        lightRegion.x(),lightRegion.y(),lightRegion.z());
            }
            boolean randomUv = pool.definition().render().sprite().uv().randomStartFrame();
            GL20.glUniform1i(loc("uRandomStartFrame"),randomUv?1:0);
            if (randomUv) {
                var uvState=pool.uvState();
                GL20.glUniform1i(loc("uUvBaseFrame"),pool.uvBaseFrame());
                GL20.glUniform1i(loc("uUvFrameCount"),uvState.frameCount());
                GL20.glUniform1i(loc("uUvLoop"),uvState.loop()?1:0);
                GL20.glUniform2f(loc("uUvStepNormalized"),
                        (float)(uvState.uvStep().x()/uvState.textureSize().x()),
                        (float)(uvState.uvStep().y()/uvState.textureSize().y()));
            }
            if (pool.gpuBirthAppearance()) {
                GL20.glUniform1f(loc("uPrevAge"), Math.max(0,pool.age()-2));
                GL20.glUniform1f(loc("uCurrentAge"), Math.max(0,pool.age()-1));
                GL20.glUniform1f(loc("uLife"), pool.plan().lifetime());
                GL20.glUniform1f(loc("uPartial"),partial);
                int currentTick=pool.age();
                int prevTick=Math.max(0,currentTick-1);
                int beforeTick=Math.max(0,currentTick-2);
                var plan=pool.plan();
                double[] prev=plan.coefficientsAt(prevTick);
                double[] current=plan.coefficientsAt(currentTick);
                double[] before=plan.coefficientsAt(beforeTick);
                GL20.glUniform2f(loc("uAppearancePrevMotion"),
                        (float)prev[0],(float)prev[1]);
                GL20.glUniform2f(loc("uAppearanceCurrentMotion"),
                        (float)current[0],(float)current[1]);
                GL20.glUniform2f(loc("uAppearanceBeforePrevMotion"),
                        (float)before[0],(float)before[1]);
                GL20.glUniform2f(loc("uAppearancePrevVelocity"),
                        (float)Math.pow(plan.dragMultiplier(),prevTick),(float)prev[0]);
                GL20.glUniform2f(loc("uAppearanceCurrentVelocity"),
                        (float)Math.pow(plan.dragMultiplier(),currentTick),(float)current[0]);
            }
            // Each cohort keeps its own source buffer, expression program and
            // uniforms. Only the resulting Minecraft-format vertices are
            // appended to a compatible material batch on the GPU.
            for (int base = 0; base < n; base += MAX_BATCH) {
                int batch = Math.min(MAX_BATCH, n - base);
                if (pendingParticles + batch > capacity) {
                    if (pendingParticles > 0) {
                        int desired = Math.min(MAX_BATCH,
                                Math.max(batch, Math.max(MIN_BATCH_CAPACITY, capacity * 2)));
                        flush();
                        pendingState = new BatchState();
                        pendingTexture = texture;
                        ensureOutputCapacity(desired);
                    } else {
                        ensureOutputCapacity(Math.min(MAX_BATCH,
                                Math.max(MIN_BATCH_CAPACITY, batch)));
                    }
                }
                long offset = (long)base * pool.stride();
                GL20.glUseProgram(activeAnalyticProgram);
                GL30.glBindVertexArray(sourceVao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, sourceBirthVbo);
                GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,pool.stride(),offset);
                GL20.glVertexAttribPointer(1,3,GL11.GL_FLOAT,false,pool.stride(),offset+12L);
                if (pool.birthRandomAttribute()) {
                    GL20.glEnableVertexAttribArray(2);
                    GL20.glVertexAttribPointer(2,1,GL11.GL_FLOAT,false,pool.stride(),offset+24L);
                } else GL20.glDisableVertexAttribArray(2);
                if (pool.individualLifetimes()) {
                    GL20.glEnableVertexAttribArray(3);
                    GL20.glVertexAttribPointer(3,1,GL11.GL_FLOAT,false,pool.stride(),
                            offset+pool.lifetimeAttributeOffset());
                } else GL20.glDisableVertexAttribArray(3);
                GL30.glBindBufferRange(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,outputVbo,
                        (long)pendingParticles * VERTICES_PER_PARTICLE * STRIDE,
                        (long)batch * VERTICES_PER_PARTICLE * STRIDE);
                GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
                boolean begun = false;
                try {
                    GL30.glBeginTransformFeedback(GL11.GL_POINTS);
                    begun = true;
                    GL31.glDrawArraysInstanced(GL11.GL_POINTS,0,VERTICES_PER_PARTICLE,batch);
                } finally {
                    if (begun) GL30.glEndTransformFeedback();
                    GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
                }
                submittedInCurrentCall = true;
                pendingParticles += batch;
                frameGenerationCalls++;
            }
        } catch (Throwable e) {
            LOG.error("Native GPU geometry bridge failed",e);
            throw new RuntimeException("Native GPU geometry bridge failed",e);
        }
    }

    /** Submit all accumulated additive geometry using ONE Minecraft shader
     * draw. The pool's shaders remain geometry-only; no custom fragment pass. */
    public static void flush() {
        RenderSystem.assertOnRenderThread();
        BatchState state = pendingState;
        if (state == null) return;
        try {
            if (pendingParticles > 0) {
                var mc = Minecraft.getInstance();
                mc.gameRenderer.lightTexture().turnOnLightLayer();
                RenderSystem.setShader(GameRenderer::getParticleShader);
                RenderSystem.setShaderTexture(0, pendingTexture);
                ShaderInstance nativeShader = RenderSystem.getShader();
                nativeShader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,
                        RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), mc.getWindow());
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE,
                        GL11.GL_ONE, GL11.GL_ONE);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                GL30.glBindVertexArray(outputVao);
                nativeShader.apply();
                try {
                    GL11.glDrawElements(GL11.GL_TRIANGLES,
                            INDICES_PER_PARTICLE * pendingParticles, GL11.GL_UNSIGNED_INT, 0L);
                    frameDrawCalls++;
                    frameNativeSubmissions++;
                    frameInstances += pendingParticles;
                } finally {
                    nativeShader.clear();
                }
            }
        } finally {
            pendingState = null;
            pendingParticles = 0;
            pendingTexture = null;
            state.restore();
        }
    }


    /** Shared bounded native vertex and index storage for both GPU sources. */
    private static void ensureOutputCapacity(int requested) {
        if (capacity >= requested) return;
        capacity = Math.max(1, Integer.highestOneBit(Math.max(1, requested - 1)) << 1);
        if (capacity < requested) capacity = requested;
        GL30.glBindVertexArray(outputVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, outputVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,
                (long) capacity * VERTICES_PER_PARTICLE * STRIDE, GL15.GL_STREAM_DRAW);
        java.nio.IntBuffer indices = org.lwjgl.BufferUtils.createIntBuffer(capacity * INDICES_PER_PARTICLE);
        for (int i = 0; i < capacity; i++) {
            int j = i * VERTICES_PER_PARTICLE;
            indices.put(j).put(j + 1).put(j + 2).put(j).put(j + 2).put(j + 3);
        }
        indices.flip();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexVbo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
        outputBytes = capacity * (VERTICES_PER_PARTICLE * STRIDE + INDICES_PER_PARTICLE * Integer.BYTES);
    }

    /**
     * Renders an already sorted, compatible CPU sprite batch through the native
     * Minecraft particle ShaderInstance. Transform Feedback performs vertex
     * expansion from 21 floats/instance on the GPU, retaining CPU-side batching
     * and global far-to-near alpha order. No GPU readback occurs.
     */
    public static boolean renderInstances(ByteBuffer instances, int count,
                                          ResourceLocation texture, VfxFacingMode facing,
                                          boolean rotating, boolean worldLit, boolean alpha,
                                          Camera camera) {
        RenderSystem.assertOnRenderThread();
        // The CPU sprite batch uses the same native vertex output storage.
        // Never overwrite geometry still awaiting its additive material draw.
        flush();
        if (count == 0 || unavailable || Boolean.getBoolean("pyrovfx.render.forceLegacy"))
            return false;
        maintain();
        if (!org.lwjgl.opengl.GL.getCapabilities().OpenGL33) return false;
        boolean submitted = false;
        var mc = Minecraft.getInstance();
        var glState = new VfxNativeSpriteBatcher.GlState();
        glState.capture();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousTf = GL11.glGetInteger(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING);
        int previousIndexedTf = GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING,0);
        int previousDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean previousDiscard = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        ShaderInstance previousShader = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        int previousLightTexture = RenderSystem.getShaderTexture(2);
        try {
            initInstanceProgram();
            ensureOutputCapacity(count);
            GL30.glBindVertexArray(instanceSourceVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceSourceVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STREAM_DRAW);
            frameInstanceUploads += instances.remaining();
            totalInstanceUploadBytes += instances.remaining();

            GL20.glUseProgram(instanceGenerationProgram);
            var right = new Vector3f(1,0,0).rotate(camera.rotation());
            var up = new Vector3f(0,1,0).rotate(camera.rotation());
            switch (facing) {
                case WORLD_X -> { right.set(0,0,-1); up.set(0,1,0); }
                case WORLD_Y -> { right.set(1,0,0); up.set(0,0,1); }
                case WORLD_Z -> { right.set(1,0,0); up.set(0,1,0); }
                case CAMERA_HORIZONTAL -> {
                    float a = (float)Math.toRadians(-camera.getYRot());
                    right.set((float)Math.cos(a),0,(float)-Math.sin(a));
                    up.set(0,1,0);
                }
                default -> { }
            }
            GL20.glUniform3f(instanceLoc("uRight"),right.x,right.y,right.z);
            GL20.glUniform3f(instanceLoc("uUp"),up.x,up.y,up.z);
            GL20.glUniform1i(instanceLoc("uHasRotation"),rotating ? 1 : 0);
            GL20.glUniform1i(instanceLoc("uVelocityFacing"),
                    facing == VfxFacingMode.VELOCITY ? 1 : 0);
            GL20.glUniform1i(instanceLoc("uUseLight"),worldLit ? 1 : 0);

            mc.gameRenderer.lightTexture().turnOnLightLayer();
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0,texture);
            ShaderInstance shader=RenderSystem.getShader();
            shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,
                    RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), mc.getWindow());
            GL11.glEnable(GL11.GL_BLEND);
            if(alpha) GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            else GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,
                    GL11.GL_ONE,GL11.GL_ONE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL20.glUseProgram(instanceGenerationProgram);
            GL30.glBindVertexArray(instanceSourceVao);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,outputVbo);
            GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            boolean begun=false;
            try {
                GL30.glBeginTransformFeedback(GL11.GL_POINTS);
                begun=true;
                GL31.glDrawArraysInstanced(GL11.GL_POINTS,0,VERTICES_PER_PARTICLE,count);
            } finally {
                if(begun) GL30.glEndTransformFeedback();
                GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            }
            GL30.glBindVertexArray(outputVao);
            shader.apply();
            GL11.glDrawElements(GL11.GL_TRIANGLES,INDICES_PER_PARTICLE*count,GL11.GL_UNSIGNED_INT,0L);
            submitted=true;
            frameInstanceDrawCalls++;
            totalInstanceDrawCalls++;
            shader.clear();
        } catch (RuntimeException failure) {
            unavailable = true;
            LOG.error("Native CPU sprite batch failed; retaining legacy-compatible rendering",failure);
        } finally {
            if (previousDiscard) GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            else GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,previousIndexedTf);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,previousTf);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,previousDrawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previousReadFbo);
            GL20.glUseProgram(previousProgram);
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,previousArray);
            if(previousShader != null) RenderSystem.setShader(()->previousShader);
            RenderSystem.setShaderTexture(0,previousTexture);
            RenderSystem.setShaderTexture(2,previousLightTexture);
            glState.restore();
            if(Boolean.getBoolean("pyrovfx.render.stateAudit")) {
                glState.assertRestored();
                if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=previousDrawFbo
                        ||GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=previousReadFbo
                        ||GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING,0)!=previousIndexedTf
                        ||GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)!=previousDiscard)
                    throw new IllegalStateException("Native CPU sprite GL state leak");
            }
        }
        return submitted;
    }

    private static int instanceLoc(String name) {
        return instanceLocations.computeIfAbsent(name,
                n->GL20.glGetUniformLocation(instanceGenerationProgram,n));
    }

    private static void initInstanceProgram() {
        init();
        if(instanceGenerationProgram!=0)return;
        int vs=compile(GL20.GL_VERTEX_SHADER,INSTANCE_VS), fs=compile(GL20.GL_FRAGMENT_SHADER,FS);
        int newProgram=GL20.glCreateProgram();
        try {
            GL20.glAttachShader(newProgram,vs);
            GL20.glAttachShader(newProgram,fs);
            GL30.glTransformFeedbackVaryings(newProgram,
                    new String[]{"outPosition","outUv","outColor","outLight"},
                    GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(newProgram);
            if(GL20.glGetProgrami(newProgram,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)
                throw new IllegalStateException("Native instance TF link: "
                        +GL20.glGetProgramInfoLog(newProgram));
        } catch(RuntimeException failure) {
            GL20.glDeleteProgram(newProgram);
            throw failure;
        } finally {
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
        }
        instanceGenerationProgram=newProgram;
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            instanceSourceVao=GL30.glGenVertexArrays();
            instanceSourceVbo=GL15.glGenBuffers();
            GL30.glBindVertexArray(instanceSourceVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instanceSourceVbo);
            final int stride=21*Float.BYTES;
            int[] channels={3,2,4,4,3,2,3};
            int offset=0;
            for(int i=0;i<channels.length;i++){
                GL20.glEnableVertexAttribArray(i);
                GL20.glVertexAttribPointer(i,channels[i],GL11.GL_FLOAT,false,stride,(long)offset);
                GL33.glVertexAttribDivisor(i,1);
                offset+=channels[i]*Float.BYTES;
            }
        } finally {
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldArray);
        }
    }

    /** Run on the render thread. The birth VBO belongs to VfxAnalyticParticlePool. */
    public static void shutdown() {
        RenderSystem.assertOnRenderThread();
        flush();
        release();
        lastLevel = null;
        lastGeneration = -1;
        resetRequested = false;
        unavailable = false;
    }
    private static void release() {
        if (lightVolumeTexture != 0) GL11.glDeleteTextures(lightVolumeTexture);
        lightVolumeTexture = 0;
        if (generationProgram != 0) GL20.glDeleteProgram(generationProgram);
        for (int id : analyticExpressionPrograms.values()) GL20.glDeleteProgram(id);
        analyticExpressionPrograms.clear();
        analyticLocations.clear();
        activeAnalyticProgram=0;
        if (instanceGenerationProgram != 0) GL20.glDeleteProgram(instanceGenerationProgram);
        if (instanceSourceVao != 0) GL30.glDeleteVertexArrays(instanceSourceVao);
        if (instanceSourceVbo != 0) GL15.glDeleteBuffers(instanceSourceVbo);
        if (sourceVao != 0) GL30.glDeleteVertexArrays(sourceVao);
        if (outputVao != 0) GL30.glDeleteVertexArrays(outputVao);
        if (outputVbo != 0) GL15.glDeleteBuffers(outputVbo);
        if (indexVbo != 0) GL15.glDeleteBuffers(indexVbo);
        generationProgram = instanceGenerationProgram = sourceVao = instanceSourceVao =
                instanceSourceVbo = outputVao = outputVbo = indexVbo = 0;
        sourceBirthVbo = sourceStride = capacity = outputBytes = 0;
        analyticLocations.clear();
        instanceLocations.clear();
    }

    /** Exact bounded Minecraft block/sky light volume for GPU-resident cohorts. */
    private static void uploadLightVolume(VfxAnalyticLightVolume.Region region) {
        int floats = region.cells() * 2;
        if (lightSamples == null || lightSamples.capacity() < floats) {
            lightSamples = ByteBuffer.allocateDirect(floats * Float.BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
        lightSamples.clear();
        for (int z = 0; z < region.depth(); z++)
            for (int y = 0; y < region.height(); y++)
                for (int x = 0; x < region.width(); x++) {
                    int packed = VfxRenderLight.getParticleLight(
                            region.x() + x, region.y() + y, region.z() + z);
                    lightSamples.put((((packed >> 4) & 15) + 0.5f) / 16f);
                    lightSamples.put((((packed >> 20) & 15) + 0.5f) / 16f);
                }
        lightSamples.flip();
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        if (lightVolumeTexture == 0) {
            lightVolumeTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, lightVolumeTexture);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        } else {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, lightVolumeTexture);
        }
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_RG16F,
                region.width(), region.height(), region.depth(), 0,
                GL30.GL_RG, GL11.GL_FLOAT, lightSamples);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static int loc(String name) {
        return analyticLocations.computeIfAbsent(activeAnalyticProgram,
                shader->new HashMap<>()).computeIfAbsent(name,
                uniform->GL20.glGetUniformLocation(activeAnalyticProgram,uniform));
    }
    private static void uniform3(String name,float x,float y,float z) {
        GL20.glUniform3f(loc(name),x,y,z);
    }

    private static void init() {
        if (generationProgram != 0) return;
        int vs = compile(GL20.GL_VERTEX_SHADER,VS), fs = compile(GL20.GL_FRAGMENT_SHADER,FS);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p,vs);
        GL20.glAttachShader(p,fs);
        GL30.glTransformFeedbackVaryings(p,
                new String[]{"outPosition","outUv","outColor","outLight"},
                GL30.GL_INTERLEAVED_ATTRIBS);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)
            throw new IllegalStateException("TF shader link: "+GL20.glGetProgramInfoLog(p));
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        generationProgram=p;
        sourceVao=GL30.glGenVertexArrays();
        outputVao=GL30.glGenVertexArrays();
        outputVbo=GL15.glGenBuffers();
        indexVbo=GL15.glGenBuffers();
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL30.glBindVertexArray(sourceVao);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL33.glVertexAttribDivisor(2, 1);
        GL33.glVertexAttribDivisor(3, 1);
        GL30.glBindVertexArray(outputVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,outputVbo);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,indexVbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glEnableVertexAttribArray(2);
        GL20.glEnableVertexAttribArray(3);
        GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,STRIDE,0L);
        GL20.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,STRIDE,12L);
        GL20.glVertexAttribPointer(2,4,GL11.GL_UNSIGNED_BYTE,true,STRIDE,20L);
        GL30.glVertexAttribIPointer(3,2,GL11.GL_UNSIGNED_SHORT,STRIDE,24L);
        GL30.glBindVertexArray(oldVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer);
    }

    /**
     * GPU expression bodies come from the existing validated expression
     * compiler. Only the geometry attributes are generated here; Minecraft
     * still supplies the final texture, lighting, fog and fragment program.
     */
    private static int analyticProgram(net.kofllee.pyrovfx.client.vfx.VfxAnalyticParticlePool pool) {
        if (!pool.gpuBirthAppearance()) return generationProgram;
        String scale=pool.gpuScaleGlsl();
        String color=pool.gpuColorGlsl();
        String key=scale+"|"+color;
        Integer cached=analyticExpressionPrograms.get(key);
        if(cached!=null)return cached;
        String generated="""
                float vfxRandom = iRandom;
                float vfxLifetime = uLife;
                float vfxAge = uPrevAge;
                vec3 vfxPos = birth + velocity * uAppearancePrevMotion.x
                        + uAcceleration * uAppearancePrevMotion.y;
                vec3 vfxPrevPos = birth + velocity * uAppearanceBeforePrevMotion.x
                        + uAcceleration * uAppearanceBeforePrevMotion.y;
                vec3 vfxVelocity = velocity * uAppearancePrevVelocity.x
                        + uAcceleration * uAppearancePrevVelocity.y;
                vec2 previousSize = %s;
                vec4 previousColor = %s;
                vfxAge = uCurrentAge;
                vfxPrevPos = vfxPos;
                vfxPos = birth + velocity * uAppearanceCurrentMotion.x
                        + uAcceleration * uAppearanceCurrentMotion.y;
                vfxVelocity = velocity * uAppearanceCurrentVelocity.x
                        + uAcceleration * uAppearanceCurrentVelocity.y;
                vec2 currentSize = %s;
                vec4 currentColor = %s;
                size = mix(previousSize, currentSize, uPartial);
                factor = mix(previousColor, currentColor, uPartial);
                """.formatted(scale,color,scale,color);
        int vs=compile(GL20.GL_VERTEX_SHADER,VS.replace("// GPU_APPEARANCE",generated));
        int fs=0,program=0;
        try {
            fs=compile(GL20.GL_FRAGMENT_SHADER,FS);
            program=GL20.glCreateProgram();
            GL20.glAttachShader(program,vs);
            GL20.glAttachShader(program,fs);
            GL30.glTransformFeedbackVaryings(program,
                    new String[]{"outPosition","outUv","outColor","outLight"},
                    GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)
                throw new IllegalStateException("Analytic native geometry expression shader: "
                        +GL20.glGetProgramInfoLog(program));
            analyticExpressionPrograms.put(key,program);
            return program;
        } catch(RuntimeException failure) {
            if(program!=0)GL20.glDeleteProgram(program);
            throw failure;
        } finally {
            GL20.glDeleteShader(vs);
            if(fs!=0)GL20.glDeleteShader(fs);
        }
    }

    private static int compile(int type,String src) {
        int s=GL20.glCreateShader(type);
        GL20.glShaderSource(s,src);
        GL20.glCompileShader(s);
        if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)!=GL11.GL_TRUE)
            throw new IllegalStateException(GL20.glGetShaderInfoLog(s));
        return s;
    }
}
