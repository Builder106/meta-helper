plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
}

group = "com.metahelper"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.51.2@jar")
    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    compileOnly("org.projectlombok:lombok")
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("com.metahelper.service*", "com.metahelper.controller*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}


tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    finalizedBy(tasks.jacocoTestCoverageVerification)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}
