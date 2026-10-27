// Nothing here. AGP 9.4.1 bundles KGP 2.2.10 and its built-in Kotlin rejects an
// external KGP on the buildscript classpath: raising it to 2.4.21 and to 2.3.21
// both failed with "Could not generate a decorated class for KotlinAndroidTarget
// > com/android/build/gradle/api/BaseVariant", an API AGP 9 removed outright.
//
// The compiler is therefore fixed at 2.2.10, which can only read kotlin metadata
// up to 2.2.x. The whole Kotlin runtime family is pinned together in
// app/build.gradle.kts: stdlib alone is not enough, because leaving
// kotlin-reflect free to resolve higher is what shipped a kotlin.jvm.internal
// class the stdlib did not define, and the app died at launch.