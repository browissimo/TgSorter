package com.tgsorter.app.navigation

import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.domain.model.ReviewMode

/** Маршруты навигации и их аргументы. */
object Routes {
    const val ARG_LIST_ID = "listId"
    const val ARG_MODE = "mode"
    const val ARG_POSITION = "position"
    const val ARG_STATUS = "status"

    const val HOME = "home"
    const val REVIEW = "review/{$ARG_LIST_ID}?$ARG_MODE={$ARG_MODE}&$ARG_POSITION={$ARG_POSITION}"
    const val RESULTS = "results/{$ARG_LIST_ID}"
    const val CHANNELS = "channels/{$ARG_LIST_ID}/{$ARG_STATUS}"

    fun review(listId: Long, mode: ReviewMode = ReviewMode.PENDING, position: Int = 0): String =
        "review/$listId?$ARG_MODE=${mode.name}&$ARG_POSITION=$position"

    fun results(listId: Long): String = "results/$listId"

    fun channels(listId: Long, status: ChannelStatus): String = "channels/$listId/${status.dbValue}"
}
