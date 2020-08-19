package de.ka.rgreed.util

import java.util.concurrent.TimeUnit.*

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import de.ka.rgreed.dao.ConsensusRepository
import de.ka.rgreed.dao.model.ConsensusDao

/**
 *
 */
object ConsensusTasksManager {

    val finishTaskMap = mutableMapOf<Int, ScheduledFuture<*>>()
    val voteTaskMap = mutableMapOf<Int, ScheduledFuture<*>>()
    private val executor = Executors.newScheduledThreadPool(1)

    fun registerFinishTask(consensus: ConsensusDao) {
        println("${javaClass.name}: Registering finish task for id: ${consensus.id.value}")

        val consensusId = consensus.id.value

        unregisterFinishTask(consensusId)

        finishTaskMap[consensus.id.value] =
            executor.schedule(
                { ConsensusRepository.consensusFinishTask(consensus) },
                calculateDelay(consensus.endDate.millis),
                MILLISECONDS
            )

    }

    fun registerVoteStartTask(consensus: ConsensusDao) {
        if (consensus.votingStartDate.isBeforeNow) {
            return
        }

        println("${javaClass.name}: Registering vote task for id: ${consensus.id.value}")

        val consensusId = consensus.id.value

        unregisterVoteTask(consensusId)

        voteTaskMap[consensus.id.value] =
            executor.schedule(
                { ConsensusRepository.consensusVotingStarDateReachedTask(consensus) },
                calculateDelay(consensus.votingStartDate.millis),
                MILLISECONDS
            )

    }

    fun unregisterFinishTask(consensusId: Int, interruptIfRunning: Boolean = true) {
        println("${javaClass.name}: Un-Registering finish task for id: $consensusId")

        finishTaskMap[consensusId]?.cancel(interruptIfRunning)
        finishTaskMap.remove(consensusId)
    }

    fun unregisterVoteTask(consensusId: Int, interruptIfRunning: Boolean = true) {
        println("${javaClass.name}: Un-Registering vote task for id: $consensusId")

        voteTaskMap[consensusId]?.cancel(interruptIfRunning)
        voteTaskMap.remove(consensusId)
    }

    fun clearTasks() {
        finishTaskMap.values.forEach { it.cancel(true) }
        finishTaskMap.clear()
        voteTaskMap.values.forEach { it.cancel(true) }
        voteTaskMap.clear()
    }

    private fun calculateDelay(endDate: Long): Long {
        return (endDate - System.currentTimeMillis())
    }
}