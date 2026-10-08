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
        // 国内镜像（网络不佳时可取消注释）
        // maven("https://maven.aliyun.com/repository/google")
        // maven("https://maven.aliyun.com/repository/public")
        // maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 国内镜像（网络不佳时可取消注释）
        // maven("https://maven.aliyun.com/repository/google")
        // maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "DokaCam"
include(":app")
