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

    /**
     * Fit for a log: neither the password nor the query string of the URL, which may carry one ({@code ?password=}).
     */
    @Override
    public String toString() {
        int queryStart = url.indexOf('?');
        String withoutQuery = queryStart < 0 ? url : url.substring(0, queryStart);
        return "DatabaseSettings[url=" + withoutQuery + ", username=" + username + "]";
    }
}
