package bas.app.shift.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCommandManagerTest {

    @Test
    fun getAvailableCommands_noModules_excludesModuleGatedCommands() {
        val commands = TerminalCommandManager.getAvailableCommands(emptyList())
        assertTrue(commands.none { it.requiredModuleId != null })
        assertTrue(commands.any { it.name == "CAMERA.FIND" })
        assertTrue(commands.any { it.name == "HELP" })
    }

    @Test
    fun getAvailableCommands_withModule_includesGatedCommandsForThatModuleOnly() {
        val commands = TerminalCommandManager.getAvailableCommands(listOf(91))
        assertTrue(commands.any { it.name == "SHIFT.PROXY.DEPLOY" })
        assertTrue(commands.any { it.name == "SHIFT.PROXY.STATUS" })
        assertFalse(commands.any { it.name == "CROSS.LINK" }) // требует модуль 92, не 91
        assertFalse(commands.any { it.name == "HUMAN.UPLOAD" }) // требует модуль 93
    }

    @Test
    fun findCommand_exactNameMatch() {
        val command = TerminalCommandManager.findCommand("CAMERA.FIND")
        assertNotNull(command)
        assertEquals("CAMERA.FIND", command!!.name)
    }

    @Test
    fun findCommand_nameWithArguments_matchesByFirstToken() {
        val command = TerminalCommandManager.findCommand("CAMERA.FIND 123")
        assertNotNull(command)
        assertEquals("CAMERA.FIND", command!!.name)
    }

    @Test
    fun findCommand_isCaseInsensitive() {
        val command = TerminalCommandManager.findCommand("camera.find 123")
        assertNotNull(command)
        assertEquals("CAMERA.FIND", command!!.name)
    }

    @Test
    fun findCommand_similarButDifferentName_doesNotFalselyMatch() {
        // Регрессия: раньше startsWith по имени ошибочно матчил похожие вводы.
        assertNull(TerminalCommandManager.findCommand("CROSS.LINKAGE"))
        assertNull(TerminalCommandManager.findCommand("USER.REBOOT"))
    }

    @Test
    fun findCommand_moduleGated_notFoundWithoutModule() {
        assertNull(TerminalCommandManager.findCommand("SHIFT.PROXY.STATUS", emptyList()))
        assertNotNull(TerminalCommandManager.findCommand("SHIFT.PROXY.STATUS", listOf(91)))
    }

    @Test
    fun findCommand_unknownCommand_returnsNull() {
        assertNull(TerminalCommandManager.findCommand("NOT.A.REAL.COMMAND"))
    }

    @Test
    fun getHelpText_containsHeaderAndKnownCommand() {
        val helpText = TerminalCommandManager.getHelpText()
        assertTrue(helpText.contains("=== ДОСТУПНЫЕ КОМАНДЫ ==="))
        assertTrue(helpText.contains("CAMERA.FIND"))
        assertFalse(helpText.contains("SHIFT.PROXY.DEPLOY")) // без модуля 91 не должно попасть в справку
    }

    @Test
    fun getHelpText_withModule_includesGatedCommand() {
        val helpText = TerminalCommandManager.getHelpText(listOf(92))
        assertTrue(helpText.contains("CROSS.LINK"))
    }

    @Test
    fun shouldSkipMgNotification_proxyCommands_skipped() {
        assertTrue(TerminalCommandManager.shouldSkipMgNotification("SHIFT.PROXY.DEPLOY node1"))
        assertTrue(TerminalCommandManager.shouldSkipMgNotification("SHIFT.PROXY.STATUS"))
    }

    @Test
    fun shouldSkipMgNotification_userCommands_skippedExceptFormat() {
        assertTrue(TerminalCommandManager.shouldSkipMgNotification("USER.REBOOT.START"))
        assertTrue(TerminalCommandManager.shouldSkipMgNotification("USER.UPGRADE.END статья"))
        // USER.FORMAT — единственная команда группы USER, о которой всё же нужно уведомить MG.
        assertFalse(TerminalCommandManager.shouldSkipMgNotification("USER.FORMAT"))
    }

    @Test
    fun shouldSkipMgNotification_utilsAndSystemCommands_skipped() {
        assertTrue(TerminalCommandManager.shouldSkipMgNotification("UTILS.GLOBAL_NOIZE"))
        assertTrue(TerminalCommandManager.shouldSkipMgNotification("SYSTEM.WHATEVER"))
    }

    @Test
    fun shouldSkipMgNotification_regularCommands_notSkipped() {
        assertFalse(TerminalCommandManager.shouldSkipMgNotification("CAMERA.FIND дверь"))
        assertFalse(TerminalCommandManager.shouldSkipMgNotification("CROSS.LINK MG_Bas"))
        assertFalse(TerminalCommandManager.shouldSkipMgNotification("DEEP_DIVE.END 3"))
    }
}
