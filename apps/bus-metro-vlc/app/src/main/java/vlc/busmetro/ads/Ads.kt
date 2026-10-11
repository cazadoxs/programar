package vlc.busmetro.ads

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import vlc.busmetro.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ads are shown only after the EU consent flow (Google UMP) allows it, and
 * never to Pro users. One discreet adaptive banner at the bottom, nothing else.
 */
object Ads {
    private val initialized = AtomicBoolean(false)

    fun gather(activity: Activity, onReady: (canShow: Boolean) -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                if (info.canRequestAds()) init(activity)
                onReady(info.canRequestAds())
            }
        }, {
            if (info.canRequestAds()) init(activity)
            onReady(info.canRequestAds())
        })
        if (info.canRequestAds()) init(activity)
    }

    fun privacyOptionsRequired(activity: Activity) =
        UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) {}

    private fun init(activity: Activity) {
        if (!initialized.compareAndSet(false, true)) return
        MobileAds.setRequestConfiguration(RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build())
        MobileAds.initialize(activity.applicationContext)
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier, widthDp: Int) {
    val context = LocalContext.current
    val view = remember(widthDp) {
        AdView(context).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adUnitId = BuildConfig.BANNER_UNIT_ID
            adListener = object : AdListener() { override fun onAdFailedToLoad(e: LoadAdError) {} }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(view) { onDispose { view.destroy() } }
    AndroidView(factory = { view }, modifier = modifier.fillMaxWidth())
}
