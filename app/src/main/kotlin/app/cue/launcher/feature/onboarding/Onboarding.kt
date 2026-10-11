package app.cue.launcher.feature.onboarding

import app.cue.launcher.data.settings.LauncherSettings

fun onboardingVisible(settings: LauncherSettings?): Boolean = settings?.onboardingSeen == false
