plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":utils"))
}

val trainerRuntimeSourceAuditProperties = listOf(
    "uprfvx.trainerRuntimeSourceBaseRom",
    "uprfvx.trainerRuntimeSourceRandomizedRom",
)

fun Test.forwardTrainerRuntimeSourceAuditProperties() {
    trainerRuntimeSourceAuditProperties.forEach { propertyName ->
        System.getProperty(propertyName)?.let { propertyValue ->
            systemProperty(propertyName, propertyValue)
        }
    }
}

tasks.named<Test>("test") {
    forwardTrainerRuntimeSourceAuditProperties()

    filter {
        excludeTestsMatching("*RomHandler*Test")
    }
}

tasks.register<Test>("testROMs") {
    description = "Runs tests dependent on loading ROM files."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("romsPath", rootProject.file("roms").absolutePath)
    forwardTrainerRuntimeSourceAuditProperties()

    shouldRunAfter("test")

    useJUnitPlatform()
    ignoreFailures = true
    maxHeapSize = "4G"

    filter {
        includeTestsMatching("*RomHandler*Test")
    }
}
