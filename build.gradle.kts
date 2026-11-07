// Security floors for BUILD TOOLING only (AGP/lint/KSP/Hilt/ktlint internals and the plugin
// classpath). None of these ship in the APK; they are raised here because the upstream
// tools still pin vulnerable versions (reported by Dependabot / the nightly Trivy
// build-tooling scan). A dependency is only ever moved UP to the floor, never down.
// Remove an entry once the tool that pulls it in ships a fixed version.
// The buildscript block is evaluated in isolation, so the same list lives in
// gradle/tooling-security-floors.gradle.kts for project configurations.
buildscript {
    val floors =
        mapOf(
            "org.bouncycastle:bcprov-jdk18on" to "1.85",
            "org.bouncycastle:bcpkix-jdk18on" to "1.85",
            "org.bouncycastle:bcutil-jdk18on" to "1.85",
            "org.jdom:jdom2" to "2.0.6.1",
            "org.bitbucket.b_c:jose4j" to "0.9.6",
            "com.google.guava:guava" to "33.7.2",
        )

    fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    fun isLower(
        a: String,
        b: String,
    ): Boolean {
        val (x, y) = parts(a) to parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d < 0
        }
        return false
    }
    configurations.classpath {
        resolutionStrategy.eachDependency {
            val floor = floors["${requested.group}:${requested.name}"] ?: return@eachDependency
            val current = requested.version ?: return@eachDependency
            if (isLower(current, floor)) {
                val suffix = if (requested.name == "guava") "-" + (current.substringAfter('-', "jre")) else ""
                useVersion(floor + suffix)
                because("Build-tooling CVE floor (see root build.gradle.kts)")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(from = rootProject.file("gradle/tooling-security-floors.gradle.kts"))
}
