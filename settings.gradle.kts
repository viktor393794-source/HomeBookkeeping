// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HomeBookkeeping" // Или как называется твой проект
include(":app") // !!! УБЕДИСЬ, ЧТО ЭТА СТРОКА ПРИСУТСТВУЕТ !!!
