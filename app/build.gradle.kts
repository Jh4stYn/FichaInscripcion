plugins { id("com.android.application") }
android {
 namespace = "pe.unsa.inscripcion"
 compileSdk = 36
 defaultConfig {
  applicationId = "pe.unsa.inscripcion"
  minSdk = 26
  targetSdk = 35
  versionCode = 2
  versionName = "1.1"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildTypes { release { isMinifyEnabled = false } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
