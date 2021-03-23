plugins {
  `java-library`
  kotlin("jvm")
  id("org.jetbrains.dokka")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  compileOnly(libs.annotations.intellij)

  api(project(":workflow-core"))
  api(libs.kotlin.jdk6)
  api(libs.coroutines.core)
  api(libs.rxjava2)

  implementation(libs.coroutines.rx2)

  testImplementation(project(":workflow-testing"))
  testImplementation(libs.kotlin.test.jdk)
}
