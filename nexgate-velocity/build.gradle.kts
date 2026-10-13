plugins {
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(project(":nexgate-common"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    compileOnly("org.jetbrains:annotations:26.0.1")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "com.nexgate.libs.hikari")
    relocate("de.mkammerer.argon2", "com.nexgate.libs.argon2")
    relocate("com.google.gson", "com.nexgate.libs.gson")
}
