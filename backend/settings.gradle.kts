rootProject.name = "bergacast"

// The tests create the schema with the migration module, as the migration job does in production. The module is
// absent from the Docker build context (backend/ only), which runs no test.
val migrationBuild = file("../migration")
if (migrationBuild.isDirectory) {
    includeBuild(migrationBuild)
}
