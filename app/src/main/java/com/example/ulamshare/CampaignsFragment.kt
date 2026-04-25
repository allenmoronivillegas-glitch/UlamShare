package com.example.ulamshare

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class CampaignsFragment : Fragment() {
    private var feedController: ChooseCampaignFeedController? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_choose_campaign, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        feedController = ChooseCampaignFeedController(
            rootView = view,
            lifecycleOwner = viewLifecycleOwner,
            activityResultRegistry = requireActivity().activityResultRegistry,
            onBackPressed = {
                val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)
                if (bottomNav != null) {
                    bottomNav.selectedItemId = R.id.nav_home
                } else {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            },
            launchIntent = { startActivity(it) }
        )
        feedController?.bind()
    }

    override fun onDestroyView() {
        feedController?.dispose()
        feedController = null
        super.onDestroyView()
    }
}
