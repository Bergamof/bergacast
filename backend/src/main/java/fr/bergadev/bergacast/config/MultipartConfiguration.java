package fr.bergadev.bergacast.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Derives the multipart limits from the single {@code BERGACAST_MAX_UPLOAD_SIZE} setting: the audio file may be
 * exactly that large, the whole request may exceed it by the size of the metadata part and multipart framing.
 */
@Configuration(proxyBeanMethods = false)
class MultipartConfiguration {

    static final DataSize REQUEST_OVERHEAD = DataSize.ofMegabytes(1);
    private static final long UNLIMITED = -1;

    @Bean
    MultipartConfigElement multipartConfigElement(MultipartProperties properties) {
        return withRequestOverhead(properties.createMultipartConfig());
    }

    static MultipartConfigElement withRequestOverhead(MultipartConfigElement fileLimits) {
        long maxFileSize = fileLimits.getMaxFileSize();
        long maxRequestSize = maxFileSize < 0 ? UNLIMITED : maxFileSize + REQUEST_OVERHEAD.toBytes();
        return new MultipartConfigElement(
                fileLimits.getLocation(), maxFileSize, maxRequestSize, fileLimits.getFileSizeThreshold());
    }
}
