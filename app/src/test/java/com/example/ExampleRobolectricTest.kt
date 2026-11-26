package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.YouTubeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("Background YT", appName)
  }

  @Test
  fun `test youtube url parsing`() {
    val id1 = YouTubeCatalog.extractVideoId("https://www.youtube.com/watch?v=w7k1WqE8jY4")
    assertEquals("w7k1WqE8jY4", id1)

    val id2 = YouTubeCatalog.extractVideoId("https://youtu.be/jfKfPfyJRdk")
    assertEquals("jfKfPfyJRdk", id2)

    val id3 = YouTubeCatalog.extractVideoId("kJQP7kiw5Fk")
    assertEquals("kJQP7kiw5Fk", id3)
  }
}

