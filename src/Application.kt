package de.ka.rgreed

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import util.DatabaseUtil
import de.ka.rgreed.dao.ConsensusRepository
import de.ka.rgreed.dao.UsersRepository
import de.ka.rgreed.model.ApiError
import de.ka.rgreed.model.ApiErrorResponse
import de.ka.rgreed.model.ConsensusException
import de.ka.rgreed.util.FileUtil
import de.ka.rgreed.util.JwtAuth
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.jackson.jackson
import io.ktor.routing.*
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.jwt
import io.ktor.response.respond
import org.jetbrains.exposed.sql.Database
import route.v1.routesV1

/**
 * Entry point of the ktor server. This function starts the jetty webserver with the given arguments.
 * The arguments are defined resources/application.conf file.
 */
fun main(args: Array<String>) {
    embeddedServer(Jetty, commandLineEnvironment(args)) {}.start(wait = true)
}

/**
 * Entry point of the application. This function is referenced in
 * the resources/application.conf file inside the ktor.application.modules.
 */
fun Application.module() {

    initDatabase(getBuildType(environment))
    initFirebase()

    install(StatusPages) {
        exception<ConsensusException> { exception ->
            call.respond(
                exception.httpCode,
                ApiErrorResponse(exception.errors.map {
                    ApiError(
                        it.errorType.code,
                        it.errorType.shortDescription,
                        it.parameter
                    )
                })
            )
        }
    }
    install(Compression)
    install(DefaultHeaders)
    install(CallLogging)
    install(XForwardedHeaderSupport)
    install(ContentNegotiation) {
        jackson {
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(Authentication) {
        jwt {
            verifier(JwtAuth.verifier)
            realm = "ktor.io"
            validate {
                it.payload.getClaim("userName").asString().let(UsersRepository::findUserByName)
            }
        }
    }

    routing {
        route("/v1") {
            routesV1()
        }
    }

    // if the server has previously somehow crashed, we have to restart all unfinished consensus tasks:
    ConsensusRepository.registerAllUnfinishedConsensusTasks()
}

/**
 * Init the Database. Depending on the build type, there are different possible connection drivers.
 */
private fun initDatabase(buildType: BuildType) {
    when (buildType) {
        BuildType.TEST -> {
            // for local tests
            Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
            DatabaseUtil.initializeTest()
        }
        BuildType.DEV -> {
            // for remote dev testing
            Database.connect(HikariDataSource(HikariConfig("/devHikari.properties")))
            DatabaseUtil.initialize()
        }
        BuildType.PROD -> {
            // for remote dev testing
            Database.connect(HikariDataSource(HikariConfig("/prodHikari.properties")))
            DatabaseUtil.initialize()
        }
    }
}

private fun initFirebase() {
    val options = FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(FileUtil.loadFirebaseConfigurationFile()))
        .build()

    if (FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.initializeApp(options)
    }
}

/**
 * Retrieves the build type from the "ktor.deployment.environment" property in the application.conf file.
 * This values is overwritten by the deployment. Else it defaults to 'dev'. If used in tests, this
 * property is overwritten with 'test'.
 */
private fun getBuildType(environment: ApplicationEnvironment): BuildType {

    val envKind = environment.config.property("ktor.deployment.environment").getString().toUpperCase()

    print("environment: $envKind")

    return when (envKind) {
        "DEV" -> return BuildType.DEV
        "PROD" -> return BuildType.PROD
        else -> BuildType.TEST
    }
}

/**
 * Lists all possible build types.
 */
enum class BuildType {
    TEST, DEV, PROD
}

