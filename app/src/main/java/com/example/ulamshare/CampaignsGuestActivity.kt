package com.example.ulamshare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class CampaignsGuestActivity : AppCompatActivity() {
    private var feedController: ChooseCampaignFeedController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rootView = layoutInflater.inflate(R.layout.activity_choose_campaign, null, false)
        setContentView(rootView)

        feedController = ChooseCampaignFeedController(
            rootView = rootView,
            lifecycleOwner = this,
            activityResultRegistry = activityResultRegistry,
            onBackPressed = { finish() },
            launchIntent = { startActivity(it) }
        )
        feedController?.bind()
    }

    override fun onDestroy() {
        feedController?.dispose()
        feedController = null
        super.onDestroy()
    }
}
