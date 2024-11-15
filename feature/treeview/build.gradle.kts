plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish") version "0.25.2"
    id("maven-publish")
    signing
}

android {
    namespace = "io.github.dingyi222666.view.treeview"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        // targetSdk = 33

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}



mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.S01)

    signAllPublications()

    coordinates("io.github.dingyi222666", "treeview", "1.3.1")

    pom {
        name.set("TreeView")
        description.set("An TreeView implement in Android with RecyclerView written in kotlin.")
        inceptionYear.set("2022")
        url.set("https://github.com/dingyi222666/TreeView")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("dingyi222666")
                name.set("dingyi222666")
                url.set("https://github.com/dingyi222666")
            }
        }
        scm {
            url.set("https://github.com/dingyi222666/TreeView")
            connection.set("scm:git:git://github.com/dingyi222666/TreeView.git")
            developerConnection.set("scm:git:ssh://git@github.com/dingyi222666/TreeView.git")
        }
    }
}



dependencies {
    compileOnly("androidx.core:core-ktx:1.15.0")
    compileOnly("androidx.appcompat:appcompat:1.6.1")
    compileOnly("com.google.android.material:material:1.9.0")
}