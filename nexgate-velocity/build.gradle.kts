plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":nexgate-common"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("org.postgresql:postgresql:42.7.4")
    compileOnly("org.jetbrains:annotations:26.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("net.kyori:adventure-api:4.17.0")
    testImplementation("net.kyori:adventure-text-minimessage:4.17.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("NexGate-Velocity")
    archiveClassifier.set("")
    // relocate("com.zaxxer.hikari", "com.nexgate.libs.hikari")
    // relocate("de.mkammerer.argon2", "com.nexgate.libs.argon2")
    // relocate("com.google.gson", "com.nexgate.libs.gson")
}
