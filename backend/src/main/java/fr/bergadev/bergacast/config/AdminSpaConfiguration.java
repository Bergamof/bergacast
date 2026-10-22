package fr.bergadev.bergacast.config;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the admin single-page application built into {@code classpath:/static/admin/}.
 *
 * <p>Paths that are not files fall back to {@code index.html} so that the client-side router handles them. When the
 * SPA is not bundled (development, tests), {@code /admin/**} simply answers 404.
 */
@Configuration(proxyBeanMethods = false)
class AdminSpaConfiguration implements WebMvcConfigurer {

    static final String ADMIN_PATH = "/admin/";
    private static final String SPA_LOCATION = "classpath:/static/admin/";
    private static final String INDEX = "index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ADMIN_PATH + "**")
                .addResourceLocations(SPA_LOCATION)
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", ADMIN_PATH);
        registry.addRedirectViewController("/admin", ADMIN_PATH);
        // Resource handlers ignore empty paths, so the SPA root is forwarded to its index explicitly.
        registry.addViewController(ADMIN_PATH).setViewName("forward:" + ADMIN_PATH + INDEX);
    }

    /** Resolves existing files, and routes of the SPA (paths without a file extension) to {@code index.html}. */
    static class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected @Nullable Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            return isClientRoute(resourcePath) ? super.getResource(INDEX, location) : null;
        }

        private static boolean isClientRoute(String resourcePath) {
            String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            return !lastSegment.contains(".");
        }
    }
}
