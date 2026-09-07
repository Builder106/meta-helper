package com.metahelper.ios

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeUIViewController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metahelper.shared.GlassesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSBundle
import platform.Foundation.NSObject
import platform.Foundation.NSString
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegate
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIColor
import platform.UIKit.UIWindow

@UIApplicationMain
@OptIn(kotlin.RequiresOptIn::class)
class AppDelegate : UIApplicationDelegate {
    private var glassesManager: GlassesManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var window: UIWindow? = null

    private fun configuredBackendUrl(): String {
        val configuredUrl = NSBundle.mainBundle.objectForInfoDictionaryKey(
            "MetaHelperBackendURL"
        ) as? String
        return configuredUrl?.takeIf { it.isNotBlank() && !it.startsWith("$(") }
            ?: "http://localhost:8080"
    }

    override fun application(
        application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: platform.Foundation.NSDictionary<*, *>?
    ): Boolean {
        println("iOS App: Starting MetaHelper...")

        // Initialize shared GlassesManager (gallery polling + backend + audio)
        glassesManager = GlassesManager(
            backendUrl = configuredBackendUrl(),
            context = Unit
        )

        println("iOS App: MetaHelper initialized with gallery polling")

        // Create the main window with Compose UI
        window = UIWindow(platform.Foundation.UIScreen.mainScreen().bounds)
        val composeVC = ComposeUIViewController {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.ui.graphics.Color.White
            ) {
                MainScreen(glassesManager!!)
            }
        }
        window?.rootViewController = composeVC
        window?.makeKeyAndVisible()

        return true
    }
}
