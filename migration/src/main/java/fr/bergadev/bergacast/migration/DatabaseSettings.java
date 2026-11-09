package fr.bergadev.bergacast.migration;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Connection to the database to migrate, read from the environment of the job. */
public record DatabaseSettings(String url, String username, String password) {

    static final String URL_VARIABLE = "BERGACAST_DATASOURCE_URL";
    static final String USERNAME_VARIABLE = "BERGACAST_DATASOURCE_USERNAME";
    static final String PASSWORD_VARIABLE = "BERGACAST_DATASOURCE_PASSWORD";

    /**
     * @throws IllegalArgumentException when a variable is missing or blank, naming every missing variable at once
     */
    public static DatabaseSettings fromEnvironment(Map<String, String> environment) {
        List<String> missing = Stream.of(URL_VARIABLE, USERNAME_VARIABLE, PASSWORD_VARIABLE)
                .filter(name -> environment.getOrDefault(name, "").isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing environment variable(s): " + String.join(", ", missing));
        }
        return new DatabaseSettings(
                environment.get(URL_VARIABLE), environment.get(USERNAME_VARIABLE), environment.get(PASSWORD_VARIABLE));
    }

    /** The password never ends up in a log through a stray {@code toString}. */
    @Override
    public String toString() {
        return "DatabaseSettings[url=" + url + ", username=" + username + "]";
    }
}
