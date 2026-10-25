package fr.bergadev.bergacast.config;

import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * Native image metadata of the headless AWT and ImageIO code used to check and downscale podcast cover images
 * ({@code image/ImageResizer}).
 *
 * <p>The JDK native libraries of AWT ({@code libawt}, {@code libawt_headless}, {@code libjavajpeg}), which native-image
 * emits next to the executable, call back into Java through JNI: the classes and members they look up must be
 * registered, otherwise the image aborts with "FindClass ... failed". The list comes from the native-image tracing
 * agent run on the resizer with every kind of image it handles (JPEG and PNG, RGB, alpha, grayscale, palette, other
 * colour spaces, downscaled or not); the JDK internal classes are registered with all their declared members rather
 * than member by member, which is simpler to maintain for a negligible size cost.
 */
class ImageProcessingHints implements RuntimeHintsRegistrar {

    /** JDK classes whose fields and methods the AWT and ImageIO native libraries access through JNI. */
    static final List<String> JNI_ACCESSED_TYPES = List.of(
            "com.sun.imageio.plugins.jpeg.JPEGImageReader",
            "com.sun.imageio.plugins.jpeg.JPEGImageWriter",
            "java.awt.AlphaComposite",
            "java.awt.Color",
            "java.awt.geom.AffineTransform",
            "java.awt.geom.Path2D",
            "java.awt.geom.Path2D$Float",
            "java.awt.image.BufferedImage",
            "java.awt.image.ColorModel",
            "java.awt.image.IndexColorModel",
            "java.awt.image.Raster",
            "java.awt.image.SampleModel",
            "java.awt.image.SinglePixelPackedSampleModel",
            "javax.imageio.plugins.jpeg.JPEGHuffmanTable",
            "javax.imageio.plugins.jpeg.JPEGQTable",
            "sun.awt.SunHints",
            "sun.awt.image.BufImgSurfaceData$ICMColorData",
            "sun.awt.image.ByteComponentRaster",
            "sun.awt.image.BytePackedRaster",
            "sun.awt.image.IntegerComponentRaster",
            "sun.awt.image.ShortComponentRaster",
            "sun.java2d.Disposer",
            "sun.java2d.InvalidPipeException",
            "sun.java2d.NullSurfaceData",
            "sun.java2d.SunGraphics2D",
            "sun.java2d.SurfaceData",
            "sun.java2d.loops.Blit",
            "sun.java2d.loops.BlitBg",
            "sun.java2d.loops.CompositeType",
            "sun.java2d.loops.DrawGlyphList",
            "sun.java2d.loops.DrawGlyphListAA",
            "sun.java2d.loops.DrawGlyphListLCD",
            "sun.java2d.loops.DrawLine",
            "sun.java2d.loops.DrawParallelogram",
            "sun.java2d.loops.DrawPath",
            "sun.java2d.loops.DrawPolygons",
            "sun.java2d.loops.DrawRect",
            "sun.java2d.loops.FillParallelogram",
            "sun.java2d.loops.FillPath",
            "sun.java2d.loops.FillRect",
            "sun.java2d.loops.FillSpans",
            "sun.java2d.loops.GraphicsPrimitive",
            "sun.java2d.loops.GraphicsPrimitiveMgr",
            "sun.java2d.loops.MaskBlit",
            "sun.java2d.loops.MaskFill",
            "sun.java2d.loops.ScaledBlit",
            "sun.java2d.loops.SurfaceType",
            "sun.java2d.loops.TransformHelper",
            "sun.java2d.loops.XORComposite",
            "sun.java2d.pipe.Region",
            "sun.java2d.pipe.RegionIterator");

    /** Colour profiles of the predefined colour spaces (sRGB, gray, linear RGB...), loaded by the colour management. */
    static final String COLOR_PROFILES = "sun/java2d/cmm/profiles/*.pf";

    private static final MemberCategory[] ALL_DECLARED_MEMBERS = {
        MemberCategory.ACCESS_DECLARED_FIELDS,
        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
        MemberCategory.INVOKE_DECLARED_METHODS
    };
    private static final TypeReference STRING = TypeReference.of(String.class);

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        ReflectionHints jni = hints.jni();
        JNI_ACCESSED_TYPES.forEach(type -> jni.registerType(TypeReference.of(type), ALL_DECLARED_MEMBERS));
        // libawt checks headless mode, then loads libawt_headless next to itself; only these members, not all of
        // System and Boolean.
        jni.registerType(
                TypeReference.of("java.awt.GraphicsEnvironment"),
                type -> type.withMethod("isHeadless", List.of(), ExecutableMode.INVOKE));
        jni.registerType(Boolean.class, type -> type.withMethod("getBoolean", List.of(STRING), ExecutableMode.INVOKE));
        jni.registerType(System.class, type -> type.withMethod("load", List.of(STRING), ExecutableMode.INVOKE));

        ReflectionHints reflection = hints.reflection();
        // Java 2D instantiates its rasterizer reflectively and stores its loops in arrays created by reflection.
        reflection.registerType(
                TypeReference.of("sun.java2d.marlin.DMarlinRenderingEngine"),
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        reflection.registerType(TypeReference.of("sun.java2d.loops.GraphicsPrimitive[]"));
        // The ImageIO registry reads the format names of its service providers reflectively.
        reflection.registerType(
                TypeReference.of("javax.imageio.spi.ImageReaderWriterSpi"),
                type -> type.withMethod("getFormatNames", List.of(), ExecutableMode.INVOKE));
        Stream.of("javax.imageio.spi.ImageReaderSpi", "javax.imageio.spi.ImageWriterSpi")
                .forEach(type -> reflection.registerType(TypeReference.of(type)));

        hints.resources()
                .registerPattern(COLOR_PROFILES)
                .registerPattern("META-INF/services/javax.imageio.spi.*")
                .registerResourceBundle("sun.awt.resources.awt");
    }
}
