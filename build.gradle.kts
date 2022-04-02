/*
 *  Copyright (c) 2021 Freya Arbjerg and contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  val kotlinVersion = "1.6.10"
  val gradleGitVersion = "2.3.2"
  val springBootVersion = "2.6.5"
  val sonarqubeVersion = "3.3"
  val testLoggerVersion = "3.1.0"
  val librespotLibVersion = "1.6.2"

  repositories {
    mavenLocal()
    maven("https://plugins.gradle.org/m2/")
    maven("https://repo.spring.io/plugins-release")
    maven("https://jitpack.io")
  }

  dependencies {
    classpath("com.gorylenko.gradle-git-properties:gradle-git-properties:$gradleGitVersion")
    classpath("org.springframework.boot:spring-boot-gradle-plugin:$springBootVersion")
    classpath("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:$sonarqubeVersion")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    classpath("org.jetbrains.kotlin:kotlin-allopen:$kotlinVersion")
    classpath("com.adarshr:gradle-test-logger-plugin:$testLoggerVersion")
    classpath("xyz.gianlu.librespot:librespot-lib:$librespotLibVersion")
  }
}

plugins {
  application
  idea
  id("org.springframework.boot") version "2.6.5"
  id("com.gorylenko.gradle-git-properties") version "2.3.2"
  id("org.ajoberstar.grgit") version "4.1.1"
  kotlin("jvm") version "1.6.10"
  kotlin("plugin.spring") version "1.6.10"
  id("com.adarshr.test-logger") version "3.1.0"
}

group = "lavalink"

description = "Play audio to discord voice channels"

application { mainClass.set("lavalink.server.Launcher") }

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

repositories {
  mavenCentral()
  jcenter()
  mavenLocal()
  maven("https://jitpack.io")
  maven("https://m2.dv8tion.net/releases")
}

val kotlinVersion = "1.6.10"

val lavaplayerVersion = "0db9ab6"
val lavaplayerIpRotatorVersion = "0.2.3"
val nettyEpollVersion = "4.1.75.Final:linux-x86_64"
val lavadspVersion = "0.7.7"
val librespotLibVersion = "1.6.2"

val springBootVersion = "2.6.5"
val springWebSocketVersion = "5.3.17"
val prometheusVersion = "0.15.0"
val koeVersion = "fbd5241"
val logbackVersion = "1.2.11"
val sentryVersion = "5.7.0"
val oshiVersion = "6.1.5"
val jsonOrgVersion = "20220320"
val gsonVersion = "2.9.0"
val spotbugsAnnotationsVersion = "4.6.0"

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

  // Audio Sending
  implementation("com.github.davidffa.koe:ext-udpqueue:$koeVersion") {
    exclude("com.sedmelluq", "lavaplayer")
  }
  implementation("com.github.davidffa.koe:core:$koeVersion") {
    exclude("org.slf4j", "slf4j-api")
    exclude("com.sedmelluq", "lavaplayer")
  }

  // Transport
  implementation("io.netty:netty-transport-native-epoll:$nettyEpollVersion")

  // Audio Player
  implementation("com.github.davidffa:lavaplayer-fork:$lavaplayerVersion")
  implementation("com.sedmelluq:lavaplayer-ext-youtube-rotator:$lavaplayerIpRotatorVersion") {
    exclude("com.sedmelluq", "lavaplayer")
  }

  // Filters
  implementation("com.github.natanbc:lavadsp:$lavadspVersion") {
    exclude("com.sedmelluq", "lavaplayer")
  }

  // Spotify
  implementation("xyz.gianlu.librespot:librespot-lib:$librespotLibVersion")

  // Spring
  implementation("org.springframework:spring-websocket:$springWebSocketVersion")
  implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion") {
    exclude("org.springframework.boot", "spring-boot-starter-tomcat")
  }
  implementation("org.springframework.boot:spring-boot-starter-undertow:$springBootVersion")

  // Logging and Statistics
  implementation("ch.qos.logback:logback-classic:$logbackVersion")
  implementation("io.sentry:sentry-logback:$sentryVersion")
  implementation("io.prometheus:simpleclient:$prometheusVersion")
  implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
  implementation("io.prometheus:simpleclient_logback:$prometheusVersion")
  implementation("io.prometheus:simpleclient_servlet:$prometheusVersion")

  // Native System Stuff
  implementation("com.github.oshi:oshi-core:$oshiVersion")

  // Json
  implementation("org.json:json:$jsonOrgVersion")
  implementation("com.google.code.gson:gson:$gsonVersion")

  // Test stuff
  compileOnly("com.github.spotbugs:spotbugs-annotations:$spotbugsAnnotationsVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
}

tasks {
  bootJar { archiveFileName.set("Lavalink.jar") }

  bootRun { dependsOn(compileTestKotlin) }

  build { doLast { println("Version: $version") } }

  test { useJUnitPlatform() }

  withType<KotlinCompile> { kotlinOptions.jvmTarget = "11" }
}