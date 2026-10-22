package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;

class MultipartConfigurationTest {

    private static final long ONE_MEGABYTE = 1024 * 1024;

    @Test
    void allowsTheRequestToExceedTheFileLimitByTheOverhead() {
        MultipartConfigElement config =
                MultipartConfiguration.withRequestOverhead(new MultipartConfigElement("/tmp", 500, 500, 0));

        assertThat(config.getMaxFileSize()).isEqualTo(500);
        assertThat(config.getMaxRequestSize()).isEqualTo(500 + ONE_MEGABYTE);
        assertThat(config.getLocation()).isEqualTo("/tmp");
        assertThat(config.getFileSizeThreshold()).isZero();
    }

    @Test
    void keepsAnUnlimitedRequestWhenFilesAreUnlimited() {
        MultipartConfigElement config =
                MultipartConfiguration.withRequestOverhead(new MultipartConfigElement("", -1, 10, 0));

        assertThat(config.getMaxRequestSize()).isEqualTo(-1);
    }
}
