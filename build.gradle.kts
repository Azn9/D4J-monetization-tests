plugins {
    id("java")
}

group = "dev.azn9"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://nexus.azn9.dev/repository/public/")
    mavenCentral()
}

dependencies {
    testImplementation("io.projectreactor:reactor-test:3.6.2")
    testImplementation("io.projectreactor:reactor-tools:3.6.2")
    testImplementation("io.projectreactor.addons:reactor-extra:3.5.1")
    testImplementation("com.discord4j:discord4j-core:3.2.7-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
