plugins {
    id("java")
}

group = "com.squidtempura"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("net.objecthunter:exp4j:0.4.8")
}

tasks.test {
    useJUnitPlatform()
}