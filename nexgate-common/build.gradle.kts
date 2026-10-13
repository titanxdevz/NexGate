dependencies {
    // Crypto libraries
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("com.zaxxer:HikariCP:5.1.0") // Apache 2.0
    implementation("com.google.code.gson:gson:2.11.0")
    
    // For annotations
    compileOnly("org.jetbrains:annotations:26.0.1")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}
```