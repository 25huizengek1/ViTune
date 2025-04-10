dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("mappings.versions.toml"))
        }

        create("rootLibs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "mappings"