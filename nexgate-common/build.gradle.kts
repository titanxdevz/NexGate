dependencies {
    // Crypto libraries
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("com.zaxxer:HikariCP:7.1.0") // Apache 2.0
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    
    // Adventure API (provided by Velocity & Paper runtimes)
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    
    // For annotations
    compileOnly("org.jetbrains:annotations:26.0.1")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("net.kyori:adventure-api:4.17.0")
    testImplementation("net.kyori:adventure-text-minimessage:4.17.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("NexGate-Common")
}
