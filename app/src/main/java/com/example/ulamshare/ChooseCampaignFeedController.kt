package com.example.ulamshare

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class ChooseCampaignFeedController(
    private val rootView: View,
    private val lifecycleOwner: LifecycleOwner,
    private val activityResultRegistry: ActivityResultRegistry,
    private val targetPostId: String = "",
    private val targetCommentId: String = "",
    private val targetReplyId: String = "",
    private val notificationType: String = "",
    private val onBackPressed: () -> Unit,
    private val launchIntent: (Intent) -> Unit
) {
    private companion object {
        const val TAG = "CampaignFeed"
    }

    private enum class FeedFilter {
        ALL,
        OFFICIAL,
        COMMUNITY,
        LIVE_CAMPAIGNS
    }

    private val context = rootView.context
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val campaignsRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("campaigns")
    private val repository = CampaignFeedRepository()

    private var backButton: ImageButton? = null
    private lateinit var chipFilterAll: TextView
    private lateinit var chipFilterOfficial: TextView
    private lateinit var chipFilterCommunity: TextView
    private lateinit var chipFilterLiveCampaigns: TextView
    private lateinit var scrollView: NestedScrollView
    private lateinit var cardAddPost: View
    private lateinit var composerPromptRow: View
    private lateinit var composerPromptAvatar: TextView
    private lateinit var composerPrompt: TextView
    private lateinit var composerQuickPhotoButton: ImageButton
    private lateinit var createPostPanel: View
    private lateinit var closeCreatePostButton: ImageButton
    private lateinit var composerAvatar: TextView
    private lateinit var composerName: TextView
    private lateinit var composerRole: TextView
    private lateinit var postTargetButton: TextView
    private lateinit var campaignPickerButton: TextView
    private lateinit var composerInput: EditText
    private lateinit var composerImagePreviewCard: View
    private lateinit var composerImageView: ImageView
    private lateinit var removeImageButton: ImageButton
    private lateinit var noteButton: TextView
    private lateinit var photoButton: TextView
    private lateinit var officialButton: TextView
    private lateinit var liveCampaignButton: TextView
    private lateinit var liveCampaignFields: View
    private lateinit var liveCampaignTitleInput: EditText
    private lateinit var liveCampaignGoalInput: EditText
    private lateinit var liveCampaignRaisedInput: EditText
    private lateinit var liveCampaignStatusSpinner: Spinner
    private lateinit var submitPostButton: TextView
    private lateinit var feedLabel: TextView
    private lateinit var emptyStateView: TextView
    private lateinit var recyclerView: RecyclerView

    private val adapter = CampaignFeedAdapter(
        onReactClicked = ::handleReact,
        onCommentClicked = ::handleComment,
        onShareClicked = ::handleShare,
        onAuthorClicked = ::openAuthorProfile,
        onCampaignClicked = ::openLinkedCampaign,
        onPostOptionsClicked = ::showPostOptions,
        canManagePost = ::canDeletePost
    )

    private var photoPickerLauncher: ActivityResultLauncher<String>? = null
    private var selectedImageUri: Uri? = null
    private var liveCampaignMode = false
    private var postTarget = CampaignFeedPost.TARGET_COMMUNITY
    private var availableCampaigns: List<Campaign> = emptyList()
    private var selectedCampaign: Campaign? = null
    private var isSubmittingPost = false
    private var isEnsuringViewerSession = false

    private var activeFilter = FeedFilter.ALL
    private var allPosts: List<CampaignFeedPost> = emptyList()
    private var displayedPosts: List<CampaignFeedPost> = emptyList()
    private var currentUserId: String? = null
    private var currentUserName: String = context.getString(R.string.guest_user)
    private var currentUserRole: String = CampaignFeedPost.ROLE_GUEST
    private var pendingDeepLinkHandled = false

    private val pendingViewerActions = mutableListOf<() -> Unit>()

    private var postsRegistration: ListenerRegistration? = null
    private var commentsRegistration: ListenerRegistration? = null
    private var settingsRegistration: ListenerRegistration? = null
    private var campaignsListener: ValueEventListener? = null
    private var feedSettings = CampaignFeedSettings()

    fun bind() {
        bindViews()
        setupRecyclerView()
        setupStatusSpinner()
        registerPhotoPickerIfNeeded()
        setupFilters()
        setupComposer()
        updateViewerUi()
        startSettingsListener()
        startCampaignsListener()
        ensureViewerSession()
    }

    fun dispose() {
        postsRegistration?.remove()
        postsRegistration = null
        commentsRegistration?.remove()
        commentsRegistration = null
        settingsRegistration?.remove()
        settingsRegistration = null
        campaignsListener?.let { campaignsRef.removeEventListener(it) }
        campaignsListener = null
        photoPickerLauncher?.unregister()
        photoPickerLauncher = null
    }

    private fun bindViews() {
        backButton = rootView.findViewById(R.id.btnBack)
        chipFilterAll = rootView.findViewById(R.id.chipFilterAll)
        chipFilterOfficial = rootView.findViewById(R.id.chipFilterOfficial)
        chipFilterCommunity = rootView.findViewById(R.id.chipFilterCommunity)
        chipFilterLiveCampaigns = rootView.findViewById(R.id.chipFilterLiveCampaigns)
        scrollView = rootView.findViewById(R.id.scrollCampaignFeed)
        cardAddPost = rootView.findViewById(R.id.cardAddPost)
        composerPromptRow = rootView.findViewById(R.id.composerPromptRow)
        composerPromptAvatar = rootView.findViewById(R.id.tvComposerPromptAvatar)
        composerPrompt = rootView.findViewById(R.id.tvComposerPrompt)
        composerQuickPhotoButton = rootView.findViewById(R.id.btnComposerQuickPhoto)
        createPostPanel = rootView.findViewById(R.id.createPostPanel)
        closeCreatePostButton = rootView.findViewById(R.id.btnCloseCreatePost)
        composerAvatar = rootView.findViewById(R.id.tvComposerAvatar)
        composerName = rootView.findViewById(R.id.tvComposerName)
        composerRole = rootView.findViewById(R.id.tvComposerRole)
        postTargetButton = rootView.findViewById(R.id.btnComposerPostTarget)
        campaignPickerButton = rootView.findViewById(R.id.btnComposerCampaignPicker)
        composerInput = rootView.findViewById(R.id.etComposerInput)
        composerImagePreviewCard = rootView.findViewById(R.id.composerImagePreviewCard)
        composerImageView = rootView.findViewById(R.id.ivComposerSelectedImage)
        removeImageButton = rootView.findViewById(R.id.btnRemoveSelectedImage)
        noteButton = rootView.findViewById(R.id.btnComposerNote)
        photoButton = rootView.findViewById(R.id.btnComposerPhoto)
        officialButton = rootView.findViewById(R.id.btnComposerOfficial)
        liveCampaignButton = rootView.findViewById(R.id.btnComposerLiveCampaign)
        liveCampaignFields = rootView.findViewById(R.id.liveCampaignFields)
        liveCampaignTitleInput = rootView.findViewById(R.id.etLiveCampaignTitle)
        liveCampaignGoalInput = rootView.findViewById(R.id.etLiveCampaignGoal)
        liveCampaignRaisedInput = rootView.findViewById(R.id.etLiveCampaignRaised)
        liveCampaignStatusSpinner = rootView.findViewById(R.id.spinnerLiveCampaignStatus)
        submitPostButton = rootView.findViewById(R.id.btnSubmitPost)
        feedLabel = rootView.findViewById(R.id.tvFeedSectionLabel)
        emptyStateView = rootView.findViewById(R.id.tvFeedEmptyState)
        recyclerView = rootView.findViewById(R.id.rvCampaigns)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.adapter = adapter
    }

    private fun setupStatusSpinner() {
        ArrayAdapter.createFromResource(
            context,
            R.array.choose_campaign_status_values,
            android.R.layout.simple_spinner_item
        ).also { spinnerAdapter ->
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            liveCampaignStatusSpinner.adapter = spinnerAdapter
        }
    }

    private fun registerPhotoPickerIfNeeded() {
        if (photoPickerLauncher != null) return

        photoPickerLauncher = activityResultRegistry.register(
            "campaign_feed_photo_picker_${System.identityHashCode(this)}",
            lifecycleOwner,
            ActivityResultContracts.GetContent()
        ) { uri ->
            Log.d(TAG, "Photo picker returned uri=$uri")
            if (uri != null) {
                if (openCreatePostPanel(focusInput = false)) {
                    selectedImageUri = uri
                    liveCampaignMode = false
                    composerImageView.setImageDrawable(null)
                    composerImageView.setImageURI(uri)
                    composerImagePreviewCard.visibility = View.VISIBLE
                    syncComposerModeUi()
                }
            }
        }
    }

    private fun setupFilters() {
        chipFilterAll.setOnClickListener { setActiveFilter(FeedFilter.ALL) }
        chipFilterOfficial.setOnClickListener { setActiveFilter(FeedFilter.OFFICIAL) }
        chipFilterCommunity.setOnClickListener { setActiveFilter(FeedFilter.COMMUNITY) }
        chipFilterLiveCampaigns.setOnClickListener { setActiveFilter(FeedFilter.LIVE_CAMPAIGNS) }
        syncFilterUi()
    }

    private fun setupComposer() {
        backButton?.setOnClickListener { onBackPressed() }
        composerPrompt.setOnClickListener { openCreatePostPanel() }
        composerPromptAvatar.setOnClickListener { openCreatePostPanel() }
        composerQuickPhotoButton.setOnClickListener {
            if (openCreatePostPanel(focusInput = false)) {
                openPhotoPicker()
            }
        }
        closeCreatePostButton.setOnClickListener { collapseCreatePostPanel() }
        postTargetButton.setOnClickListener { showPostTargetMenu() }
        campaignPickerButton.setOnClickListener { showCampaignPickerMenu() }
        noteButton.setOnClickListener { switchToNoteMode() }
        photoButton.setOnClickListener { openPhotoPicker() }
        liveCampaignButton.setOnClickListener { toggleLiveCampaignMode() }
        removeImageButton.setOnClickListener {
            selectedImageUri = null
            composerImageView.setImageDrawable(null)
            composerImagePreviewCard.visibility = View.GONE
            syncComposerModeUi()
        }
        submitPostButton.setOnClickListener { submitPost() }
        val submitStateWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSubmitButtonState()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        }
        composerInput.addTextChangedListener(submitStateWatcher)
        liveCampaignTitleInput.addTextChangedListener(submitStateWatcher)
        liveCampaignGoalInput.addTextChangedListener(submitStateWatcher)
        liveCampaignRaisedInput.addTextChangedListener(submitStateWatcher)
        updateSubmitButtonState()
    }

    private fun ensureViewerSession(afterReady: (() -> Unit)? = null) {
        afterReady?.let { pendingViewerActions += it }

        val user = auth.currentUser
        if (user != null) {
            resolveViewerFromUser(user)
            return
        }

        if (isEnsuringViewerSession) {
            return
        }

        isEnsuringViewerSession = true
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                isEnsuringViewerSession = false
                Log.d(TAG, "Anonymous guest sign-in succeeded: uid=${result.user?.uid.orEmpty()}")
                resolveViewerFromUser(result.user ?: auth.currentUser)
            }
            .addOnFailureListener { error ->
                isEnsuringViewerSession = false
                Log.e(TAG, "Anonymous guest sign-in failed: ${firebaseErrorDetails(error)}", error)
                currentUserId = null
                currentUserName = context.getString(R.string.guest_user)
                currentUserRole = CampaignFeedPost.ROLE_GUEST
                updateViewerUi()
                restartPostListener()
                pendingViewerActions.clear()
                Toast.makeText(
                    context,
                    R.string.choose_campaign_guest_session_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun resolveViewerFromUser(user: FirebaseUser?) {
        if (user == null) {
            currentUserId = null
            currentUserName = context.getString(R.string.guest_user)
            currentUserRole = CampaignFeedPost.ROLE_GUEST
            updateViewerUi()
            restartPostListener()
            runPendingViewerActions()
            return
        }

        currentUserId = user.uid
        if (user.isAnonymous) {
            currentUserName = buildGuestName(user.uid)
            currentUserRole = CampaignFeedPost.ROLE_GUEST
            Log.d(
                TAG,
                "Viewer resolved: uid=${currentUserId.orEmpty()}, email=${user.email.orEmpty()}, " +
                    "isGuest=true, role=$currentUserRole, name=$currentUserName"
            )
            updateViewerUi()
            restartPostListener()
            runPendingViewerActions()
            return
        }

        val fallbackName = resolveFallbackUserName(user)
        currentUserName = fallbackName
        currentUserRole = CampaignFeedPost.ROLE_USER
        updateViewerUi()

        firestore.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                val fullName = document.getString("fullName").orEmpty().ifBlank { fallbackName }
                val docRole = document.getString("role").orEmpty()

                user.getIdToken(false)
                    .addOnSuccessListener { result ->
                        val tokenRole = result.claims["role"] as? String
                        finalizeViewer(fullName, normalizeRole(docRole.ifBlank { tokenRole.orEmpty() }))
                    }
                    .addOnFailureListener {
                        finalizeViewer(fullName, normalizeRole(docRole))
                    }
            }
            .addOnFailureListener {
                finalizeViewer(fallbackName, CampaignFeedPost.ROLE_USER)
            }
    }

    private fun finalizeViewer(name: String, role: String) {
        currentUserName = name
        currentUserRole = role
        Log.d(
            TAG,
            "Viewer resolved: uid=${currentUserId.orEmpty()}, email=${auth.currentUser?.email.orEmpty()}, " +
                "isGuest=${currentUserRole == CampaignFeedPost.ROLE_GUEST}, role=$currentUserRole, " +
                "name=$currentUserName"
        )
        updateViewerUi()
        restartPostListener()
        runPendingViewerActions()
    }

    private fun runPendingViewerActions() {
        if (pendingViewerActions.isEmpty()) return
        val actions = pendingViewerActions.toList()
        pendingViewerActions.clear()
        actions.forEach { action -> action.invoke() }
    }

    private fun restartPostListener() {
        postsRegistration?.remove()
        postsRegistration = repository.listenToPosts(
            currentUserId = currentUserId,
            onUpdate = { posts ->
                allPosts = visiblePostsForCurrentViewer(posts)
                applyFeedFilter()
                handlePendingDeepLink()
            },
            onError = {
                Toast.makeText(
                    context,
                    R.string.choose_campaign_load_failed,
                    Toast.LENGTH_SHORT
                ).show()
                allPosts = emptyList()
                applyFeedFilter()
            }
        )
    }

    private fun handlePendingDeepLink() {
        if (pendingDeepLinkHandled || targetPostId.isBlank()) return
        val post = allPosts.firstOrNull { it.id == targetPostId }
        if (post == null) {
            if (isModerationNotificationType(notificationType)) {
                pendingDeepLinkHandled = true
                activeFilter = FeedFilter.ALL
                syncFilterUi()
                applyFeedFilter()
                Toast.makeText(
                    context,
                    moderationRemovedMessage(notificationType),
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        pendingDeepLinkHandled = true
        activeFilter = FeedFilter.ALL
        syncFilterUi()
        applyFeedFilter()

        recyclerView.post {
            val index = displayedPosts.indexOfFirst { it.id == targetPostId }
            if (index >= 0) {
                recyclerView.scrollToPosition(index)
            }
            if (targetCommentId.isNotBlank() || targetReplyId.isNotBlank()) {
                openCommentsDialog(post)
            } else {
                // TODO: Temporarily highlight the exact post card when a stable item animator is in place.
                Toast.makeText(context, R.string.choose_campaign_post_opened, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSettingsListener() {
        settingsRegistration?.remove()
        settingsRegistration = repository.listenToSettings(
            onUpdate = { settings ->
                feedSettings = settings
                Log.d(
                    TAG,
                    "Settings path app_settings/campaign_feed loaded"
                )
                Log.d(TAG, "allowUserPosts=${settings.allowUserPosts}")
                Log.d(TAG, "allowGuestPosts=${settings.allowGuestPosts}")
                Log.d(TAG, "allowGuestReactions=${settings.allowGuestReactions}")
                Log.d(TAG, "allowGuestComments=${settings.allowGuestComments}")
                updateViewerUi()
            },
            onError = { error ->
                Log.w(TAG, "Unable to load campaign feed settings", error)
                feedSettings = CampaignFeedSettings()
                updateViewerUi()
            }
        )
    }

    private fun startCampaignsListener() {
        campaignsListener?.let { campaignsRef.removeEventListener(it) }
        campaignsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                availableCampaigns = snapshot.children
                    .mapNotNull { CampaignDisplayHelper.parseCampaign(it) }
                    .filter { CampaignDisplayHelper.canDonate(it) }
                    .let { CampaignDisplayHelper.sortActiveCampaigns(it) }
                if (selectedCampaign != null &&
                    availableCampaigns.none { it.campaignId == selectedCampaign?.campaignId }
                ) {
                    selectedCampaign = null
                }
                syncPostTargetUi()
                updateSubmitButtonState()
                Log.d(TAG, "Available post campaigns=${availableCampaigns.size}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Unable to load available campaigns for post selector", error.toException())
                availableCampaigns = emptyList()
                selectedCampaign = null
                syncPostTargetUi()
                updateSubmitButtonState()
            }
        }
        campaignsRef.addValueEventListener(campaignsListener!!)
    }

    private fun setActiveFilter(filter: FeedFilter) {
        activeFilter = filter
        syncFilterUi()
        applyFeedFilter()
    }

    private fun syncFilterUi() {
        bindFilterChip(chipFilterAll, activeFilter == FeedFilter.ALL)
        bindFilterChip(chipFilterOfficial, activeFilter == FeedFilter.OFFICIAL)
        bindFilterChip(chipFilterCommunity, activeFilter == FeedFilter.COMMUNITY)
        bindFilterChip(chipFilterLiveCampaigns, activeFilter == FeedFilter.LIVE_CAMPAIGNS)
    }

    private fun bindFilterChip(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_support_chip_active else R.drawable.bg_support_chip
        )
        chip.setTextColor(
            ContextCompat.getColor(
                context,
                if (selected) android.R.color.white else R.color.primary_blue
            )
        )
    }

    private fun applyFeedFilter() {
        displayedPosts = when (activeFilter) {
            FeedFilter.ALL -> allPosts
            FeedFilter.OFFICIAL -> allPosts.filter { it.isOfficialPost }
            FeedFilter.COMMUNITY -> allPosts.filter {
                it.category == CampaignFeedPost.CATEGORY_COMMUNITY &&
                    (it.authorRole == CampaignFeedPost.ROLE_USER ||
                        it.authorRole == CampaignFeedPost.ROLE_GUEST)
            }
            FeedFilter.LIVE_CAMPAIGNS -> allPosts.filter {
                it.postType == CampaignFeedPost.TYPE_LIVE_CAMPAIGN
            }
        }
        renderFeed()
    }

    private fun renderFeed() {
        adapter.submitList(displayedPosts)
        recyclerView.isVisible = displayedPosts.isNotEmpty()
        emptyStateView.isVisible = displayedPosts.isEmpty()
        feedLabel.text = when (activeFilter) {
            FeedFilter.ALL -> context.getString(R.string.choose_campaign_feed_label_all)
            FeedFilter.OFFICIAL -> context.getString(R.string.choose_campaign_feed_label_official)
            FeedFilter.COMMUNITY -> context.getString(R.string.choose_campaign_feed_label_community)
            FeedFilter.LIVE_CAMPAIGNS -> context.getString(R.string.choose_campaign_feed_label_live)
        }
        emptyStateView.text = when (activeFilter) {
            FeedFilter.ALL -> context.getString(R.string.choose_campaign_empty_state)
            FeedFilter.OFFICIAL -> context.getString(R.string.choose_campaign_empty_state_official)
            FeedFilter.COMMUNITY -> context.getString(R.string.choose_campaign_empty_state_community)
            FeedFilter.LIVE_CAMPAIGNS -> context.getString(R.string.choose_campaign_empty_state_live)
        }
    }

    private fun updateViewerUi() {
        composerPromptAvatar.text = buildInitials(currentUserName)
        composerAvatar.text = buildInitials(currentUserName)
        composerName.text = currentUserName
        composerRole.text = roleLabel(currentUserRole)
        composerRole.setBackgroundResource(
            if (canCreateOfficialPosts()) {
                R.drawable.bg_campaign_badge_official
            } else {
                R.drawable.bg_campaign_badge_community
            }
        )
        composerRole.setTextColor(
            ContextCompat.getColor(
                context,
                if (canCreateOfficialPosts()) R.color.primary_blue else R.color.text_grey
            )
        )

        cardAddPost.isVisible = !currentUserId.isNullOrBlank()
        if (!canCreatePosts() && createPostPanel.isVisible) {
            collapseCreatePostPanel()
        }
        officialButton.isVisible = canCreateOfficialPosts()
        liveCampaignButton.isVisible = canCreateOfficialPosts()
        syncComposerModeUi()
        syncPostTargetUi()
        renderSelectedImage()
    }

    private fun syncComposerModeUi() {
        bindComposerModeChip(
            chip = noteButton,
            selected = !liveCampaignMode && selectedImageUri == null
        )
        bindComposerModeChip(
            chip = photoButton,
            selected = selectedImageUri != null && !liveCampaignMode
        )
        if (canCreateOfficialPosts()) {
            bindComposerModeChip(
                chip = officialButton,
                selected = true
            )
            bindComposerModeChip(
                chip = liveCampaignButton,
                selected = liveCampaignMode
            )
        }
        liveCampaignFields.isVisible = liveCampaignMode && canCreateOfficialPosts()
        updateSubmitButtonState()
    }

    private fun syncPostTargetUi() {
        val isCampaignTarget = postTarget == CampaignFeedPost.TARGET_CAMPAIGN
        postTargetButton.text = if (isCampaignTarget) {
            context.getString(R.string.choose_campaign_campaigns_target)
        } else {
            context.getString(R.string.choose_campaign_community_chip)
        }
        campaignPickerButton.isVisible = isCampaignTarget && createPostPanel.isVisible
        campaignPickerButton.text = selectedCampaign?.let { campaign ->
            context.getString(
                R.string.choose_campaign_selected_campaign_format,
                CampaignDisplayHelper.campaignEmoji(campaign),
                campaign.title.orEmpty().ifBlank { context.getString(R.string.choose_campaign_campaign_target) }
            )
        } ?: context.getString(R.string.choose_campaign_choose_campaign)
    }

    private fun showPostTargetMenu() {
        if (!openCreatePostPanel(focusInput = false)) return
        PopupMenu(context, postTargetButton).apply {
            menu.add(0, 1, 0, context.getString(R.string.choose_campaign_community_chip))
            menu.add(0, 2, 1, context.getString(R.string.choose_campaign_campaigns_target))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        postTarget = CampaignFeedPost.TARGET_COMMUNITY
                        selectedCampaign = null
                        syncPostTargetUi()
                        updateSubmitButtonState()
                        true
                    }
                    2 -> {
                        postTarget = CampaignFeedPost.TARGET_CAMPAIGN
                        syncPostTargetUi()
                        updateSubmitButtonState()
                        if (availableCampaigns.isEmpty()) {
                            Toast.makeText(
                                context,
                                R.string.choose_campaign_no_available_campaigns,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (selectedCampaign == null) {
                            showCampaignPickerMenu()
                        }
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showCampaignPickerMenu() {
        if (!openCreatePostPanel(focusInput = false)) return
        if (availableCampaigns.isEmpty()) {
            Toast.makeText(context, R.string.choose_campaign_no_available_campaigns, Toast.LENGTH_SHORT)
                .show()
            return
        }

        PopupMenu(context, campaignPickerButton).apply {
            availableCampaigns.forEachIndexed { index, campaign ->
                val title = campaign.title.orEmpty().ifBlank {
                    context.getString(R.string.choose_campaign_campaign_target)
                }
                val category = campaign.cat.orEmpty().ifBlank {
                    context.getString(R.string.choose_campaign_community_chip)
                }
                menu.add(0, index + 1, index, "${CampaignDisplayHelper.campaignEmoji(campaign)} $title · $category")
            }
            setOnMenuItemClickListener { item ->
                selectedCampaign = availableCampaigns.getOrNull(item.itemId - 1)
                syncPostTargetUi()
                updateSubmitButtonState()
                true
            }
            show()
        }
    }

    private fun bindComposerModeChip(chip: TextView, selected: Boolean) {
        val color = ContextCompat.getColor(
            context,
            if (selected) android.R.color.white else R.color.primary_blue
        )
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_support_chip_active else R.drawable.bg_support_chip
        )
        chip.setTextColor(color)
        chip.compoundDrawablesRelative
            .filterNotNull()
            .forEach { drawable ->
                DrawableCompat.setTint(DrawableCompat.wrap(drawable).mutate(), color)
            }
    }

    private fun openCreatePostPanel(focusInput: Boolean = true): Boolean {
        if (currentUserId.isNullOrBlank()) {
            ensureViewerSession { openCreatePostPanel(focusInput) }
            return false
        }

        disabledPostMessageRes()?.let { messageRes ->
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
            return false
        }

        if (!canCreatePosts()) {
            Toast.makeText(context, R.string.choose_campaign_post_unauthenticated, Toast.LENGTH_SHORT)
                .show()
            return false
        }

        composerPromptRow.isVisible = false
        createPostPanel.isVisible = true
        syncPostTargetUi()
        renderSelectedImage()
        updateSubmitButtonState()
        if (focusInput) {
            composerInput.requestFocus()
        }
        return true
    }

    private fun collapseCreatePostPanel() {
        if (isSubmittingPost) return
        createPostPanel.isVisible = false
        composerPromptRow.isVisible = true
        syncPostTargetUi()
        updateSubmitButtonState()
    }

    private fun canCreatePosts(): Boolean {
        if (currentUserId.isNullOrBlank()) return false
        if (canCreateOfficialPosts()) return true
        return if (currentUserRole == CampaignFeedPost.ROLE_GUEST) {
            feedSettings.allowGuestPosts
        } else {
            feedSettings.allowUserPosts
        }
    }

    private fun canCreateOfficialPosts(): Boolean {
        return currentUserRole == CampaignFeedPost.ROLE_ADMIN ||
            currentUserRole == CampaignFeedPost.ROLE_SUPER_ADMIN
    }

    private fun canViewModeratedFeedContent(): Boolean {
        return canCreateOfficialPosts() || currentUserRole == CampaignFeedPost.ROLE_MODERATOR
    }

    private fun visiblePostsForCurrentViewer(posts: List<CampaignFeedPost>): List<CampaignFeedPost> {
        return if (canViewModeratedFeedContent()) {
            posts
        } else {
            posts.filter { it.isVisibleAfterModeration }
        }
    }

    private fun visibleCommentsForCurrentViewer(
        comments: List<CampaignPostComment>
    ): List<CampaignPostComment> {
        if (canViewModeratedFeedContent()) return comments
        return comments
            .filter { it.isVisibleAfterModeration }
            .map { comment ->
                comment.copy(
                    replies = comment.replies.filter { it.isVisibleAfterModeration }
                )
            }
    }

    private fun canDeletePost(post: CampaignFeedPost): Boolean {
        val viewerId = currentUserId.orEmpty()
        return when {
            viewerId.isBlank() -> false
            canViewModeratedFeedContent() -> true
            currentUserRole == CampaignFeedPost.ROLE_GUEST -> false
            else -> viewerId == post.authorId
        }
    }

    private fun switchToNoteMode() {
        if (!openCreatePostPanel(focusInput = false)) return
        liveCampaignMode = false
        selectedImageUri = null
        clearLiveCampaignFields()
        renderSelectedImage()
        syncComposerModeUi()
    }

    private fun openPhotoPicker() {
        if (!createPostPanel.isVisible && !openCreatePostPanel(focusInput = false)) return
        try {
            photoPickerLauncher?.launch("image/*")
        } catch (_: Exception) {
            Toast.makeText(context, R.string.choose_campaign_photo_pick_failed, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun toggleLiveCampaignMode() {
        if (!canCreateOfficialPosts()) return
        if (!openCreatePostPanel(focusInput = false)) return

        liveCampaignMode = !liveCampaignMode
        if (!liveCampaignMode) {
            clearLiveCampaignFields()
            Toast.makeText(context, R.string.choose_campaign_live_toggle_off, Toast.LENGTH_SHORT)
                .show()
        } else {
            postTarget = CampaignFeedPost.TARGET_COMMUNITY
            selectedCampaign = null
            Toast.makeText(context, R.string.choose_campaign_live_toggle_on, Toast.LENGTH_SHORT)
                .show()
        }
        syncComposerModeUi()
        syncPostTargetUi()
    }

    private fun renderSelectedImage() {
        val hasSelectedImage = selectedImageUri != null
        composerImagePreviewCard.isVisible = hasSelectedImage && canCreatePosts() && createPostPanel.isVisible
        if (hasSelectedImage) {
            composerImageView.setImageDrawable(null)
            composerImageView.setImageURI(selectedImageUri)
            composerImageView.invalidate()
        } else {
            composerImageView.setImageDrawable(null)
        }
    }

    private fun submitPost() {
        if (isSubmittingPost) return

        if (currentUserId.isNullOrBlank()) {
            ensureViewerSession { submitPost() }
            return
        }

        Log.d(TAG, "Posting started")
        logPostingState(postType = if (selectedImageUri != null) {
            CampaignFeedPost.TYPE_PHOTO
        } else {
            CampaignFeedPost.TYPE_NOTE
        })

        disabledPostMessageRes()?.let { messageRes ->
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
            updateViewerUi()
            return
        }

        val text = composerInput.text?.toString()?.trim().orEmpty()
        val liveTitle = liveCampaignTitleInput.text?.toString()?.trim().orEmpty()
        val liveGoal = liveCampaignGoalInput.text?.toString()?.trim().orEmpty().toLongOrNull() ?: 0L
        val liveRaised = liveCampaignRaisedInput.text?.toString()?.trim().orEmpty().toLongOrNull() ?: 0L
        val liveStatus = normalizeStatus(liveCampaignStatusSpinner.selectedItem?.toString())
        val linkedCampaign = selectedCampaign

        if (liveCampaignMode) {
            if (liveTitle.isBlank()) {
                Toast.makeText(
                    context,
                    R.string.choose_campaign_live_missing_title,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            if (liveGoal <= 0L) {
                Toast.makeText(
                    context,
                    R.string.choose_campaign_live_missing_goal,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        } else if (postTarget == CampaignFeedPost.TARGET_CAMPAIGN && linkedCampaign == null) {
            Toast.makeText(context, R.string.choose_campaign_select_campaign_first, Toast.LENGTH_SHORT).show()
            return
        } else if (selectedImageUri == null && text.isBlank()) {
            Toast.makeText(context, R.string.choose_campaign_post_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val authorId = currentUserId.orEmpty()
        if (authorId.isBlank()) {
            ensureViewerSession { submitPost() }
            return
        }

        val draft = CampaignComposerDraft(
            text = text,
            imageUri = selectedImageUri,
            category = currentPostCategory(),
            postTarget = if (liveCampaignMode) {
                CampaignFeedPost.TARGET_COMMUNITY
            } else {
                postTarget
            },
            linkedCampaignId = if (liveCampaignMode) "" else linkedCampaign?.campaignId.orEmpty(),
            linkedCampaignTitle = if (liveCampaignMode) "" else linkedCampaign?.title.orEmpty(),
            linkedCampaignCategory = if (liveCampaignMode) "" else linkedCampaign?.cat.orEmpty(),
            linkedCampaignEmoji = if (liveCampaignMode) "" else linkedCampaign?.let {
                CampaignDisplayHelper.campaignEmoji(it)
            }.orEmpty(),
            isLiveCampaign = liveCampaignMode,
            campaignTitle = liveTitle,
            campaignGoal = liveGoal,
            campaignRaised = liveRaised,
            campaignStatus = liveStatus
        )

        Log.d(
            TAG,
            "Submitting campaign post: uid=$authorId, email=${auth.currentUser?.email.orEmpty()}, " +
                "isGuest=${currentUserRole == CampaignFeedPost.ROLE_GUEST}, role=$currentUserRole, " +
                "allowUserPosts=${feedSettings.allowUserPosts}, " +
                "allowGuestPosts=${feedSettings.allowGuestPosts}, " +
                "category=${draft.category}, postType=${draft.resolvedPostType()}, " +
                "hasImage=${draft.imageUri != null}, textLength=${draft.text.length}"
        )

        isSubmittingPost = true
        updateSubmitButtonState()

        repository.createPost(
            author = CampaignPostAuthor(
                id = authorId,
                name = currentUserName,
                role = currentUserRole
            ),
            draft = draft
        ) { result ->
            isSubmittingPost = false
            updateSubmitButtonState()

            result.onSuccess {
                clearComposer()
                collapseCreatePostPanel()
                Toast.makeText(context, R.string.choose_campaign_post_created, Toast.LENGTH_SHORT)
                    .show()
                scrollView.post { scrollView.smoothScrollTo(0, 0) }
            }.onFailure { error ->
                Log.e(TAG, "Post publish failed", error)
                Log.e(TAG, "Post publish failed details: ${firebaseErrorDetails(error)}")
                if (error is CampaignImageUploadException) {
                    Log.e(TAG, "Image upload failed", error.cause ?: error)
                    Log.e(TAG, "Cloudinary upload failed: ${(error.cause ?: error).message.orEmpty()}")
                }
                if (error is CampaignFirestoreSaveException) {
                    Log.e(TAG, "Firestore post save failed", error.cause ?: error)
                }
                Toast.makeText(
                    context,
                    publishFailureMessage(error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun logPostingState(postType: String) {
        Log.d(TAG, "selectedImageUri=${selectedImageUri?.toString().orEmpty()}")
        Log.d(TAG, "currentUserId=${currentUserId.orEmpty()}")
        Log.d(TAG, "authorRole=$currentUserRole")
        Log.d(TAG, "allowUserPosts=${feedSettings.allowUserPosts}")
        Log.d(TAG, "allowGuestPosts=${feedSettings.allowGuestPosts}")
        Log.d(TAG, "currentUid=${currentUserId.orEmpty()}")
        Log.d(TAG, "isGuest=${currentUserRole == CampaignFeedPost.ROLE_GUEST}")
        Log.d(TAG, "userRole=$currentUserRole")
        Log.d(TAG, "postType=$postType")
    }

    private fun firebaseErrorDetails(error: Throwable): String {
        val cause = error.cause ?: error
        val firestoreCode = (cause as? FirebaseFirestoreException)?.code?.name.orEmpty()
        return "type=${error.javaClass.simpleName}, causeType=${cause.javaClass.simpleName}, firestoreCode=$firestoreCode, message=${cause.message.orEmpty()}"
    }

    private fun publishFailureMessage(error: Throwable): Int {
        val cause = error.cause ?: error
        return when ((cause as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                R.string.choose_campaign_post_permission_denied

            FirebaseFirestoreException.Code.UNAVAILABLE ->
                R.string.choose_campaign_post_network_failed

            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                R.string.choose_campaign_post_unauthenticated

            else -> if (error is CampaignImageUploadException) {
                R.string.choose_campaign_photo_upload_failed
            } else {
                R.string.choose_campaign_post_create_failed
            }
        }
    }

    private fun disabledPostMessageRes(): Int? {
        if (canCreateOfficialPosts()) return null
        return if (currentUserRole == CampaignFeedPost.ROLE_GUEST) {
            if (feedSettings.allowGuestPosts) null else R.string.choose_campaign_guest_posting_disabled
        } else {
            if (feedSettings.allowUserPosts) null else R.string.choose_campaign_user_posting_disabled
        }
    }

    private fun currentPostCategory(): String {
        return if (canCreateOfficialPosts()) {
            CampaignFeedPost.CATEGORY_OFFICIAL
        } else {
            CampaignFeedPost.CATEGORY_COMMUNITY
        }
    }

    private fun handleReact(post: CampaignFeedPost) {
        val actorId = currentUserId
        if (actorId.isNullOrBlank()) {
            ensureViewerSession { handleReact(post) }
            return
        }

        if (currentUserRole == CampaignFeedPost.ROLE_GUEST && !feedSettings.allowGuestReactions) {
            Toast.makeText(context, R.string.choose_campaign_guest_reactions_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        submitReaction(post, CampaignReactionUi.LIKE)
    }

    private fun submitReaction(post: CampaignFeedPost, reactionType: String) {
        val actorId = currentUserId.orEmpty()
        if (actorId.isBlank()) {
            ensureViewerSession { submitReaction(post, reactionType) }
            return
        }

        repository.toggleReaction(
            post = post,
            postId = post.id,
            actorId = actorId,
            actorName = currentUserName,
            actorRole = currentUserRole,
            reactionType = reactionType
        ) { result ->
            result.onSuccess { selectedReactionType ->
                applyLocalReactionState(post.id, selectedReactionType)
                applyFeedFilter()
            }.onFailure {
                Toast.makeText(
                    context,
                    R.string.choose_campaign_reaction_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun applyLocalReactionState(postId: String, selectedReactionType: String) {
        allPosts = allPosts.map { current ->
            if (current.id != postId) return@map current

            val oldType = current.myReactionType
            val counts = current.reactionCounts.toMutableMap()
            var total = current.reactCount

            if (oldType.isNotBlank() && oldType != selectedReactionType) {
                counts[oldType] = ((counts[oldType] ?: 0) - 1).coerceAtLeast(0)
                total = (total - 1).coerceAtLeast(0)
            }
            if (selectedReactionType.isNotBlank()) {
                counts[selectedReactionType] = (counts[selectedReactionType] ?: 0) + 1
                total += 1
            }

            current.copy(
                myReactionType = selectedReactionType,
                reactedByMe = selectedReactionType.isNotBlank(),
                reactCount = total.coerceAtLeast(0),
                reactionCounts = counts
            )
        }
    }

    private fun handleComment(post: CampaignFeedPost) {
        if (currentUserId.isNullOrBlank()) {
            ensureViewerSession { handleComment(post) }
            return
        }

        if (currentUserRole == CampaignFeedPost.ROLE_GUEST && !feedSettings.allowGuestComments) {
            Toast.makeText(context, R.string.choose_campaign_guest_comments_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        openCommentsDialog(post)
    }

    private fun handleShare(post: CampaignFeedPost) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(R.string.choose_campaign_share_caption, post.authorName)
            )
            putExtra(Intent.EXTRA_TEXT, buildShareText(post))
        }

        launchIntent(
            Intent.createChooser(
                shareIntent,
                context.getString(R.string.choose_campaign_share_chooser)
            )
        )
    }

    private fun openAuthorProfile(post: CampaignFeedPost) {
        if (post.authorId.isBlank()) return
        if (post.authorId == currentUserId) {
            launchIntent(Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_PROFILE, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        } else {
            launchIntent(Intent(context, PublicProfileActivity::class.java).apply {
                putExtra("targetUserId", post.authorId)
            })
        }
    }

    private fun openLinkedCampaign(post: CampaignFeedPost) {
        if (!post.hasLinkedCampaign) return
        launchIntent(Intent(context, CampaignCatalogActivity::class.java).apply {
            putExtra(CampaignCatalogActivity.EXTRA_INITIAL_TAB, CampaignCatalogActivity.TAB_ACTIVE)
            putExtra(MainActivity.EXTRA_CAMPAIGN_ID, post.linkedCampaignId)
            putExtra("openCampaign", true)
        })
    }

    private fun showPostOptions(anchor: View, post: CampaignFeedPost) {
        if (!canDeletePost(post)) return

        val popupMenu = PopupMenu(context, anchor)
        popupMenu.menu.add(0, 1, 0, context.getString(R.string.delete_action))
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    confirmDeletePost(post)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun confirmDeletePost(post: CampaignFeedPost) {
        AlertDialog.Builder(context)
            .setTitle(R.string.choose_campaign_delete_post_title)
            .setMessage(R.string.choose_campaign_delete_post_message)
            .setNegativeButton(R.string.cancel_action, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                deletePost(post)
            }
            .show()
    }

    private fun deletePost(post: CampaignFeedPost) {
        repository.deletePost(post) { result ->
            result.onSuccess {
                Toast.makeText(
                    context,
                    R.string.choose_campaign_delete_post_success,
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    R.string.choose_campaign_delete_post_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openCommentsDialog(post: CampaignFeedPost) {
        commentsRegistration?.remove()
        commentsRegistration = null

        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_campaign_comments, null, false)
        val commentsRecycler = dialogView.findViewById<RecyclerView>(R.id.rvComments)
        val commentsEmptyView = dialogView.findViewById<TextView>(R.id.tvCommentsEmpty)
        val commentInput = dialogView.findViewById<EditText>(R.id.etCommentInput)
        val sendCommentButton = dialogView.findViewById<TextView>(R.id.btnSendComment)
        val replyModeContainer = dialogView.findViewById<View>(R.id.replyModeContainer)
        val replyingToView = dialogView.findViewById<TextView>(R.id.tvReplyingTo)
        val cancelReplyButton = dialogView.findViewById<TextView>(R.id.btnCancelReply)
        var replyTarget: CampaignPostComment? = null
        var replyToReplyTarget: CampaignPostReply? = null

        fun clearReplyMode() {
            replyTarget = null
            replyToReplyTarget = null
            replyModeContainer.isVisible = false
            replyingToView.text = ""
            sendCommentButton.text = context.getString(R.string.choose_campaign_send_comment)
        }

        val commentsAdapter = CampaignCommentAdapter(
            onReplyClicked = { comment, reply ->
                replyTarget = comment
                replyToReplyTarget = reply
                val targetName = reply?.authorName ?: comment.authorName
                replyModeContainer.isVisible = true
                replyingToView.text = context.getString(
                    R.string.choose_campaign_replying_to,
                    targetName
                )
                val mentionPrefix = "@$targetName "
                commentInput.setText(mentionPrefix)
                commentInput.setSelection(commentInput.text?.length ?: 0)
                commentInput.requestFocus()
                sendCommentButton.text = context.getString(R.string.choose_campaign_send_reply)
            },
            onUserClicked = { userId ->
                if (userId.isNotBlank()) {
                    PublicProfileActivity.start(context, userId)
                }
            }
        )

        commentsRecycler.layoutManager = LinearLayoutManager(context)
        commentsRecycler.adapter = commentsAdapter

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        cancelReplyButton.setOnClickListener {
            clearReplyMode()
            commentInput.text?.clear()
        }

        sendCommentButton.setOnClickListener {
            val commentText = commentInput.text?.toString()?.trim().orEmpty()
            val actorId = currentUserId.orEmpty()
            if (commentText.isBlank()) {
                Toast.makeText(context, R.string.choose_campaign_comment_empty, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (actorId.isBlank()) {
                ensureViewerSession { openCommentsDialog(post) }
                dialog.dismiss()
                return@setOnClickListener
            }

            sendCommentButton.isEnabled = false
            val activeReplyTarget = replyTarget
            if (activeReplyTarget == null) {
                repository.addComment(
                    postId = post.id,
                    userId = actorId,
                    userName = currentUserName,
                    userRole = currentUserRole,
                    text = commentText
                ) { result ->
                    sendCommentButton.isEnabled = true
                    result.onSuccess {
                        commentInput.text?.clear()
                        Toast.makeText(context, R.string.choose_campaign_comment_sent, Toast.LENGTH_SHORT)
                            .show()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            R.string.choose_campaign_comment_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                repository.addReply(
                    postId = post.id,
                    parentComment = activeReplyTarget,
                    replyingToReply = replyToReplyTarget,
                    userId = actorId,
                    userName = currentUserName,
                    userRole = currentUserRole,
                    text = commentText,
                    mentionedUserId = replyToReplyTarget?.authorId ?: activeReplyTarget.authorId,
                    mentionedUserName = replyToReplyTarget?.authorName ?: activeReplyTarget.authorName
                ) { result ->
                    sendCommentButton.isEnabled = true
                    result.onSuccess {
                        commentInput.text?.clear()
                        clearReplyMode()
                        Toast.makeText(context, R.string.choose_campaign_reply_sent, Toast.LENGTH_SHORT)
                            .show()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            R.string.choose_campaign_comment_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        commentsRegistration = repository.listenToComments(
            postId = post.id,
            onUpdate = { comments ->
                val visibleComments = visibleCommentsForCurrentViewer(comments)
                commentsAdapter.submitList(visibleComments)
                commentsEmptyView.isVisible = visibleComments.isEmpty()
                if (post.id == targetPostId && (targetCommentId.isNotBlank() || targetReplyId.isNotBlank())) {
                    val targetIndex = visibleComments.indexOfFirst { comment ->
                        comment.id == targetCommentId ||
                            comment.replies.any { reply -> reply.id == targetReplyId }
                    }
                    if (targetIndex >= 0) {
                        commentsRecycler.post {
                            commentsRecycler.scrollToPosition(targetIndex)
                        }
                    } else if (isModerationNotificationType(notificationType)) {
                        Toast.makeText(
                            context,
                            moderationRemovedMessage(notificationType),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    // TODO: Add a brief highlight animation for the exact comment/reply row.
                }
                val totalCommentsAndReplies = comments.sumOf { 1 + it.replies.size }
                allPosts = allPosts.map { current ->
                    if (current.id == post.id) {
                        current.copy(commentCount = totalCommentsAndReplies)
                    } else {
                        current
                    }
                }
                applyFeedFilter()
            },
            onError = {
                Toast.makeText(
                    context,
                    R.string.choose_campaign_comment_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        dialog.setOnDismissListener {
            commentsRegistration?.remove()
            commentsRegistration = null
        }
        dialog.show()
    }

    private fun isModerationNotificationType(type: String): Boolean {
        return when (type) {
            FirestoreNotificationRepository.TYPE_POST_HIDDEN,
            FirestoreNotificationRepository.TYPE_POST_DELETED,
            FirestoreNotificationRepository.TYPE_COMMENT_HIDDEN,
            FirestoreNotificationRepository.TYPE_COMMENT_DELETED,
            FirestoreNotificationRepository.TYPE_REPLY_HIDDEN,
            FirestoreNotificationRepository.TYPE_REPLY_DELETED -> true
            else -> false
        }
    }

    private fun moderationRemovedMessage(type: String): String {
        return when (type) {
            FirestoreNotificationRepository.TYPE_COMMENT_HIDDEN,
            FirestoreNotificationRepository.TYPE_COMMENT_DELETED -> "This comment was removed by moderation."
            FirestoreNotificationRepository.TYPE_REPLY_HIDDEN,
            FirestoreNotificationRepository.TYPE_REPLY_DELETED -> "This reply was removed by moderation."
            else -> "This post was removed by moderation."
        }
    }

    private fun buildShareText(post: CampaignFeedPost): String {
        val sections = mutableListOf<String>()
        sections += context.getString(R.string.choose_campaign_share_caption, post.authorName)
        sections += post.badgeLabel

        if (post.hasCampaignInfo) {
            sections += context.getString(
                R.string.choose_campaign_share_live_title,
                post.campaignTitle.ifBlank { context.getString(R.string.choose_campaign_live_campaign) }
            )
            sections += context.getString(
                R.string.choose_campaign_share_progress,
                formatCurrency(post.campaignRaised),
                formatCurrency(post.campaignGoal)
            )
            sections += context.getString(
                R.string.choose_campaign_status_format,
                statusLabel(post.campaignStatus)
            )
        }

        if (post.text.isNotBlank()) {
            sections += post.text
        }

        if (post.imageUrl.isNotBlank()) {
            sections += post.imageUrl
        }

        sections += context.getString(R.string.choose_campaign_share_link_label)
        return sections.joinToString(separator = "\n\n")
    }

    private fun updateSubmitButtonState() {
        val canSubmit = !isSubmittingPost && hasSubmitReadyDraft()
        submitPostButton.isEnabled = canSubmit
        submitPostButton.alpha = when {
            isSubmittingPost -> 0.72f
            canSubmit -> 1f
            else -> 0.48f
        }
        val labelRes = when {
            isSubmittingPost && selectedImageUri != null -> R.string.choose_campaign_uploading_photo
            isSubmittingPost -> R.string.choose_campaign_posting
            else -> R.string.choose_campaign_post
        }
        submitPostButton.text = context.getString(labelRes)
    }

    private fun hasSubmitReadyDraft(): Boolean {
        if (!createPostPanel.isVisible || !canCreatePosts()) return false
        val text = composerInput.text?.toString()?.trim().orEmpty()
        if (!liveCampaignMode &&
            postTarget == CampaignFeedPost.TARGET_CAMPAIGN &&
            selectedCampaign == null
        ) {
            return false
        }
        if (selectedImageUri != null || text.isNotBlank()) return true

        if (!liveCampaignMode || !canCreateOfficialPosts()) return false
        val liveTitle = liveCampaignTitleInput.text?.toString()?.trim().orEmpty()
        val liveGoal = liveCampaignGoalInput.text?.toString()?.trim().orEmpty().toLongOrNull() ?: 0L
        return liveTitle.isNotBlank() && liveGoal > 0L
    }

    private fun clearComposer() {
        composerInput.text?.clear()
        selectedImageUri = null
        liveCampaignMode = false
        postTarget = CampaignFeedPost.TARGET_COMMUNITY
        selectedCampaign = null
        clearLiveCampaignFields()
        renderSelectedImage()
        syncComposerModeUi()
        syncPostTargetUi()
    }

    private fun clearLiveCampaignFields() {
        liveCampaignTitleInput.text?.clear()
        liveCampaignGoalInput.text?.clear()
        liveCampaignRaisedInput.setText("0")
        liveCampaignStatusSpinner.setSelection(0)
    }

    private fun resolveFallbackUserName(user: FirebaseUser): String {
        return when {
            !user.displayName.isNullOrBlank() -> user.displayName!!.trim()
            else -> context.getString(R.string.hopegive_user)
        }
    }

    private fun buildGuestName(userId: String): String {
        val suffix = userId.takeLast(4).uppercase(Locale.getDefault())
        return "${context.getString(R.string.guest_user)} $suffix"
    }

    private fun buildInitials(name: String): String {
        val parts = name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
        if (parts.isEmpty()) return "HG"
        return parts.joinToString(separator = "") { it.first().uppercase(Locale.getDefault()) }
    }

    private fun normalizeRole(raw: String?): String {
        return when (raw.orEmpty().trim().lowercase(Locale.getDefault())) {
            "super admin", "super_admin", "superadmin" -> CampaignFeedPost.ROLE_SUPER_ADMIN
            "admin" -> CampaignFeedPost.ROLE_ADMIN
            "moderator", "mod" -> CampaignFeedPost.ROLE_MODERATOR
            "guest" -> CampaignFeedPost.ROLE_GUEST
            else -> CampaignFeedPost.ROLE_USER
        }
    }

    private fun normalizeStatus(raw: String?): String {
        return when (raw.orEmpty().trim().lowercase(Locale.getDefault())) {
            context.getString(R.string.choose_campaign_live_status_completed).lowercase(Locale.getDefault()),
            CampaignFeedPost.STATUS_COMPLETED -> CampaignFeedPost.STATUS_COMPLETED

            context.getString(R.string.choose_campaign_live_status_paused).lowercase(Locale.getDefault()),
            CampaignFeedPost.STATUS_PAUSED -> CampaignFeedPost.STATUS_PAUSED

            else -> CampaignFeedPost.STATUS_ACTIVE
        }
    }

    private fun roleLabel(role: String): String {
        return when (role) {
            CampaignFeedPost.ROLE_SUPER_ADMIN -> context.getString(R.string.choose_campaign_super_admin_role)
            CampaignFeedPost.ROLE_ADMIN -> context.getString(R.string.choose_campaign_admin_role)
            CampaignFeedPost.ROLE_MODERATOR -> "Moderator"
            CampaignFeedPost.ROLE_GUEST -> context.getString(R.string.choose_campaign_guest_role)
            else -> context.getString(R.string.choose_campaign_user_role)
        }
    }

    private fun statusLabel(status: String): String {
        return when (status) {
            CampaignFeedPost.STATUS_COMPLETED -> context.getString(R.string.choose_campaign_live_status_completed)
            CampaignFeedPost.STATUS_PAUSED -> context.getString(R.string.choose_campaign_live_status_paused)
            else -> context.getString(R.string.choose_campaign_live_status_active)
        }
    }

    private fun formatCurrency(amount: Long): String {
        return String.format(Locale.US, "\u20B1%,d", amount)
    }

    private fun Int.dp(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
