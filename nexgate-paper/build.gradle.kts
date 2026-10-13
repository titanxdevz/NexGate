plugins {
    id("com.gradleup.shadow")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":nexgate-common"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("de.mkammerer:argon2-jvm:2.12")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    compileOnly("org.jetbrains:annotations:26.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("NexGate-Paper")
    archiveClassifier.set("")
    // relocate("com.zaxxer.hikari", "com.nexgate.libs.hikari")
    // relocate("de.mkammerer.argon2", "com.nexgate.libs.argon2")
    // relocate("com.google.gson", "com.nexgate.libs.gson")
}
