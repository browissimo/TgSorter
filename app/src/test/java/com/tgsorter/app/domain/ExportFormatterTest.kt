package com.tgsorter.app.domain

import com.tgsorter.app.domain.export.ExportFormatter
import com.tgsorter.app.domain.export.ExportKind
import com.tgsorter.app.domain.model.Channel
import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.domain.parser.ChannelListParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFormatterTest {

    private val channels = listOf(
        Channel(1, 1, "the_male_nude", 1, ChannelStatus.POSITIVE, 1L),
        Channel(2, 1, "the_pashtet", 2, ChannelStatus.NEGATIVE, 2L),
        Channel(3, 1, "the_plumule", 3, ChannelStatus.POSITIVE, 3L),
    )

    @Test
    fun `txt export is one @username per line`() {
        val positive = channels.filter { it.status == ChannelStatus.POSITIVE }
        assertEquals("@the_male_nude\n@the_plumule\n", ExportFormatter.format(ExportKind.POSITIVE, positive))
        assertEquals("", ExportFormatter.usernames(emptyList()))
    }

    @Test
    fun `csv export matches the spec`() {
        assertEquals(
            "username,status,position\n" +
                "the_male_nude,POSITIVE,1\n" +
                "the_pashtet,NEGATIVE,2\n" +
                "the_plumule,POSITIVE,3\n",
            ExportFormatter.csv(channels),
        )
    }

    @Test
    fun `csv export can be imported back`() {
        val restored = ChannelListParser.parse(ExportFormatter.csv(channels)).entries
        assertEquals(channels.map { it.username to it.status }, restored.map { it.username to it.status })
    }

    @Test
    fun `txt export can be imported back`() {
        val restored = ChannelListParser.parse(ExportFormatter.usernames(channels)).entries
        assertEquals(channels.map { it.username }, restored.map { it.username })
    }
}
