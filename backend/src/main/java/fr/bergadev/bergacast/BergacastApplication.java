package fr.bergadev.bergacast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BergacastApplication {

    static void main(String[] args) {
        SpringApplication.run(BergacastApplication.class, args);
    }
}
