package com.tgsorter.app.domain

import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.domain.parser.ChannelListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelListParserTest {

    private fun usernames(text: String) = ChannelListParser.parse(text).entries.map { it.username }

    @Test
    fun `example from the spec`() {
        val text = "@channel1\nhttps://t.me/channel2\nt.me/channel3\n@channel1\n"
        val result = ChannelListParser.parse(text)
        assertEquals(listOf("channel1", "channel2", "channel3"), result.entries.map { it.username })
        assertEquals(1, result.duplicates)
        assertEquals(0, result.invalidLines)
    }

    @Test
    fun `keeps original order and skips blank lines`() {
        val text = "\n\n@the_male_nude\r\n\r\n@the_pashtet\n   \n@the_plumule\n@the_sanyaba_01\n@the_second_amendment\n"
        assertEquals(
            listOf("the_male_nude", "the_pashtet", "the_plumule", "the_sanyaba_01", "the_second_amendment"),
            usernames(text),
        )
    }

    @Test
    fun `supports many link formats`() {
        val text = """
            https://t.me/alpha_one
            http://t.me/beta_two/
            www.t.me/gamma_three
            https://telegram.me/delta_four
            telegram.dog/epsilon_5
            https://t.me/s/zeta_six
            https://t.me/eta_seven/1234
            https://t.me/theta_eight?start=abc
            tg://resolve?domain=iota_nine&post=5
            kappa_ten.t.me
            plain_name
              @  spaced_name
            «@quoted_name»
        """.trimIndent()
        assertEquals(
            listOf(
                "alpha_one", "beta_two", "gamma_three", "delta_four", "epsilon_5", "zeta_six",
                "eta_seven", "theta_eight", "iota_nine", "kappa_ten", "plain_name", "spaced_name", "quoted_name",
            ),
            usernames(text),
        )
    }

    @Test
    fun `duplicates are case insensitive`() {
        val result = ChannelListParser.parse("@Durov_Channel\n@durov_channel\nt.me/DUROV_CHANNEL")
        assertEquals(listOf("Durov_Channel"), result.entries.map { it.username })
        assertEquals(2, result.duplicates)
    }

    @Test
    fun `several channels in one line`() {
        assertEquals(
            listOf("first_one", "second_two", "third_three"),
            usernames("@first_one, @second_two; https://t.me/third_three"),
        )
    }

    @Test
    fun `invalid lines are counted, not imported`() {
        val result = ChannelListParser.parse(
            "Канал про котов\nhttps://t.me/+AbCdEf123\nhttps://t.me/joinchat/xyz\n@ab\n# comment\n@good_name",
        )
        assertEquals(listOf("good_name"), result.entries.map { it.username })
        assertEquals(4, result.invalidLines)
        assertTrue(result.invalidExamples.isNotEmpty())
    }

    @Test
    fun `plain text words are not treated as channels`() {
        assertTrue(usernames("Some channel name here").isEmpty())
    }

    @Test
    fun `empty input gives no entries`() {
        assertTrue(usernames("").isEmpty())
        assertTrue(usernames("   \n\n  ").isEmpty())
    }

    @Test
    fun `csv backup restores statuses`() {
        val text = "username,status,position\nthe_male_nude,POSITIVE,1\nthe_pashtet,NEGATIVE,2\nthe_plumule,SKIPPED,3\nfresh_one,PENDING,4\n"
        val result = ChannelListParser.parse(text)
        assertTrue(result.isCsv)
        assertEquals(
            listOf(
                "the_male_nude" to ChannelStatus.POSITIVE,
                "the_pashtet" to ChannelStatus.NEGATIVE,
                "the_plumule" to ChannelStatus.SKIPPED,
                "fresh_one" to ChannelStatus.PENDING,
            ),
            result.entries.map { it.username to it.status },
        )
        assertEquals(3, result.restoredStatuses)
    }

    @Test
    fun `extract username edge cases`() {
        assertEquals("channel_x", ChannelListParser.extractUsername("https://T.ME/channel_x/"))
        assertNull(ChannelListParser.extractUsername("https://example.com/channel_x"))
        assertNull(ChannelListParser.extractUsername("https://t.me/"))
        assertNull(ChannelListParser.extractUsername("1channel"))
        assertNull(ChannelListParser.extractUsername("bare_word", allowBare = false))
        assertEquals("bare_word", ChannelListParser.extractUsername("@bare_word", allowBare = false))
    }
}
