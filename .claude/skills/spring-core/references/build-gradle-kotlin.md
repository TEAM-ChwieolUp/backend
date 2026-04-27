# Gradle Kotlin 플러그인/의존성 설정

현재 `build.gradle.kts`는 Java 21 기반으로 셋업되어 있으나, **취얼업은 Kotlin으로 개발한다.** 도메인 코드 작성을 시작하기 전에 다음 항목을 추가해야 한다.

## 필수 변경 사항

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"  // @Component, @Service, @Configuration 등을 자동 open
    kotlin("plugin.jpa") version "2.1.0"     // @Entity 등에 no-arg 생성자 자동 추가
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")  // platform type을 nullable 미지정으로 막아 NPE 안전성 확보
    }
}

dependencies {
    // 기존
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    runtimeOnly("com.mysql:mysql-connector-j")

    // Kotlin 필수
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")  // data class JSON 직렬화

    // 추가 권장
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Spring AI (메일 분류·일정 추출)
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")  // MockK 사용
    }
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:mysql:1.20.4")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

## 왜 Lombok을 빼는가

Kotlin은 `data class`, `val`, primary constructor로 Lombok 기능(`@Getter`, `@Setter`, `@RequiredArgsConstructor`)을 모두 대체한다. Lombok과 Kotlin을 함께 쓰면 컴파일 순서 문제로 빌드가 깨질 수 있다.

## 디렉토리 구조 변경

```
src/main/java/        →  src/main/kotlin/
src/test/java/        →  src/test/kotlin/
```

`DemoApplication.java`를 `DemoApplication.kt`로 변환:

```kotlin
package com.cheerup.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
```
