plugins {
    id("com.android.application")
}

// URL del backend, sin secretos. Se cambia sin tocar el código:
//   ./gradlew assembleDebug -PvetstockApiUrl=http://192.168.1.50:8080
// 10.0.2.2 es, desde el emulador, el localhost del ordenador.
val urlDesarrollo = (findProperty("vetstockApiUrl") as String?) ?: "http://10.0.2.2:8080"
// En release solo se admite HTTPS (ver network_security_config): sin valor, la app avisa al iniciar sesión.
val urlProduccion = (findProperty("vetstockApiUrlProduccion") as String?) ?: ""

android {
    namespace = "es.florida.vetstock"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "es.florida.vetstock"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_URL", "\"$urlDesarrollo\"")
        }
        release {
            buildConfigField("String", "API_URL", "\"$urlProduccion\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
