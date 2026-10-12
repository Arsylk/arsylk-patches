group = "dev.arsylk.fosspatch"

patches {
    about {
        name = "Arsylk Patches"
        description = "Android modding should always be free, this is a repo that tries to bring that idea back instead of being another $10/month subscription, enjoy ~"
        source = "https://github.com/Arsylk/arsylk-patches"
        author = "Arsylk"
        contact = "https://github.com/Arsylk/arsylk-patches/issues"
        website = "https://github.com/Arsylk/arsylk-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
