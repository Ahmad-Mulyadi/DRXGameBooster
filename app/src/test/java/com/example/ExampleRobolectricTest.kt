package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DRX GAME TURBO PRO", appName)
  }

  @Test
  fun `application context has expected package and name`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    assertEquals("com.derrex.gameturbo", app.packageName)
    assertEquals("DRX GAME TURBO PRO", app.getString(R.string.app_name))
  }
}
