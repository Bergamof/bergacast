package fr.bergadev.bergacast.requestlog;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Wraps the data source so that the request log tells which requests use the database. Spring Boot still finds the
 * Hikari pool behind it (health, metrics): {@link ConnectionTrackingDataSource} unwraps to it.
 */
@Component
class ConnectionTrackingPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof ConnectionTrackingDataSource)) {
            return new ConnectionTrackingDataSource(dataSource);
        }
        return bean;
    }
}
