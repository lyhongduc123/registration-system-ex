plugins {
    id("java")
}

group = "org.lhduc.registration"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        compileOnly("org.projectlombok:lombok:1.18.38")
        annotationProcessor("org.projectlombok:lombok:1.18.38")
        testCompileOnly("org.projectlombok:lombok:1.18.38")
        testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

        implementation("org.slf4j:slf4j-api:2.0.18")
        runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
    }
}

tasks.test {
    useJUnitPlatform()
}