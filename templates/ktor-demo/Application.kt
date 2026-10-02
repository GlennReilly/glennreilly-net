// Reference only: the bits a Ktor demo needs to run behind Caddy in a container.
// Merge into your existing Application.kt rather than copying wholesale.

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.routing.*

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    // Bind 0.0.0.0, not localhost, or Caddy can't reach the container.
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Caddy terminates TLS; trust its X-Forwarded-* headers so request.origin reports https.
    install(XForwardedHeaders)

    routing {
        // Serve the bundled Kotlin/JS frontend from resources/static.
        staticResources("/", "static")
        // ...your API routes
    }
}
