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
        versionCode = 135
        versionName = "0.9.80-dev"
        buildConfigField("boolean", "FULL_FEATURES", "true")
        val supabaseUrl = System.getenv("NERO_SUPABASE_URL").orEmpty().trim().trimEnd('/')
        val supabaseKey = System.getenv("NERO_SUPABASE_PUBLISHABLE_KEY").orEmpty().trim()
        val googleClient = System.getenv("NERO_GOOGLE_CLIENT_ID").orEmpty().trim()
        require(supabaseUrl.isEmpty() || Regex("https://[a-z0-9]+\\.supabase\\.co").matches(supabaseUrl)) { "Invalid Supabase URL" }
        require(supabaseKey.isEmpty() || Regex("sb_publishable_[A-Za-z0-9_-]+").matches(supabaseKey)) { "Use a Supabase publishable key, never a secret/service role key" }
        require(googleClient.isEmpty() || Regex("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com").matches(googleClient)) { "Invalid Google OAuth client ID" }
        buildConfigField("String","SUPABASE_URL","\""+supabaseUrl+"\"")
        buildConfigField("String","SUPABASE_KEY","\""+supabaseKey+"\"")
        buildConfigField("String","GOOGLE_CLIENT","\""+googleClient+"\"")
        val shareHost = System.getenv("NERO_SHARE_HOST").orEmpty().trim().lowercase()
        require(shareHost.isEmpty() || Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+").matches(shareHost)) { "Invalid share host" }
        manifestPlaceholders["neroShareHost"] = shareHost.ifEmpty { "nero-share-unconfigured.invalid" }
        buildConfigField("String","SHARE_HOST","\""+shareHost+"\"")
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
// Both variants use the same catalogue and dependency conflict resolution.
configurations.configureEach {
    exclude(group = "com.google.firebase", module = "protolite-well-known-types")
}
dependencies {
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.github.teamnewpipe:newpipeextractor:0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
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
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
