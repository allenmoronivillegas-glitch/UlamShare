package com.example.ulamshare

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Set default fragment
        if (savedInstanceState == null) {
            if (intent.getBooleanExtra(EXTRA_OPEN_CAMPAIGNS, false) ||
                intent.getStringExtra(EXTRA_POST_ID).orEmpty().isNotBlank()
            ) {
                bottomNavigation.selectedItemId = R.id.nav_campaigns
                replaceFragment(CampaignsFragment.newInstance(intent.extras))
            } else {
                replaceFragment(HomeFragment())
            }
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> replaceFragment(HomeFragment())
                R.id.nav_campaigns -> replaceFragment(CampaignsFragment())
                R.id.nav_profile -> replaceFragment(ProfileFragment())
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        return true
    }

    companion object {
        const val EXTRA_OPEN_CAMPAIGNS = "openCampaigns"
        const val EXTRA_POST_ID = "postId"
        const val EXTRA_COMMENT_ID = "commentId"
        const val EXTRA_REPLY_ID = "replyId"
        const val EXTRA_NOTIFICATION_TYPE = "notificationType"
    }
}
