plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
if (file("google-services.json").isFile) { apply(plugin = "com.google.gms.google-services") }
android {
    namespace = "br.com.timachado.neromusic"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.timachado.neromusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 40
        versionName = "0.9.18-dev"
        val catalogEndpoint = System.getenv("NERO_YOUTUBE_ENDPOINT").orEmpty()
        require(catalogEndpoint.isEmpty() || Regex("https://[A-Za-z0-9.-]+(?::443)?/[A-Za-z0-9/_-]+").matches(catalogEndpoint)) { "Invalid YouTube endpoint" }
        buildConfigField("String","YOUTUBE_ENDPOINT","\""+catalogEndpoint+"\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// NewPipe debug brings protobuf-javalite 4.x, which includes descriptor types.
// Firebase\u0027s legacy companion AAR duplicates those classes. Keep release untouched.
configurations.configureEach {
    if (name.contains("debug", ignoreCase = true)) {
        exclude(group = "com.google.firebase", module = "protolite-well-known-types")
    }
}
dependencies {
    debugImplementation("com.github.teamnewpipe:newpipeextractor:0.26.5")
    debugImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
