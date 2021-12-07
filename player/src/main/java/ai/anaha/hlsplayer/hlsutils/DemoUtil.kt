package ai.anaha.hlsplayer.hlsutils

import android.content.Context
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.DefaultRenderersFactory.ExtensionRendererMode
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * Utility methods for the demo app.
 */
object DemoUtil {
    /**
     * Whether the demo application uses Cronet for networking. Note that Cronet does not provide
     * automatic support for cookies (https://github.com/google/ExoPlayer/issues/5975).
     *
     *
     * If set to false, the platform's default network stack is used with a [CookieManager]
     * configured in [.getHttpDataSourceFactory].
     */
    private var dataSourceFactory: DataSource.Factory? = null

    @get:Synchronized
    var httpDataSourceFactory: HttpDataSource.Factory? = null
        get() {
            if (field == null) {
                val cookieManager = CookieManager()
                cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
                CookieHandler.setDefault(cookieManager)
                field = DefaultHttpDataSource.Factory()
            }
            return field
        }
        private set

    /**
     * Returns whether extension renderers should be used.
     */
    fun useExtensionRenderers(): Boolean {
        return false
    }

    @JvmStatic
    fun buildRenderersFactory(
        context: Context, preferExtensionRenderer: Boolean
    ): RenderersFactory {
        @ExtensionRendererMode val extensionRendererMode =
            if (useExtensionRenderers()) if (preferExtensionRenderer) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        return DefaultRenderersFactory(context.applicationContext)
            .setExtensionRendererMode(extensionRendererMode)
    }

    /**
     * Returns a [DataSource.Factory].
     */
    @JvmStatic
    @Synchronized
    fun getDataSourceFactory(context: Context): DataSource.Factory {
        if (dataSourceFactory == null) {
            dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory!!)
        }
        return dataSourceFactory!!
    }
}