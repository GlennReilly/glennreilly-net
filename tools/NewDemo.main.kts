#!/usr/bin/env kotlin

// Scaffolds a new demo in this repo: Caddyfile block, compose service (for container
// demos), a sites/<slug> folder (for static demos) and a demos.json entry.
//
//   kotlin tools/NewDemo.main.kts <slug> <static|container> "<Title>" "<Description>" [tech,tech] [port]
//
// Examples:
//   kotlin tools/NewDemo.main.kts tide-clock static "Tide Clock" "Tides as a clock face" "HTML,SVG"
//   kotlin tools/NewDemo.main.kts year-round container "Year Round" "Radial climate chart" "Kotlin,Ktor" 8080

import java.io.File

sealed interface Kind {
    data object Static : Kind
    data class Container(val port: Int) : Kind
}

data class Demo(val slug: String, val kind: Kind, val title: String, val description: String, val tech: List<String>)

val domain = "glennreilly.net"
val owner = "glennreilly"
val root = File(".").canonicalFile.let { if (File(it, "Caddyfile").exists()) it else it.parentFile }

fun fail(msg: String): Nothing { System.err.println(msg); kotlin.system.exitProcess(1) }

fun parse(args: Array<String>): Demo {
    if (args.size < 4) fail("usage: <slug> <static|container> <title> <description> [tech,tech] [port]")
    val slug = args[0].also { require(Regex("^[a-z0-9][a-z0-9-]*$").matches(it)) { "slug must be lowercase letters, digits and dashes" } }
    val kind = when (args[1]) {
        "static" -> Kind.Static
        "container" -> Kind.Container(args.getOrNull(5)?.toInt() ?: 8080)
        else -> fail("kind must be 'static' or 'container'")
    }
    val tech = args.getOrNull(4)?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
    return Demo(slug, kind, args[2], args[3], tech)
}

/** Inserts [block] immediately before the line containing [marker]. */
fun File.insertBefore(marker: String, block: String) {
    val text = readText()
    val idx = text.indexOf(marker).takeIf { it >= 0 } ?: fail("marker '$marker' not found in $name")
    val lineStart = text.lastIndexOf('\n', idx) + 1
    writeText(text.substring(0, lineStart) + block + "\n" + text.substring(lineStart))
}

fun caddyBlock(d: Demo) = buildString {
    appendLine("${d.slug}.$domain {")
    appendLine("\timport common")
    when (val k = d.kind) {
        Kind.Static -> { appendLine("\troot * /srv/${d.slug}"); appendLine("\tfile_server") }
        is Kind.Container -> appendLine("\treverse_proxy ${d.slug}:${k.port}")
    }
    append("}")
}

fun composeBlock(d: Demo, port: Int) = """
  |  ${d.slug}:
  |    image: ghcr.io/$owner/${d.slug}:latest
  |    restart: unless-stopped
  |    environment:
  |      PORT: "$port"
  |    mem_limit: 384m
  |    networks: [web]
""".trimMargin()

fun String.json() = buildString {
    append('"')
    for (c in this@json) when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
    }
    append('"')
}

/** Appends an entry to sites/root/demos.json (kept dependency-free: no JSON library needed). */
fun addToIndex(d: Demo) {
    val file = File(root, "sites/root/demos.json")
    val text = file.readText().trimEnd()
    if (Regex("\"slug\"\\s*:\\s*${Regex.escape(d.slug.json())}").containsMatchIn(text)) fail("${d.slug} already in demos.json")
    val status = if (d.kind is Kind.Container) "soon" else "live"
    val entry = """
        |  {
        |    "slug": ${d.slug.json()},
        |    "title": ${d.title.json()},
        |    "description": ${d.description.json()},
        |    "tech": [${d.tech.joinToString(", ") { it.json() }}],
        |    "status": ${status.json()}
        |  }
    """.trimMargin()
    val body = text.removeSuffix("]").trimEnd()
    val sep = if (body.endsWith("[")) "\n" else ",\n"
    file.writeText(body + sep + entry + "\n]\n")
}

val demo = parse(args)
if (File(root, "Caddyfile").readText().contains("${demo.slug}.$domain")) fail("${demo.slug} already exists in Caddyfile")
File(root, "Caddyfile").insertBefore(">>> new demos", caddyBlock(demo) + "\n")
when (val k = demo.kind) {
    Kind.Static -> File(root, "sites/${demo.slug}").apply { mkdirs() }.resolve("index.html").takeUnless { it.exists() }
        ?.writeText("<!doctype html>\n<meta charset=\"utf-8\">\n<title>${demo.title}</title>\n<h1>${demo.title}</h1>\n")
    is Kind.Container -> File(root, "compose.yaml").insertBefore(">>> new demo services", composeBlock(demo, k.port) + "\n")
}
addToIndex(demo)

println("Added ${demo.slug}.$domain (${demo.kind::class.simpleName}).")
if (demo.kind is Kind.Container) {
    println("Next: add templates/ktor-demo (or static-build-demo) Dockerfile + publish.yml to the demo repo,")
    println("push once so ghcr.io/$owner/${demo.slug} exists, then set its status to \"live\" in demos.json and push this repo.")
} else {
    println("Next: put your files in sites/${demo.slug}/ and push this repo.")
}
