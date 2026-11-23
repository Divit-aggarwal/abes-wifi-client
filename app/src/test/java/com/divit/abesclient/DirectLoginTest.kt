package com.divit.abesclient

import com.divit.abesclient.DirectLogin.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectLoginTest {
    private fun reply(status: String, message: String) =
        "<?xml version='1.0' ?><requestresponse><status><![CDATA[$status]]></status>" +
            "<message><![CDATA[$message]]></message><logoutmessage><![CDATA[You have successfully logged off]]></logoutmessage></requestresponse>"

    @Test fun parsesSophosReplies() {
        assertEquals(Outcome.SIGNED_IN, DirectLogin.parse(reply("LIVE", "You are signed in as {username}")).outcome)
        assertEquals(Outcome.WRONG_PASSWORD, DirectLogin.parse(reply("LOGIN", "Login failed. Invalid user name/password. Please contact the administrator.")).outcome)
        val limit = DirectLogin.parse(reply("LOGIN", "You have reached the maximum login limit."))
        assertEquals(Outcome.FAILED, limit.outcome)
        assertEquals("You have reached the maximum login limit.", limit.message)
        assertEquals(Outcome.FAILED, DirectLogin.parse("<html>not xml</html>").outcome)
    }
}
