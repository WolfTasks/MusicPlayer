// Security floors for build-tooling configurations of a project (androidLintTool, ktlint,
// KSP / Hilt processor classpaths, ...). See the comment in the root build.gradle.kts.
// The shipped app's Guava floor stays in app/build.gradle.kts (dependency constraint).
val floors =
    mapOf(
        "org.bouncycastle:bcprov-jdk18on" to "1.85",
        "org.bouncycastle:bcpkix-jdk18on" to "1.85",
        "org.bouncycastle:bcutil-jdk18on" to "1.85",
        "org.jdom:jdom2" to "2.0.6.1",
        "org.bitbucket.b_c:jose4j" to "0.9.6",
        "com.google.guava:guava" to "33.7.2",
        "org.apache.commons:commons-lang3" to "3.18.0",
        "org.apache.httpcomponents:httpclient" to "4.5.14",
        "ch.qos.logback:logback-classic" to "1.5.34",
        "ch.qos.logback:logback-core" to "1.5.34",
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

configurations.configureEach {
    resolutionStrategy.eachDependency {
        val floor = floors["${requested.group}:${requested.name}"] ?: return@eachDependency
        val current = requested.version ?: return@eachDependency
        if (current.isNotEmpty() && isLower(current, floor)) {
            val suffix = if (requested.name == "guava") "-" + current.substringAfter('-', "jre") else ""
            useVersion(floor + suffix)
            because("Build-tooling CVE floor (see root build.gradle.kts)")
        }
    }
}
