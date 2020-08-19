package util

import de.ka.rgreed.dao.model.*
import de.ka.rgreed.util.ConsensusTasksManager
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * This object provides several initializations for all databases.
 */
object DatabaseUtil {

    /**
     * Create the database tables if they don't exist.
     */
    fun initialize() {
        transaction {
            if (!UsersTable.exists()) {
                SchemaUtils.create(
                    UsersTable,
                    ConsensusTable,
                    SuggestionsTable,
                    ConsensusAdminTable,
                    ConsensusAccessorsTable,
                    ConsensusFollowersTable,
                    SuggestionsVoteTable
                )
            }
        }
    }

    /**
     * Wipes all tables and resets all corresponding modules. Generates a default set of data for debug usage
     */
    fun initializeTest() {
        transaction {

            ConsensusTasksManager.clearTasks() // clears the registry of consensus tasks

            SchemaUtils.drop(
                SuggestionsVoteTable,
                SuggestionsTable,
                ConsensusAdminTable,
                ConsensusAccessorsTable,
                ConsensusFollowersTable,
                ConsensusTable,
                UsersTable
            )
        }

        initialize()
    }
}

