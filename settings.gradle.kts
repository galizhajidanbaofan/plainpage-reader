pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // mavenCentral 放前面：kotlin-stdlib 等纯 Central 的构件没必要先去问 Google 的镜像，
        // 本机实测 dl.google.com 偶发连接超时，顺序反过来会让构建随机失败。
        mavenCentral()
        google()
    }
}

rootProject.name = "plainpage-reader"
include(":app")
