plugins {
    id("module")
    id("binary-compatibility-validator")
}

dependencies {
    implementation(libs.kotlin.compilerEmbeddable)
}
