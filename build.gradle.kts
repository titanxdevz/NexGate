plugins {
    java
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    group = "com.nexgate"
    version = "1.0.0"
    
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://nexus.velocitypowered.com/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

subprojects {
    apply(plugin = "java")
    
    
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    
    tasks.withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    
    tasks.withType<Copy> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

tasks.register("deployServer") {
    dependsOn(":nexgate-velocity:shadowJar", ":nexgate-paper:shadowJar")
    doLast {
        val velocityJar = file("nexgate-velocity/build/libs/NexGate-Velocity-1.0.0.jar")
        val paperJar = file("nexgate-paper/build/libs/NexGate-Paper-1.0.0.jar")

        val targetVelocity = file("minecraft-server/velocity/plugins/NexGate-Velocity-1.0.0.jar")
        val targetPaperAuth = file("minecraft-server/paper-auth/plugins/NexGate-Paper-1.0.0.jar")
        val targetPaperLobby = file("minecraft-server/paper-lobby/plugins/NexGate-Paper-1.0.0.jar")

        // Clear Paper remapped cache to prevent ZipException
        file("minecraft-server/paper-auth/plugins/.paper-remapped").deleteRecursively()
        file("minecraft-server/paper-lobby/plugins/.paper-remapped").deleteRecursively()

        velocityJar.copyTo(targetVelocity, overwrite = true)
        paperJar.copyTo(targetPaperAuth, overwrite = true)
        paperJar.copyTo(targetPaperLobby, overwrite = true)

        println("✓ Successfully built and deployed NexGate jars to minecraft-server:")
        println("  • " + targetVelocity.path)
        println("  • " + targetPaperAuth.path)
        println("  • " + targetPaperLobby.path)
    }
}

