plugins {
    `kotlin-dsl`
    alias(rootLibs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.smali)
    implementation(libs.smali.dexlib2)
    implementation(libs.mappings)
    implementation(rootLibs.agp)
    implementation(rootLibs.kotlin.serialization.json)
}