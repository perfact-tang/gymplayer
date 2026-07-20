package com.vibecodingjapan.gymplayer.ui

import com.vibecodingjapan.gymplayer.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class UiLocalizationTest {
  @Test
  fun translatesCoreNavigationAndTtsText() {
    assertEquals("Settings", localizeUiText("設定", AppLanguage.ENGLISH))
    assertEquals("设置", localizeUiText("設定", AppLanguage.CHINESE))
    assertEquals("휴식을 시작합니다", localizeUiText("休憩に入ります", AppLanguage.KOREAN))
  }

  @Test
  fun JapaneseAndUserContentArePreserved() {
    assertEquals("設定", localizeUiText("設定", AppLanguage.JAPANESE))
    assertEquals("My Track", localizeUiText("My Track", AppLanguage.CHINESE))
  }
}
