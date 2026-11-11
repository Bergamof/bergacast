package fr.bergadev.bergacast.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Derives the multipart limits from the single {@code BERGACAST_MAX_UPLOAD_SIZE} setting: the audio file may be
 * exactly that large, the whole request may exceed it by the optional image of an episode (at most
 * {@code BERGACAST_MAX_IMAGE_UPLOAD_SIZE}), the metadata part and the multipart framing.
 */
@Configuration(proxyBeanMethods = false)
class MultipartConfiguration {

    static final DataSize REQUEST_OVERHEAD = DataSize.ofMegabytes(1);
    private static final long UNLIMITED = -1;

    /**
     * Only the image size is read here, not the whole {@link BergacastProperties}: this bean is created while the web
     * server starts, and binding every {@code bergacast.*} property that early would fix the ones a test registers
     * later (the object storage endpoint), sending its requests to the real storage.
     */
    @Bean
    MultipartConfigElement multipartConfigElement(
            MultipartProperties properties,
            @Value("${bergacast.max-image-upload-size:" + BergacastProperties.DEFAULT_MAX_IMAGE_UPLOAD_SIZE + "}")
                    DataSize maxImageSize) {
        return withRequestOverhead(properties.createMultipartConfig(), maxImageSize);
    }

    static MultipartConfigElement withRequestOverhead(MultipartConfigElement fileLimits, DataSize maxImageSize) {
        long maxFileSize = fileLimits.getMaxFileSize();
        long maxRequestSize =
                maxFileSize < 0 ? UNLIMITED : maxFileSize + maxImageSize.toBytes() + REQUEST_OVERHEAD.toBytes();
        return new MultipartConfigElement(
                fileLimits.getLocation(), maxFileSize, maxRequestSize, fileLimits.getFileSizeThreshold());
    }
}
