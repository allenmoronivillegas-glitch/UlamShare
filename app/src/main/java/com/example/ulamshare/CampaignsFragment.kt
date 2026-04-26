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
            targetPostId = arguments?.getString(MainActivity.EXTRA_POST_ID).orEmpty(),
            targetCommentId = arguments?.getString(MainActivity.EXTRA_COMMENT_ID).orEmpty(),
            targetReplyId = arguments?.getString(MainActivity.EXTRA_REPLY_ID).orEmpty(),
            notificationType = arguments?.getString(MainActivity.EXTRA_NOTIFICATION_TYPE).orEmpty(),
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

    companion object {
        fun newInstance(extras: Bundle?): CampaignsFragment {
            return CampaignsFragment().apply {
                arguments = Bundle().apply {
                    putString(MainActivity.EXTRA_POST_ID, extras?.getString(MainActivity.EXTRA_POST_ID).orEmpty())
                    putString(MainActivity.EXTRA_COMMENT_ID, extras?.getString(MainActivity.EXTRA_COMMENT_ID).orEmpty())
                    putString(MainActivity.EXTRA_REPLY_ID, extras?.getString(MainActivity.EXTRA_REPLY_ID).orEmpty())
                    putString(
                        MainActivity.EXTRA_NOTIFICATION_TYPE,
                        extras?.getString(MainActivity.EXTRA_NOTIFICATION_TYPE).orEmpty()
                    )
                }
            }
        }
    }
}
