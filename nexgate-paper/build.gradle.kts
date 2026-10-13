plugins {
    id("com.github.johnrengelman.shadow")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":nexgate-common"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.google.code.gson:gson:2.11.0")
    compileOnly("org.jetbrains:annotations:26.0.1")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "com.nexgate.libs.hikari")
    relocate("de.mkammerer.argon2", "com.nexgate.libs.argon2")
    relocate("com.google.gson", "com.nexgate.libs.gson")
}
```