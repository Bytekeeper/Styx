package org.fttbot.start

/**
 * Kills and starts processes, including Starcraft game itself and Chaoslauncher.
 */
object ProcessHelper {

    fun killStarcraftProcess() {
        executeInCommandLine("taskkill /f /im Starcraft.exe")
        executeInCommandLine("taskkill /f /im StarCraft.exe")
    }

    fun killChaosLauncherProcess() {
        executeInCommandLine("taskkill /f /im Chaoslauncher.exe")
    }

    /**
     * Autostart Chaoslauncher
     * Combined with Chaoslauncher -> Settings -> Run Starcraft on Startup
     * SC will be autostarted at this moment
     */
    fun startChaosLauncherProcess() {
        try {
            Thread.sleep(250)
            executeInCommandLine("c:\\Users\\dante\\BWAPI\\Chaoslauncher\\Chaoslauncher.exe")
//            executeInCommandLine("C:\\Program Files (x86)\\BWAPI\\Chaoslauncher\\Chaoslauncher.exe")
        } catch (ex: InterruptedException) {
            // Don't do anything
        }

    }

    // =========================================================

    private fun executeInCommandLine(command: String) {
        try {
            Runtime.getRuntime().exec(command)
        } catch (err: Exception) {
            err.printStackTrace()
        }

    }

}
