plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.vanniktech.maven.publish") version "0.30.0"
    signing
}

android {
    namespace = "com.example.shaderrippleeffect"
    compileSdk = 35

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
}

if ((project.findProperty("RELEASE_SIGNING_ENABLED")?.toString() ?: "false").toBoolean()) {
    signing {
        useGpgCmd()
        sign(publishing.publications)
    }
}

mavenPublishing {
    coordinates(
        artifactId = "android-shader-effect",
    )

    pom {
        name.set("Android AGSL Shader Playground")
        description.set("A Jetpack Compose library of AGSL shader animations: tap ripples, continuous waves, ripple reveal transitions, and drag-driven motion blur.")
        url.set("https://github.com/mejdi14/Android-AGSL-Shader-Playground")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        scm {
            url.set("https://github.com/mejdi14/Android-AGSL-Shader-Playground")
            connection.set("scm:git:git://github.com/mejdi14/Android-AGSL-Shader-Playground.git")
            developerConnection.set("scm:git:ssh://git@github.com/mejdi14/Android-AGSL-Shader-Playground.git")
        }
        developers {
            developer {
                id.set("mejdi14")
                name.set("Mejdi Hafiene")
                url.set("https://github.com/mejdi14/")
                email.set("mejdihafiene@gmail.com")
            }
        }
    }
}