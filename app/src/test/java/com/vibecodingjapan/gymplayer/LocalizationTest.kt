package com.vibecodingjapan.gymplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationTest {
  @Test
  fun languageCodesRoundTripAndInvalidDefaultsToJapanese() {
    AppLanguage.entries.forEach { language -> assertEquals(language, AppLanguage.fromCode(language.code)) }
    assertEquals(AppLanguage.JAPANESE, AppLanguage.fromCode("invalid"))
    assertEquals(AppLanguage.JAPANESE, AppLanguage.fromCode(null))
  }

  @Test
  fun machineTranslationFallsBackToJapanese() {
    val machine = Machine(id = "m1", number = "1", name = "チェストプレス", bodyPart = "胸", icon = "🏋", nameZh = "坐姿推胸")
    assertEquals("坐姿推胸", machine.localizedName(AppLanguage.CHINESE))
    assertEquals("チェストプレス", machine.localizedName(AppLanguage.ENGLISH))
    assertEquals("胸", machine.localizedBodyPart(AppLanguage.KOREAN))
  }

  @Test
  fun languageSyncUploadsLocalChangesAndRestoresValidCloudState() {
    assertEquals(true, shouldUploadLanguage(localDirty = true, remoteExists = true, remoteCode = "en"))
    assertEquals(true, shouldUploadLanguage(localDirty = false, remoteExists = false, remoteCode = null))
    assertEquals(true, shouldUploadLanguage(localDirty = false, remoteExists = true, remoteCode = "invalid"))
    assertEquals(false, shouldUploadLanguage(localDirty = false, remoteExists = true, remoteCode = "ko"))
  }
}
