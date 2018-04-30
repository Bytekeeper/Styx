package org.fttbot

fun main(args: Array<String>) {
    // Kill previous Starcraft.exe process
//    ProcessHelper.killStarcraftProcess()

    // Kill previous Chaoslauncher.exe process
//    ProcessHelper.killChaosLauncherProcess()

    // Autostart Chaoslauncher
    // Combined with Chaoslauncher -> Settings -> Run Starcraft on Startup
    // SC will be autostarted at this moment
    ProcessHelper.startChaosLauncherProcess()

    FTTBot.start(args.contains("debug"))
}