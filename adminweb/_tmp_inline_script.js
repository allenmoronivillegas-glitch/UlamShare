
const titles={dashboard:'Dashboard',campaigns:'Campaigns',donations:'Donations',donors:'Donors',payments:'Payments',users:'Team',settings:'Settings',support:'Support'};
let selectedUserId = null;
let selectedUserEmail = '';
let selectedConversationType = 'support';
let selectedChatSource = 'supportChats';
let selectedConversationKey = '';
let detachChatListener = null;
let sAllUsers = [];
let sTypeFilter = 'all';
let sReplyToKey = null;
let sReplyToText = '';
let sReplyToSenderName = '';
let sReplyToSenderRole = '';
let sEmojiPickerTarget = null;
let sCurrentMessages = [];
let detachTypingListener = null;
let sTypingStopTimer = null;
let sTypingPresenceActive = false;
let sTypingEntries = [];
const S_TYPING_IDLE_MS = 2500;
const S_TYPING_STALE_MS = 5000;

// Support chat UI
const S_COLORS = [
  {bg:'#2f80ed',fg:'#ffffff'},
  {bg:'#1f63c0',fg:'#ffffff'},
  {bg:'#3c93f6',fg:'#ffffff'},
  {bg:'#5b8fd6',fg:'#ffffff'},
  {bg:'#2c6fb2',fg:'#ffffff'},
  {bg:'#74aaf3',fg:'#0f2c4f'},
];
const S_SUPPORT_ROLES = ['admin','moderator','superadmin','support'];
const S_QUICK_REACTIONS = ['+1','love','wow','sad','ok','thanks'];
const S_REACTION_DISPLAY_MAP = {
  '+1': '👍',
  '-1': '👎',
  heart: '❤️',
  laugh: '😄',
  hooray: '🎉',
  confused: '😕',
  eyes: '👀',
  love: '❤️',
  wow: '😮',
  sad: '😢',
  ok: '👌',
  thanks: '🙏'
};
const S_CHAT_SOURCES = [
  { path:'supportChats', defaultType:'support', label:'Support' },
  { path:'userUserChats', defaultType:'user-user', label:'User-User' },
  { path:'adminTeamChats', defaultType:'admin-team', label:'Admin Team' },
];
const ASSISTANT_FAQS = [
  {
    question:'How do I donate?',
    keywords:['donate','donation','give','payment','amount'],
    answer:'To donate, go to Campaigns, choose a campaign, enter your donation amount, select a payment method, and confirm your donation.'
  },
  {
    question:'How do I choose a campaign?',
    keywords:['campaign','choose','fundraiser','browse','progress'],
    answer:'Go to Campaigns and browse the available campaigns. You can view campaign details, progress, and updates before donating.'
  },
  {
    question:'Where can I see my donations?',
    keywords:['my donations','donation history','history','receipt','records'],
    answer:'Open Profile and tap My Donations to view your donation history.'
  },
  {
    question:'How do I contact support?',
    keywords:['support','admin','contact','help','problem'],
    answer:'Open Messenger and choose Support/Admin Team, or use HopeGive Assistant and tap Talk to Support.'
  },
  {
    question:'How do I edit my profile?',
    keywords:['profile','edit','name','phone','email','photo'],
    answer:'Open Profile, tap the pencil/edit button, update your details, then tap Save Changes.'
  },
  {
    question:'How do campaign posts work?',
    keywords:['campaign feed','feed','post','comment','reaction','share'],
    answer:'The Campaign Feed shows official HopeGive updates, community posts, comments, reactions, and campaign progress.'
  },
  {
    question:'How do payment methods work?',
    keywords:['payment method','payment methods','gcash','maya','card','saved method'],
    answer:'Open Profile -> Payment Methods to add or manage GCash, Maya, or card details. Payment methods are saved for future donations.'
  }
];
let sSourceBuckets = {};
let sSourceDetachers = [];

function sNormalizeRole(value){
  const raw = String(value || '').trim().toLowerCase();
  if(raw === 'super admin' || raw === 'super_admin' || raw === 'superadmin') return 'superadmin';
  if(raw === 'admin') return 'admin';
  if(raw === 'moderator' || raw === 'mod') return 'moderator';
  if(raw === 'support' || raw === 'agent') return 'support';
  if(raw === 'system' || raw === 'bot') return 'system';
  if(raw === 'user' || raw === 'member' || raw === 'donor') return 'user';
  return '';
}

function sRoleLabel(role){
  if(role === 'superadmin') return 'Super Admin';
  if(role === 'moderator') return 'Moderator';
  if(role === 'admin') return 'Admin';
  if(role === 'support') return 'Support';
  if(role === 'system') return 'System';
  return 'User';
}

function sRoleClass(role){
  if(role === 'superadmin') return 'superadmin';
  if(role === 'moderator') return 'moderator';
  if(role === 'admin') return 'admin';
  if(role === 'support') return 'support';
  if(role === 'system') return 'system';
  return 'user';
}

function sTypeLabel(type){
  if(type === 'user-user') return 'User-User';
  if(type === 'admin-team') return 'Admin Team';
  return 'User-Support';
}

function sGetCurrentSupportRole(){
  const profileRole = window.currentUserProfile?.role || (typeof currentRole === 'function' ? currentRole() : 'Support');
  return sNormalizeRole(profileRole) || 'support';
}

function sCanReplyToSupport(){
  return ['superadmin','admin','moderator'].includes(sGetCurrentSupportRole());
}

function sIsSupportRole(role){
  return S_SUPPORT_ROLES.includes(role);
}

function sGetColor(str){
  const text = String(str || 'user');
  let h = 0;
  for(let i = 0; i < text.length; i++) h = text.charCodeAt(i) + ((h << 5) - h);
  return S_COLORS[Math.abs(h) % S_COLORS.length];
}

function sInitials(name){
  const clean = String(name || '').trim();
  if(!clean) return 'US';
  const words = clean.split(/[\s@._-]+/).filter(Boolean);
  if(words.length >= 2){
    return (words[0][0] + words[1][0]).toUpperCase();
  }
  return clean.substring(0,2).toUpperCase();
}

function sAvaHtml(label,size=34,isOnline=true,showDot=true){
  const c = sGetColor(label);
  const dotColor = isOnline ? '#22c55e' : '#93c5fd';
  const fontSize = Math.max(11, Math.round(size * 0.35));
  return `<div class="s-ava" style="width:${size}px;height:${size}px;background:${c.bg};color:${c.fg};font-size:${fontSize}px">${sInitials(label)}${showDot ? `<div class="s-ava-dot" style="background:${dotColor}"></div>` : ''}</div>`;
}

function sFormatTime(value){
  if(!value) return '';
  return new Date(value).toLocaleTimeString([], { hour:'2-digit', minute:'2-digit' });
}

function sFormatDateLabel(value){
  if(!value) return 'Today';
  return new Date(value).toLocaleDateString([], { month:'short', day:'numeric', year:'numeric' });
}

function assistantNormalize(text){
  return String(text || '').toLowerCase().replace(/[^a-z0-9 ]/g,' ').replace(/\s+/g,' ').trim();
}

function assistantAnswerFor(question){
  const normalized = assistantNormalize(question);
  if(!normalized) return '';
  let best = null;
  let bestScore = 0;
  ASSISTANT_FAQS.forEach(item => {
    let score = 0;
    const q = assistantNormalize(item.question);
    if(q && normalized.includes(q)) score += 8;
    item.keywords.forEach(keyword => {
      const key = assistantNormalize(keyword);
      if(key && normalized.includes(key)) score += key.includes(' ') ? 3 : 1;
    });
    if(score > bestScore){ bestScore = score; best = item; }
  });
  return bestScore > 0 && best ? best.answer : "I'm not sure about that yet. Would you like to contact support?";
}

function renderAssistantFaqs(){
  const list = document.getElementById('assistantFaqList');
  if(!list) return;
  list.innerHTML = ASSISTANT_FAQS.map(item => `
    <div class="assistant-faq-item">
      <div class="assistant-faq-q">${escapeHtml(item.question)}</div>
      <div class="assistant-faq-a">${escapeHtml(item.answer)}</div>
    </div>
  `).join('');
}

function assistantTestQuestion(){
  const input = document.getElementById('assistantTestInput');
  const response = document.getElementById('assistantResponse');
  if(!input || !response) return;
  const question = input.value.trim();
  response.textContent = question ? assistantAnswerFor(question) : 'Type a question first.';
}

function assistantFillQuestion(question){
  const input = document.getElementById('assistantTestInput');
  if(input) input.value = question;
  assistantTestQuestion();
}

function sShortText(value,max=72){
  const text = String(value || '').trim();
  if(!text) return '';
  return text.length > max ? (text.substring(0, max).trimEnd() + '...') : text;
}

function escHtml(s){
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function sEscapeAttr(value){
  return encodeURIComponent(String(value || ''));
}

function sReadAttr(encoded){
  try{ return decodeURIComponent(String(encoded || '')); }
  catch(_){ return String(encoded || ''); }
}

function sGetReactionDisplay(reaction){
  const token = String(reaction || '').trim();
  if(!token) return '';
  return S_REACTION_DISPLAY_MAP[token] || token;
}

function sGetReactionToken(entry){
  return typeof entry === 'string' ? entry : String(entry?.emoji || '').trim();
}

function sGetReactionActorRole(){
  return sGetCurrentSupportRole();
}

function sGetReactionActorKey(){
  return window.currentUserProfile?.uid
    ? `staff_${window.currentUserProfile.uid}`
    : `staff_${sGetReactionActorRole()}`;
}

function sGetMyReactionToken(msg){
  const reactions = msg?.reactions || {};
  const actorKey = sGetReactionActorKey();
  const directToken = sGetReactionToken(reactions[actorKey]);
  if(directToken) return directToken;
  const matched = Object.entries(reactions).find(([key, val]) => key === actorKey || val?.by === actorKey);
  return sGetReactionToken(matched?.[1]);
}

function sGetMessageRole(msg){
  const direct = sNormalizeRole(msg?.senderRole || msg?.role || msg?.senderType);
  if(direct) return direct;
  const senderRaw = String(msg?.sender || '').trim().toLowerCase();
  const senderNameRaw = String(msg?.senderName || msg?.name || '').trim().toLowerCase();
  const senderEmailRaw = String(msg?.senderEmail || msg?.email || '').trim().toLowerCase();
  const senderDisplayRaw = String(msg?.displayName || msg?.username || '').trim().toLowerCase();
  const senderIdRaw = String(msg?.senderId || '').trim().toLowerCase();
  const senderMetaRaw = [senderRaw, senderNameRaw, senderEmailRaw, senderDisplayRaw, senderIdRaw].filter(Boolean).join(' ');
  const textRaw = String(msg?.text || '').trim().toLowerCase();
  if(senderRaw === 'superadmin' || senderRaw === 'super admin') return 'superadmin';
  if(senderRaw === 'moderator' || senderRaw === 'mod') return 'moderator';
  if(senderRaw === 'admin') return 'admin';
  if(senderRaw === 'support') return 'support';
  if(/\b(system|community|bot|announcement)\b/.test(senderMetaRaw)) return 'system';
  if(msg?.system === true || msg?.isSystem === true) return 'system';
  if(
    textRaw.includes('start a conversation with another user') ||
    textRaw.includes('you are now in user-to-user chat') ||
    textRaw.includes('you are now connected to admin/moderator/super admin team chat')
  ) return 'system';
  return 'user';
}

function sGetSenderName(msg){
  const role = sGetMessageRole(msg);
  if(role === 'system') return 'System';
  const known = msg?.senderName || msg?.name || msg?.senderEmail || '';
  if(known) return String(known);
  return sRoleLabel(role);
}

function sGetReplyMeta(msg, msgs){
  if(!msg?.replyTo && !msg?.replyText && !msg?.replyToText) return null;
  const quoted = msg?.replyTo ? msgs.find(item => item.key === msg.replyTo) : null;
  const replyText = quoted && !quoted.deleted
    ? String(quoted.text || '').trim()
    : String(msg?.replyText || msg?.replyToText || '').trim();
  if(!replyText) return null;
  const replySenderName = quoted
    ? sGetSenderName(quoted)
    : String(msg?.replySenderName || msg?.replyToSenderName || '').trim();
  return {
    text: replyText,
    senderName: replySenderName
  };
}

function sResolveConversationType(chatData,msgs,fallbackType='support'){
  const raw = String(chatData?.chatType || chatData?.type || chatData?.conversationType || '').trim().toLowerCase();
  if(raw === 'user-user' || raw === 'user_to_user' || raw === 'peer') return 'user-user';
  if(raw === 'admin-team' || raw === 'admin_team' || raw === 'team-admin' || raw === 'admin') return 'admin-team';
  if(raw === 'support' || raw === 'user-support' || raw === 'support-chat') return 'support';
  const seenRoles = new Set(msgs.map(sGetMessageRole));
  const hasSupport = Array.from(seenRoles).some(sIsSupportRole);
  if(hasSupport) return 'support';
  const uniqueSenders = new Set(msgs.map(m => String(m.senderId || m.sender || '').trim()).filter(Boolean));
  if(uniqueSenders.size >= 2) return 'user-user';
  return fallbackType || 'support';
}

function sBuildConversation(uid, chatData, sourcePath='supportChats', sourceType='support', sourceLabel='Support'){
  const messages = chatData?.messages
    ? Object.entries(chatData.messages).map(([key, val]) => ({ key, ...(val || {}) })).sort((a,b)=>(a.time||0)-(b.time||0))
    : [];
  const last = messages.at(-1) || null;
  const type = sResolveConversationType(chatData, messages, sourceType);
  const displayName = chatData?.displayName || chatData?.name || chatData?.email || uid;
  const email = chatData?.email || uid;
  const preview = !last
    ? 'No messages yet'
    : (last.deleted ? 'Message deleted' : `${sGetSenderName(last)}: ${sShortText(last.text || '', 54)}`);
  const hasUnread = messages.some(m => !sIsSupportRole(sGetMessageRole(m)) && !m.read);
  return {
    uid,
    email,
    displayName,
    type,
    messages,
    last,
    preview,
    timeLabel: last ? sFormatTime(last.time) : '',
    hasUnread,
    sourcePath,
    sourceLabel,
    conversationKey: `${sourcePath}:${uid}`
  };
}

function sRenderStoryRow(list){
  const row = document.getElementById('s-story-row');
  if(!row) return;
  row.innerHTML = '';
  const top = list.slice(0, 10);
  if(!top.length){
    row.innerHTML = '<div class="s-story-empty">No active conversations.</div>';
    return;
  }
  top.forEach(item => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 's-story-item' + (selectedConversationKey === item.conversationKey ? ' active' : '');
    btn.innerHTML = `${sAvaHtml(item.displayName,36,true)}<span>${escHtml(sShortText(item.displayName, 10))}</span>`;
    btn.onclick = () => openChat(item.uid, item.email, item.type, item.sourcePath);
    row.appendChild(btn);
  });
}

function loadUsers(){
  if(sSourceDetachers.length){
    sSourceDetachers.forEach(detach => { if(typeof detach === 'function') detach(); });
    sSourceDetachers = [];
  }
  sSourceBuckets = {};
  S_CHAT_SOURCES.forEach(source => {
    const sourceRef = window.firebaseRef(window.firebaseDb, source.path);
    const detach = window.firebaseOnValue(sourceRef, (snapshot) => {
      sSourceBuckets[source.path] = snapshot.val() || {};
      sRebuildConversationList();
    });
    if(typeof detach === 'function') sSourceDetachers.push(detach);
  });
}

function sRebuildConversationList(){
  const ul = document.getElementById('userList');
  if(!ul) return;

  const combined = [];
  S_CHAT_SOURCES.forEach(source => {
    const bucket = sSourceBuckets[source.path] || {};
    Object.entries(bucket).forEach(([uid, raw]) => {
      const chatData = raw || {};
      const normalized = {
        ...chatData,
        chatType: chatData.chatType || source.defaultType
      };
      combined.push(sBuildConversation(uid, normalized, source.path, source.defaultType, source.label));
    });
  });

  sAllUsers = combined.sort((a,b) => (b.last?.time || 0) - (a.last?.time || 0));
  document.getElementById('s-user-count').textContent = String(sAllUsers.length);

  if(!sAllUsers.length){
    ul.innerHTML = '<div class="s-empty-list">No conversations yet.</div>';
    sRenderStoryRow([]);
    sResetChatPanel();
    sRefreshRoleBadges();
    return;
  }

  if(selectedConversationKey && !sAllUsers.some(u => u.conversationKey === selectedConversationKey)){
    sResetChatPanel();
  }

  sApplyUserFilters();
  sRefreshRoleBadges();
}

function sRenderUserList(list){
  const ul = document.getElementById('userList');
  if(!ul) return;
  ul.innerHTML = '';
  if(!list.length){
    ul.innerHTML = '<div class="s-empty-list">No conversations match your filters.</div>';
    return;
  }
  list.forEach(u => {
    const div = document.createElement('div');
    div.className = 's-user-item' + (selectedConversationKey===u.conversationKey ? ' s-active' : '') + (u.hasUnread ? ' s-unread' : '');
    div.dataset.userId = u.uid;
    div.dataset.conversationKey = u.conversationKey;
    div.innerHTML =
      `${sAvaHtml(u.displayName)}` +
      `<div class="s-user-meta">` +
        `<div class="s-user-name-row">` +
          `<div class="s-user-name">${escHtml(u.displayName)}</div>` +
          `<span class="s-user-type-pill">${sTypeLabel(u.type)}</span>` +
        `</div>` +
        `<div class="s-user-preview">${escHtml(u.preview)}</div>` +
      `</div>` +
      `<div class="s-user-time">${u.timeLabel}</div>`;
    div.onclick = () => openChat(u.uid, u.email, u.type, u.sourcePath);
    ul.appendChild(div);
  });
  sRenderStoryRow(list);
}

function sFilterUsers(q){
  sApplyUserFilters();
}

function sSetTypeFilter(type){
  sTypeFilter = type;
  document.querySelectorAll('#s-chat-type-tabs .s-tab-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.type === type);
  });
  sApplyUserFilters();
}

function sApplyUserFilters(){
  const q = (document.getElementById('s-search-input')?.value || '').trim().toLowerCase();
  let filtered = sAllUsers.filter(u => {
    if(!q) return true;
    return (
      String(u.displayName || '').toLowerCase().includes(q) ||
      String(u.email || '').toLowerCase().includes(q) ||
      String(u.preview || '').toLowerCase().includes(q)
    );
  });
  if(sTypeFilter !== 'all'){
    filtered = filtered.filter(u => u.type === sTypeFilter);
  }
  sRenderUserList(filtered);
}

function sRefreshRoleBadges(){
  const roleLabel = sRoleLabel(sGetCurrentSupportRole());
  const indicator = document.getElementById('s-role-indicator');
  const agentRole = document.getElementById('s-agent-role');
  if(indicator) indicator.textContent = `Role: ${roleLabel}`;
  if(agentRole) agentRole.textContent = roleLabel;
}

function sUpdateComposerState(){
  const input = document.getElementById('adminMessage');
  const sendBtn = document.getElementById('s-send-btn');
  const canReplyByRole = sCanReplyToSupport();
  if(input){
    input.disabled = !canReplyByRole;
    if(!canReplyByRole){
      input.placeholder = 'Read only. Admin, moderator, or super admin can reply.';
    } else if(!selectedUserId){
      input.placeholder = 'Select a conversation first...';
    } else {
      input.placeholder = `Reply as ${sRoleLabel(sGetCurrentSupportRole())}...`;
    }
  }
  if(sendBtn){
    sendBtn.disabled = !selectedUserId || !canReplyByRole;
  }
}

function sResetChatPanel(){
  sClearLocalTypingState();
  selectedUserId = null;
  selectedUserEmail = '';
  selectedConversationType = 'support';
  selectedChatSource = 'supportChats';
  selectedConversationKey = '';
  sCurrentMessages = [];
  if(typeof detachChatListener === 'function'){ detachChatListener(); detachChatListener = null; }
  if(typeof detachTypingListener === 'function'){ detachTypingListener(); detachTypingListener = null; }
  sTypingEntries = [];
  const headerAva = document.getElementById('s-header-ava');
  const headerName = document.getElementById('s-header-name');
  const headerStatus = document.getElementById('s-header-status');
  const headerType = document.getElementById('s-header-chat-type');
  const chatBox = document.getElementById('chatMessages');
  if(headerAva) headerAva.innerHTML = '';
  if(headerName) headerName.textContent = 'Select a conversation';
  if(headerStatus) headerStatus.textContent = 'Waiting for selection';
  if(headerType) headerType.textContent = '--';
  if(chatBox){
    chatBox.innerHTML =
      '<div class="s-empty-state">' +
        '<div class="s-empty-icon">Chat</div>' +
        '<div class="s-empty-label">No conversation selected</div>' +
      '</div>';
  }
  sClearReply();
  sCloseEmojiPicker();
  sCloseMsgMenu();
  sHideTypingIndicator();
  sUpdateComposerState();
}

function openChat(userId, email, forcedType, sourcePath = 'supportChats') {
  sClearLocalTypingState();
  const lookupKey = `${sourcePath}:${userId}`;
  const conversation = sAllUsers.find(item => item.conversationKey === lookupKey) ||
    sAllUsers.find(item => item.uid === userId && item.sourcePath === sourcePath);
  selectedUserId = userId;
  selectedUserEmail = conversation?.email || email || userId;
  selectedConversationType = forcedType || conversation?.type || 'support';
  selectedChatSource = conversation?.sourcePath || sourcePath || 'supportChats';
  selectedConversationKey = conversation?.conversationKey || lookupKey;
  document.getElementById('s-header-ava').innerHTML = sAvaHtml(conversation?.displayName || selectedUserEmail, 30);
  document.getElementById('s-header-name').textContent = conversation?.displayName || selectedUserEmail;
  document.getElementById('s-header-status').textContent = 'Loading messages...';
  document.getElementById('s-header-chat-type').textContent = sTypeLabel(selectedConversationType);
  document.getElementById('s-header-status').style.color = '';
  sUpdateComposerState();
  document.querySelectorAll('#userList .s-user-item').forEach(row => {
    row.classList.toggle('s-active', row.dataset.conversationKey === selectedConversationKey);
    row.classList.remove('s-unread');
  });
  if (typeof detachChatListener === 'function') { detachChatListener(); detachChatListener = null; }
  if (typeof detachTypingListener === 'function') { detachTypingListener(); detachTypingListener = null; }

  const chatRef = window.firebaseRef(window.firebaseDb, `${selectedChatSource}/${userId}/messages`);
  detachChatListener = window.firebaseOnValue(chatRef, (snapshot) => {
    const data = snapshot.val() || {};
    const box = document.getElementById('chatMessages');
    const msgs = Object.entries(data)
      .map(([key, val]) => ({ key, ...(val || {}) }))
      .sort((a, b) => (a.time || 0) - (b.time || 0));
    sCurrentMessages = msgs;

    selectedConversationType = conversation?.type || sResolveConversationType({type:selectedConversationType}, msgs);
    document.getElementById('s-header-chat-type').textContent = sTypeLabel(selectedConversationType);
    document.getElementById('s-header-status').textContent = msgs.length ? `${msgs.length} messages` : 'No messages yet';

    box.innerHTML = '';
    if (!msgs.length) {
      box.innerHTML =
        '<div class="s-empty-state">' +
          '<div class="s-empty-icon">Chat</div>' +
          '<div class="s-empty-label">No messages in this conversation yet</div>' +
        '</div>';
      return;
    }

    let lastDateLabel = '';
    msgs.forEach(msg => {
      const dateLabel = sFormatDateLabel(msg.time);
      if(dateLabel !== lastDateLabel){
        const divider = document.createElement('div');
        divider.className = 's-date-divider';
        divider.textContent = dateLabel;
        box.appendChild(divider);
        lastDateLabel = dateLabel;
      }

      const role = sGetMessageRole(msg);
      const isSystem = role === 'system';
      const isOut = !isSystem && sIsSupportRole(role);
      const senderName = sGetSenderName(msg);
      const senderColor = sGetColor(senderName);
      const time = sFormatTime(msg.time);
      const roleLabel = sRoleLabel(role);
      const roleClass = sRoleClass(role);

      const row = document.createElement('div');
      row.className = 's-msg-row ' + (isSystem ? 's-system' : (isOut ? 's-out' : 's-in'));
      row.dataset.msgKey = msg.key;
      row.dataset.msgText = msg.text || '';

      const replyMeta = sGetReplyMeta(msg, msgs);
      const quoteHtml = replyMeta
        ? `<div class="s-reply-quote"><div class="s-reply-quote-label">${escHtml(replyMeta.senderName ? `Reply to ${replyMeta.senderName}` : 'Reply')}</div><div class="s-reply-quote-text">${escHtml(sShortText(replyMeta.text, 70))}</div></div>`
        : '';

      const reactionValues = msg.reactions ? Object.values(msg.reactions) : [];
      const reactionMap = {};
      const myReactionToken = sGetMyReactionToken(msg);
      reactionValues.forEach(val => {
        const token = sGetReactionToken(val);
        if(!token) return;
        reactionMap[token] = (reactionMap[token] || 0) + 1;
      });
      const reactionHtml = Object.keys(reactionMap).length
        ? `<div class="s-reaction-bar">${Object.entries(reactionMap).map(([reactionKey, count]) =>
            `<div class="s-reaction${reactionKey === myReactionToken ? ' active' : ''}" onclick="sToggleReaction('${msg.key}','${sEscapeAttr(reactionKey)}',true)">${escHtml(sGetReactionDisplay(reactionKey))}<span class="s-reaction-count">${count}</span></div>`
          ).join('')}</div>`
        : '';

      const encodedReplyText = sEscapeAttr(msg.text || '');
      const actionsHtml = !isSystem && !msg.deleted ? `
        <div class="s-msg-actions">
          <button type="button" class="s-msg-menu-btn" title="More actions" aria-label="More actions" onclick="sShowMsgMenu(event,'${userId}','${msg.key}','${encodedReplyText}')">&#8942;</button>
        </div>` : '';

      const bubbleContent = msg.deleted
        ? '<span class="s-deleted-msg">Message deleted</span>'
        : escHtml(msg.text || '');
      const metaHtml =
        `<div class="s-msg-meta">` +
          `<span class="s-msg-sender">${escHtml(senderName)}</span>` +
          `${role === 'user' ? '' : `<span class="s-msg-role ${roleClass}">${roleLabel}</span>`}` +
        `</div>`;

      if (isSystem) {
        row.innerHTML =
          `<div class="s-bubble-wrap">` +
            `<div class="s-bubble">${bubbleContent}</div>` +
            `<div class="s-msg-time">${time}</div>` +
          `</div>`;
      } else if (!isOut) {
        row.innerHTML =
          `<div class="s-msg-ava" style="background:${senderColor.bg};color:${senderColor.fg}">${sInitials(senderName)}</div>` +
          `<div class="s-msg-main">` +
            `${metaHtml}` +
            `${quoteHtml}` +
            `<div class="s-msg-content-row">` +
              `<div class="s-bubble">${bubbleContent}</div>` +
              `${actionsHtml}` +
            `</div>` +
            `${reactionHtml}` +
            `<div class="s-msg-footer"><div class="s-msg-time">${time}</div></div>` +
          `</div>`;
      } else {
        row.innerHTML =
          `<div class="s-msg-main">` +
            `${metaHtml}` +
            `${quoteHtml}` +
            `<div class="s-msg-content-row">` +
              `${actionsHtml}` +
              `<div class="s-bubble">${bubbleContent}</div>` +
            `</div>` +
            `${reactionHtml}` +
            `<div class="s-msg-footer"><div class="s-msg-time">${time}</div></div>` +
          `</div>`;
      }
      box.appendChild(row);
    });

    box.scrollTop = box.scrollHeight;
    sCloseEmojiPicker();
    sCloseMsgMenu();
  });

  const typingRef = window.firebaseRef(window.firebaseDb, `${selectedChatSource}/${userId}/typing`);
  detachTypingListener = window.firebaseOnValue(typingRef, (snapshot) => {
    const raw = snapshot.val() || {};
    sTypingEntries = Object.entries(raw).map(([key, value]) => ({
      key,
      ...(value || {})
    }));
    sRenderTypingIndicator();
  });
}

function handleSend(){sSendAdminMessage();}

function sSetReply(key, text, encoded = false) {
  const decoded = encoded ? sReadAttr(text) : String(text || '');
  const source = sCurrentMessages.find(msg => msg.key === key) || {};
  sReplyToKey = key;
  sReplyToText = decoded;
  sReplyToSenderName = sGetSenderName(source);
  sReplyToSenderRole = sGetMessageRole(source);
  let preview = document.getElementById('s-reply-preview');
  if (!preview) {
    preview = document.createElement('div');
    preview.id = 's-reply-preview';
    preview.className = 's-reply-preview';
    const inputBar = document.querySelector('.s-input-bar');
    if(!inputBar) return;
    inputBar.insertBefore(preview, inputBar.firstChild);
  }
  preview.innerHTML =
    `<div class="s-reply-preview-copy">` +
      `<div class="s-reply-preview-label">${escHtml(sReplyToSenderName ? `Replying to ${sShortText(sReplyToSenderName, 40)}` : 'Replying to message')}</div>` +
      `<div class="s-reply-preview-text">${escHtml(sShortText(decoded, 80))}</div>` +
    `</div>` +
    `<button class="s-reply-preview-close" onclick="sClearReply()">x</button>`;
  document.getElementById('adminMessage').focus();
}

function sClearReply() {
  sReplyToKey = null;
  sReplyToText = '';
  sReplyToSenderName = '';
  sReplyToSenderRole = '';
  const preview = document.getElementById('s-reply-preview');
  if (preview) preview.remove();
}

async function sDeleteMsg(userId, msgKey) {
  if (!confirm('Delete this message?')) return;
  try {
    const root = selectedChatSource || 'supportChats';
    const original = sCurrentMessages.find(msg => msg.key === msgKey) || {};
    const { key, ...persisted } = original;
    const originalRole = sGetMessageRole(original);
    const senderName = persisted.senderName || persisted.name || persisted.senderEmail || sGetSenderName(original);
    const ref = window.firebaseRef(window.firebaseDb, `${root}/${userId}/messages/${msgKey}`);
    await window.firebaseSet(ref, {
      ...persisted,
      sender: persisted.sender || (sIsSupportRole(originalRole) ? 'admin' : 'user'),
      senderRole: persisted.senderRole || persisted.role || originalRole,
      senderName,
      senderId: persisted.senderId || '',
      deleted: true,
      deletedAt: Date.now(),
      time: persisted.time || Date.now(),
      text: ''
    });
    sCloseMsgMenu();
    showToast('Message deleted');
  } catch (err) {
    console.error('Delete msg error:', err);
    showToast('Failed to delete message');
  }
}

function sOpenEmojiPicker(anchorEl, msgKey) {
  sCloseEmojiPicker();
  sCloseMsgMenu();
  sEmojiPickerTarget = msgKey;
  const message = sCurrentMessages.find(msg => msg.key === msgKey) || null;
  const activeReaction = sGetMyReactionToken(message);

  const picker = document.createElement('div');
  picker.className = 's-emoji-picker';
  picker.id = 's-emoji-picker';
  picker.style.position = 'fixed';
  picker.style.zIndex = '9999';

  S_QUICK_REACTIONS.forEach(emoji => {
    const btn = document.createElement('button');
    btn.textContent = sGetReactionDisplay(emoji);
    btn.title = emoji;
    btn.setAttribute('aria-label', `React with ${sGetReactionDisplay(emoji)}`);
    if (emoji === activeReaction) btn.classList.add('active');
    btn.onclick = (e) => { e.stopPropagation(); sToggleReaction(msgKey, emoji); sCloseEmojiPicker(); };
    picker.appendChild(btn);
  });

  document.body.appendChild(picker);

  // Position AFTER appending so offsetHeight is known
  const rect = anchorEl.getBoundingClientRect();
  const pickerH = picker.offsetHeight || 80;
  const pickerW = picker.offsetWidth || 180;
  const top = rect.top - pickerH - 8;
  const left = Math.min(rect.left, window.innerWidth - pickerW - 8);
  picker.style.top = Math.max(8, top) + 'px';
  picker.style.left = Math.max(8, left) + 'px';

  setTimeout(() => document.addEventListener('click', sCloseEmojiPicker, { once: true }), 0);
}

function sShowEmojiPicker(event, msgKey) {
  event.stopPropagation();
  sOpenEmojiPicker(event.currentTarget || event.target, msgKey);
}

function sCloseEmojiPicker() {
  const p = document.getElementById('s-emoji-picker');
  if (p) p.remove();
}

function sShowMsgMenu(event, userId, msgKey, encodedReplyText) {
  event.stopPropagation();
  const anchor = event.currentTarget || event.target;
  const existing = document.getElementById('s-msg-menu');
  if (existing && existing.dataset.msgKey === msgKey) {
    sCloseMsgMenu();
    return;
  }

  sCloseMsgMenu();
  sCloseEmojiPicker();

  const menu = document.createElement('div');
  menu.className = 's-msg-menu';
  menu.id = 's-msg-menu';
  menu.dataset.msgKey = msgKey;

  const reactBtn = document.createElement('button');
  reactBtn.type = 'button';
  reactBtn.textContent = 'React';
  reactBtn.onclick = (e) => {
    e.stopPropagation();
    sCloseMsgMenu();
    sOpenEmojiPicker(anchor, msgKey);
  };
  menu.appendChild(reactBtn);

  const replyBtn = document.createElement('button');
  replyBtn.type = 'button';
  replyBtn.textContent = 'Reply';
  replyBtn.onclick = (e) => {
    e.stopPropagation();
    sCloseMsgMenu();
    sSetReply(msgKey, encodedReplyText, true);
  };
  menu.appendChild(replyBtn);

  const deleteBtn = document.createElement('button');
  deleteBtn.type = 'button';
  deleteBtn.className = 'danger';
  deleteBtn.textContent = 'Delete';
  deleteBtn.onclick = (e) => {
    e.stopPropagation();
    sCloseMsgMenu();
    sDeleteMsg(userId, msgKey);
  };
  menu.appendChild(deleteBtn);

  document.body.appendChild(menu);

  const rect = anchor.getBoundingClientRect();
  const menuH = menu.offsetHeight || 124;
  const menuW = menu.offsetWidth || 144;
  const roomBelow = window.innerHeight - rect.bottom;
  const top = roomBelow >= menuH + 8 ? rect.bottom + 6 : rect.top - menuH - 6;
  const left = Math.min(rect.right - menuW, window.innerWidth - menuW - 8);
  menu.style.top = Math.max(8, top) + 'px';
  menu.style.left = Math.max(8, left) + 'px';

  setTimeout(() => document.addEventListener('click', sCloseMsgMenu, { once: true }), 0);
}

function sCloseMsgMenu() {
  const menu = document.getElementById('s-msg-menu');
  if (menu) menu.remove();
}

function sGetTypingActorKey(){
  return window.currentUserProfile?.uid || window.firebaseAuth?.currentUser?.uid || 'admin-web';
}

function sGetTypingActorName(){
  return window.currentUserProfile?.username || window.currentUserProfile?.email || sRoleLabel(sGetCurrentSupportRole());
}

function sWriteTypingState(isTyping){
  if(!selectedUserId || !window.firebaseDb) return Promise.resolve();
  const root = selectedChatSource || 'supportChats';
  const actorKey = sGetTypingActorKey();
  if(!actorKey) return Promise.resolve();
  const typingRef = window.firebaseRef(window.firebaseDb, `${root}/${selectedUserId}/typing/${actorKey}`);
  const payload = {
    userId: actorKey,
    userName: sGetTypingActorName(),
    role: sGetCurrentSupportRole(),
    isTyping: !!isTyping,
    updatedAt: Date.now()
  };
  sTypingPresenceActive = !!isTyping;
  return window.firebaseSet(typingRef, payload).catch(err => {
    console.error('Typing state update failed:', err);
  });
}

function sClearLocalTypingState(){
  if(sTypingStopTimer){
    clearTimeout(sTypingStopTimer);
    sTypingStopTimer = null;
  }
  if(!sTypingPresenceActive) return;
  sWriteTypingState(false);
}

function sHandleTypingInput(){
  const input = document.getElementById('adminMessage');
  if(!input || input.disabled || !sCanReplyToSupport()){
    sClearLocalTypingState();
    return;
  }
  const text = String(input.value || '').trim();
  if(!text){
    sClearLocalTypingState();
    return;
  }
  if(!sTypingPresenceActive){
    sWriteTypingState(true);
  }
  if(sTypingStopTimer){
    clearTimeout(sTypingStopTimer);
  }
  sTypingStopTimer = setTimeout(() => {
    sWriteTypingState(false);
    sTypingStopTimer = null;
  }, S_TYPING_IDLE_MS);
}

function sHideTypingIndicator(){
  const row = document.getElementById('s-typing-row');
  if(row) row.classList.remove('show');
}

function sRenderTypingIndicator(){
  const row = document.getElementById('s-typing-row');
  const label = document.getElementById('s-typing-label');
  if(!row || !label){
    return;
  }
  const actorKey = sGetTypingActorKey();
  const now = Date.now();
  const active = sTypingEntries
    .filter(entry =>
      entry &&
      entry.userId &&
      entry.userId !== actorKey &&
      entry.isTyping === true &&
      Number(now - Number(entry.updatedAt || 0)) <= S_TYPING_STALE_MS
    )
    .sort((a, b) => Number(b.updatedAt || 0) - Number(a.updatedAt || 0))[0];

  if(!active){
    row.classList.remove('show');
    return;
  }

  label.textContent = `${active.userName || 'Someone'} is typing...`;
  row.classList.add('show');
}

async function sToggleReaction(msgKey, emoji, encoded = false) {
  if (!selectedUserId) return;
  try {
    const token = encoded ? sReadAttr(emoji) : emoji;
    const actorRole = sGetReactionActorRole();
    const actorKey = sGetReactionActorKey();
    const root = selectedChatSource || 'supportChats';
    const reactionRef = window.firebaseRef(window.firebaseDb, `${root}/${selectedUserId}/messages/${msgKey}/reactions/${actorKey}`);
    const message = sCurrentMessages.find(msg => msg.key === msgKey) || null;
    const existingToken = sGetMyReactionToken(message);
    if (existingToken && existingToken === token) {
      await window.firebaseRemove(reactionRef);
      return;
    }
    await window.firebaseSet(reactionRef, { emoji: token, by: actorKey, role: actorRole, time: Date.now() });
  } catch (err) {
    console.error('Reaction error:', err);
  }
}

function sSendAdminMessage() {
  const input = document.getElementById('adminMessage');
  const text = (input.value || '').trim();
  if (!selectedUserId) { showToast('Select a conversation first.'); return; }
  if (!sCanReplyToSupport()) { showToast('Only admin, moderator, or super admin can reply.'); return; }
  if (!text) return;
  const role = sGetCurrentSupportRole();
  const senderName = window.currentUserProfile?.username || window.currentUserProfile?.email || sRoleLabel(role);
  const root = selectedChatSource || 'supportChats';
  const msgRef = window.firebaseRef(window.firebaseDb, `${root}/${selectedUserId}/messages`);
  const newMsg = window.firebasePush(msgRef);
  const payload = {
    text,
    sender: 'admin',
    senderRole: role,
    senderName,
    senderId: window.currentUserProfile?.uid || 'admin-web',
    chatType: selectedConversationType,
    time: Date.now(),
    read: false
  };
  if (sReplyToKey) {
    payload.replyTo = sReplyToKey;
    payload.replyText = sReplyToText;
    payload.replySenderName = sReplyToSenderName;
    payload.replySenderRole = sReplyToSenderRole;
  }
  const conversationRef = window.firebaseRef(window.firebaseDb, `${root}/${selectedUserId}`);
  sClearLocalTypingState();
  window.firebaseSet(newMsg, payload)
    .then(() => window.firebaseUpdate(conversationRef, {
      updatedAt: payload.time,
      lastMessage: payload
    }))
    .then(() => { input.value = ''; input.focus(); sClearReply(); sUpdateComposerState(); })
    .catch(err => { console.error('Support send failed:', err); showToast('Failed to send message.'); });
}

sSetTypeFilter('all');
sRefreshRoleBadges();
sUpdateComposerState();
renderAssistantFaqs();
window.setInterval(() => sRenderTypingIndicator(), 1000);
window.addEventListener('beforeunload', () => sClearLocalTypingState());

function diagnoseCampaigns(){
  console.group('🔧 Firebase Campaign Diagnostics');
  console.log('\n👤 USER AUTHENTICATION:');
  console.log('• window.firebaseAuth.currentUser:', window.firebaseAuth?.currentUser?.uid || 'null');
  console.log('• window.currentUserProfile:', window.currentUserProfile);
  console.log('• currentRole():', currentRole());
  console.log('• canManageCampaigns():', canManageCampaigns());
  
  console.log('\n📚 CAMPAIGNS DATA:');
  console.log('• campaigns array:', campaigns);
  console.log('• campaigns.length:', campaigns.length);
  console.log('• donationStats:', donationStats);
  
  console.log('\n🔥 FIREBASE FUNCTIONS:');
  console.log('• window.firebaseDb:', !!window.firebaseDb);
  console.log('• window.firebaseCampaignsRef:', !!window.firebaseCampaignsRef);
  console.log('• window.firebaseOnValue:', !!window.firebaseOnValue);
  console.log('• window.firebaseSet:', !!window.firebaseSet);
  console.log('• window.firebasePush:', !!window.firebasePush);
  console.log('• window.firebaseRef:', !!window.firebaseRef);
  
  console.log('\n🎨 DOM CONTAINERS:');
  console.log('• #campaigns-empty:', !!document.getElementById('campaigns-empty'));
  console.log('• #campaigns-list:', !!document.getElementById('campaigns-list'));
  console.log('• #campaigns-table-body:', !!document.getElementById('campaigns-table-body'));
  console.log('• #modal-campaign:', !!document.getElementById('modal-campaign'));
  console.log('• #campaign-save-btn:', !!document.getElementById('campaign-save-btn'));
  
  // Try to manually fetch campaigns data for debugging
  if(window.firebaseCampaignsRef && window.firebaseOnValue){
    console.log('\n📡 Attempting Firebase REST API fetch...');
    const dbUrl = 'https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app/campaigns.json';
    fetch(dbUrl).then(r => {
      console.log('REST API response status:', r.status);
      return r.json();
    }).then(data => {
      console.log('✓ Firebase REST API campaigns data:', data);
    }).catch(e => console.error('❌ REST API fetch failed:', e));
  }
  
  console.log('\n📊 SUMMARY:');
  if(!window.currentUserProfile){
    console.warn('⚠️ User profile not loaded yet. Campaigns may not display.');
  }
  if(campaigns.length === 0){
    console.warn('⚠️ No campaigns found. Check Firebase Realtime Database > campaigns node.');
  }
  if(!canManageCampaigns()){
    console.log('ℹ️ Current user is ' + currentRole() + ' - cannot create campaigns. Only Super Admin/Admin/Moderator can manage.');
  }
  console.groupEnd();
}

// 🔄 Manual refresh - call from console if needed
function refreshCampaigns(){
  console.log('🔄 Manually refreshing campaigns...');
  watchCampaigns();
}

// 🎨 Manual render - call from console if needed
function refreshRender(){
  console.log('🎨 Manually re-rendering campaigns...');
  renderCampaigns();
}
let checks=[true,false,false,false,false];
let campaigns=[];
let teamMembers=[];
const campaignFeedSettingsDefaults = {
  allowUserPosts: true,
  allowGuestPosts: false,
  allowGuestReactions: true,
  allowGuestComments: true
};
let campaignFeedSettings = {...campaignFeedSettingsDefaults};
let campaignFeedSettingsExists = false;
let campaignFeedPosts = [];
 
function nav(id,el){
  document.querySelectorAll('.section').forEach(s=>s.classList.remove('active'));
  document.getElementById('s-'+id).classList.add('active');
  document.querySelectorAll('.nav-item').forEach(n=>n.classList.remove('active'));
  if(el)el.classList.add('active');
  document.getElementById('topbar-title').textContent=titles[id]||id;
}
 
function openModal(id){document.getElementById(id).classList.add('open')}
function closeModal(id){
  document.getElementById(id).classList.remove('open');
  if(id === 'modal-delete'){
    if(window.deleteTimer){
      clearInterval(window.deleteTimer);
      window.deleteTimer = null;
    }
    const btn = document.getElementById('delete-confirm-btn');
    btn.disabled = true;
    btn.textContent = 'Yes (5)';
  }
}
window.deleteCampaign = deleteCampaign;
window.confirmDelete = confirmDelete;
window.openModal = openModal;
window.closeModal = closeModal;
document.querySelectorAll('.modal-bg').forEach(m=>{
  m.addEventListener('click',e=>{if(e.target===m)m.classList.remove('open')});
});
 
function showToast(msg, type = 'success'){
  const t=document.getElementById('toast');
  const icon=t.querySelector('span');
  t.classList.remove('success','error');
  t.classList.add(type === 'error' ? 'error' : 'success');
  if(icon) icon.textContent = type === 'error' ? '!' : '✓';
  document.getElementById('toast-msg').textContent=msg;
  t.classList.add('show');
  setTimeout(()=>t.classList.remove('show'),3000);
}
 
function toggleCheck(i){
  if(i===0)return;
  checks[i]=!checks[i];
  setCheckState(i, checks[i] ? 'done' : false);
}

function setCheckState(i, state){
  const el=document.getElementById('chk-'+i);
  const circle=el.querySelector('.check-circle');
  const badge=el.querySelector('.check-badge');
  el.classList.toggle('done', state==='done');
  el.classList.toggle('failed', state==='failed');
  if(state==='done'){
    circle.textContent='✓';
    badge.textContent='Done ✓';
    checks[i]=true;
  } else if(state==='failed'){
    circle.textContent='✕';
    badge.textContent='Failed ✕';
    checks[i]=false;
  } else {
    circle.textContent='';
    badge.textContent='→ Start';
    checks[i]=false;
  }
  updateCheckProgress();
}
 
function updateCheckProgress(){
  const done=checks.filter(Boolean).length;
  const pct=(done/5)*100;
  document.getElementById('check-prog').style.width=pct+'%';
  document.getElementById('check-count').textContent=done+' of 5 done';
}
 
let editingCampaignKey = null;
let donations = [];
let donationStats = {};
const DASHBOARD_CHART_PLACEHOLDER = [24, 40, 31, 56, 44, 68];

function formatCurrency(value){
  return '\u20B1' + Number(value || 0).toLocaleString();
}

function setTextContent(selector, value){
  document.querySelectorAll(selector).forEach(el => {
    el.textContent = value;
  });
}

function getDonationDate(donation){
  const raw = donation?.createdAt || donation?.time || donation?.timestamp || 0;
  const numeric = Number(raw);
  const date = new Date(Number.isFinite(numeric) && numeric > 0 ? numeric : raw);
  return Number.isNaN(date.getTime()) ? null : date;
}

function getAccessibleCampaigns(){
  return canManageCampaigns() ? campaigns : campaigns.filter(c => !c.hidden);
}

function getActiveCampaignCount(){
  return getAccessibleCampaigns().filter(c => {
    const status = String(c?.status || 'Active').trim().toLowerCase();
    return !c?.hidden && status === 'active';
  }).length;
}

function getDonorKey(donation){
  const userId = String(donation?.userId || donation?.uid || donation?.donorId || '').trim().toLowerCase();
  if(userId) return 'user:' + userId;
  const email = String(donation?.donorEmail || donation?.email || '').trim().toLowerCase();
  if(email) return 'email:' + email;
  const name = String(donation?.donorName || donation?.name || '').trim().toLowerCase();
  if(name) return 'name:' + name;
  return donation?.id ? 'anon:' + donation.id : '';
}

function getDonorSummary(){
  const donorsByKey = new Map();
  const monthlyKeys = new Set();
  const now = new Date();

  donations.forEach(donation => {
    const key = getDonorKey(donation);
    if(!key) return;

    if(!donorsByKey.has(key)){
      donorsByKey.set(key, {
        isRegistered: Boolean(
          String(donation?.userId || donation?.uid || donation?.donorId || '').trim() ||
          donation?.isRegistered === true ||
          donation?.registered === true
        )
      });
    }

    const donatedAt = getDonationDate(donation);
    if(donatedAt && donatedAt.getMonth() === now.getMonth() && donatedAt.getFullYear() === now.getFullYear()){
      monthlyKeys.add(key);
    }
  });

  const total = donorsByKey.size;
  const registeredCount = Array.from(donorsByKey.values()).filter(donor => donor.isRegistered).length;

  return {
    total,
    registeredCount,
    guestCount: Math.max(total - registeredCount, 0),
    monthlyCount: monthlyKeys.size
  };
}

function hasAdditionalTeamMember(){
  const currentUid = window.currentUserProfile?.uid || window.firebaseAuth?.currentUser?.uid || '';
  if(!currentUid){
    return teamMembers.length > 1;
  }
  return teamMembers.some(member => member.uid !== currentUid);
}

function hasConnectedPaymentProvider(){
  return ['t-gcash','t-maya','t-stripe'].some(id => document.getElementById(id)?.classList.contains('on'));
}

function hasOrganizationDetails(){
  return Boolean(document.getElementById('org-name')?.value.trim());
}

function setSetupStepDone(dotId, done){
  const dot = document.getElementById(dotId);
  if(!dot) return;
  dot.classList.toggle('done', !!done);
  dot.classList.remove('active');
  const row = dot.parentElement;
  if(row){
    row.style.color = done ? 'var(--text)' : '';
  }
}

function updateDashboardGettingStartedAction(states){
  const button = document.querySelector('#s-dashboard .two-col .card:last-child button');
  if(!button) return;

  if(!states.campaign && canManageCampaigns()){
    button.textContent = '+ Create first campaign';
    button.onclick = () => openCampaignModal();
    return;
  }

  if(!states.payment && ['Super Admin','Admin'].includes(currentRole())){
    button.textContent = 'Configure payment providers';
    button.onclick = () => nav('settings', document.getElementById('nav-settings'));
    return;
  }

  if(!states.organization && ['Super Admin','Admin'].includes(currentRole())){
    button.textContent = 'Complete organization details';
    button.onclick = () => nav('settings', document.getElementById('nav-settings'));
    return;
  }

  if(!states.team && canInviteUsers()){
    button.textContent = '+ Invite team member';
    button.onclick = () => openModal('modal-invite');
    return;
  }

  button.textContent = 'View campaigns';
  button.onclick = () => nav('campaigns', document.getElementById('nav-campaigns'));
}

function syncChecklistFromData(){
  const states = {
    campaign: campaigns.length > 0,
    payment: hasConnectedPaymentProvider(),
    organization: hasOrganizationDetails(),
    team: hasAdditionalTeamMember()
  };

  [
    { index: 1, done: states.campaign },
    { index: 2, done: states.payment },
    { index: 3, done: states.organization },
    { index: 4, done: states.team }
  ].forEach(item => {
    const el = document.getElementById('chk-' + item.index);
    if(!el) return;
    const circle = el.querySelector('.check-circle');
    const badge = el.querySelector('.check-badge');
    if(badge && !el.dataset.defaultBadge){
      el.dataset.defaultBadge = badge.textContent;
    }
    el.classList.toggle('done', item.done);
    el.classList.remove('failed');
    if(circle) circle.textContent = item.done ? '\u2713' : '';
    if(badge) badge.textContent = item.done ? 'Done \u2713' : (el.dataset.defaultBadge || '\u2192 Start');
    checks[item.index] = item.done;
  });

  setSetupStepDone('dot-campaign', states.campaign);
  setSetupStepDone('dot-payment', states.payment);
  setSetupStepDone('dot-org', states.organization);
  setSetupStepDone('dot-team', states.team);
  updateCheckProgress();
  updateDashboardGettingStartedAction(states);
}

function buildDashboardDonationSeries(months = 6){
  const now = new Date();
  const series = [];
  const monthIndex = new Map();

  for(let offset = months - 1; offset >= 0; offset--){
    const monthDate = new Date(now.getFullYear(), now.getMonth() - offset, 1);
    const key = monthDate.getFullYear() + '-' + monthDate.getMonth();
    const item = {
      key,
      label: monthDate.toLocaleString('en-US', { month: 'short' }).toUpperCase(),
      title: monthDate.toLocaleString('en-US', { month: 'long', year: 'numeric' }),
      total: 0
    };
    monthIndex.set(key, item);
    series.push(item);
  }

  donations.forEach(donation => {
    const donatedAt = getDonationDate(donation);
    if(!donatedAt) return;
    const key = donatedAt.getFullYear() + '-' + donatedAt.getMonth();
    const bucket = monthIndex.get(key);
    if(!bucket) return;
    bucket.total += Number(donation.amount || 0);
  });

  return series;
}

function renderDashboardVolumeChart(series){
  const chart = document.getElementById('ghost-chart');
  if(!chart) return;

  chart.className = 'dashboard-volume-chart';
  const hasData = series.some(item => item.total > 0);
  const maxTotal = Math.max(...series.map(item => item.total), 1);

  chart.innerHTML = series.map((item, index) => {
    const height = hasData
      ? Math.max(12, Math.round((item.total / maxTotal) * 100))
      : DASHBOARD_CHART_PLACEHOLDER[index % DASHBOARD_CHART_PLACEHOLDER.length];
    const barClass = 'dashboard-volume-bar' + (hasData ? '' : ' placeholder');
    return `
      <div class="dashboard-volume-col" title="${item.title}: ${formatCurrency(item.total)}">
        <div class="dashboard-volume-bar-wrap">
          <div class="${barClass}" style="height:${height}%"></div>
        </div>
        <div class="dashboard-volume-label">${item.label}</div>
      </div>
    `;
  }).join('');
}

function renderDashboardOverview(){
  const totalRaised = donations.reduce((sum, donation) => sum + Number(donation.amount || 0), 0);
  const avgDonation = donations.length > 0 ? Math.round(totalRaised / donations.length) : 0;
  const activeCampaignCount = getActiveCampaignCount();
  const donorSummary = getDonorSummary();
  const donationLabel = donations.length === 1 ? 'donation' : 'donations';
  const monthLabel = donorSummary.monthlyCount === 1 ? 'donor' : 'donors';

  setTextContent('[data-stat="total-raised"]', formatCurrency(totalRaised));
  setTextContent('[data-stat="active-campaign-count"]', String(activeCampaignCount));
  setTextContent('[data-stat="total-donor-count"]', String(donorSummary.total));

  const dashboardAvgValue = document.querySelector('#s-dashboard .stats-grid .stat-card:nth-child(4) .stat-value');
  if(dashboardAvgValue){
    dashboardAvgValue.textContent = donations.length > 0 ? formatCurrency(avgDonation) : '\u2014';
  }

  const totalRaisedSub = document.getElementById('dashboard-total-raised-sub');
  if(totalRaisedSub){
    totalRaisedSub.textContent = donations.length > 0
      ? `${donations.length} ${donationLabel} received`
      : 'No donations yet';
  }

  const activeCampaignsSub = document.getElementById('dashboard-active-campaigns-sub');
  if(activeCampaignsSub){
    activeCampaignsSub.textContent = activeCampaignCount > 0
      ? `${activeCampaignCount} live campaign${activeCampaignCount === 1 ? '' : 's'} right now`
      : (campaigns.length > 0 ? 'No live campaigns right now' : 'Create your first one \u2192');
  }

  const totalDonorsSub = document.getElementById('dashboard-total-donors-sub');
  if(totalDonorsSub){
    totalDonorsSub.textContent = donorSummary.total > 0
      ? `${donorSummary.monthlyCount} ${monthLabel} gave this month`
      : 'Donors will appear here';
  }

  const avgDonationSub = document.getElementById('dashboard-avg-donation-sub') ||
    document.querySelector('#s-dashboard .stats-grid .stat-card:nth-child(4) .stat-sub');
  if(avgDonationSub){
    avgDonationSub.textContent = donations.length > 0
      ? `Across ${donations.length} ${donationLabel}`
      : 'No data yet';
  }

  const donationSeries = buildDashboardDonationSeries(6);
  const currentMonth = donationSeries[donationSeries.length - 1] || { total: 0, title: 'No data yet' };
  const volumeHeader = document.querySelector('#s-dashboard .two-col .card:first-child .card-body > div:first-child');
  const volumeCaption = document.querySelector('#s-dashboard .two-col .card:first-child .card-body > div:last-child > div');

  if(volumeHeader){
    const totalEl = volumeHeader.querySelector('span:first-child');
    const metaEl = volumeHeader.querySelector('span:last-child');
    if(totalEl) totalEl.textContent = formatCurrency(currentMonth.total || 0);
    if(metaEl) metaEl.textContent = donations.length > 0 ? currentMonth.title : 'No data yet';
  }

  if(volumeCaption){
    volumeCaption.textContent = donations.length > 0
      ? 'Monthly totals are based on donation dates from the last 6 months.'
      : 'Chart will populate once donations come in';
  }

  renderDashboardVolumeChart(donationSeries);
  syncChecklistFromData();
}

function getCampaignEmoji(campaignOrEmoji, category = '', title = '') {
  const savedEmoji = typeof campaignOrEmoji === 'object'
    ? String(campaignOrEmoji?.emoji || '').trim()
    : String(campaignOrEmoji || '').trim();
  if(savedEmoji) return savedEmoji;

  const resolvedCategory = typeof campaignOrEmoji === 'object'
    ? String(campaignOrEmoji?.cat || campaignOrEmoji?.category || '').trim()
    : String(category || '').trim();
  const resolvedTitle = typeof campaignOrEmoji === 'object'
    ? String(campaignOrEmoji?.title || '').trim()
    : String(title || '').trim();
  const haystack = `${resolvedCategory} ${resolvedTitle}`.toLowerCase();

  if(haystack.includes('hospital') || haystack.includes('health') || haystack.includes('medical')) return '🏥';
  if(haystack.includes('school') || haystack.includes('education') || haystack.includes('supplies') || haystack.includes('student') || haystack.includes('book')) return '📚';
  if(haystack.includes('typhoon') || haystack.includes('flood') || haystack.includes('storm') || haystack.includes('wave')) return '🌊';
  if(haystack.includes('disaster') || haystack.includes('relief') || haystack.includes('emergency')) return '🚨';
  if(haystack.includes('environment') || haystack.includes('tree') || haystack.includes('earth') || haystack.includes('nature')) return '🌱';
  if(haystack.includes('food') || haystack.includes('meal') || haystack.includes('feeding') || haystack.includes('hunger')) return '🍲';
  if(haystack.includes('animal') || haystack.includes('pet')) return '🐾';
  if(haystack.includes('housing') || haystack.includes('shelter') || haystack.includes('home')) return '🏠';
  return '💙';
}

function getCategoryIcon(category) {
  const icons = {
    'Disaster Relief': '🌊',
    'Education': '📚',
    'Health': '🏥',
    'Environment': '🌱',
    'Animal Welfare': '🐾',
    'Feeding Program': '🍽️'
  };
  return icons[category] || '🌊'; // Default to wave icon if category not found
}

function getCampaignDaysLeft(campaign) {
  if(!campaign || !campaign.date) return null;
  const target = new Date(campaign.date);
  if(Number.isNaN(target.getTime())) return null;
  const today = new Date();
  const currentDay = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const deadlineDay = new Date(target.getFullYear(), target.getMonth(), target.getDate());
  const diff = deadlineDay.getTime() - currentDay.getTime();
  return Math.ceil(diff / (1000 * 60 * 60 * 24));
}

function getCampaignProgress(campaign) {
  const goal = Number(campaign?.goal || 0);
  const raised = Number(donationStats[campaign?.key]?.total || 0);
  if(goal <= 0) return 0;
  return Math.min(Math.max(Math.round((raised / goal) * 100), 0), 100);
}

function selectEmergencyOrFeaturedCampaign() {
  const visibleCampaigns = campaigns.filter(c => c && !c.hidden);
  const enriched = visibleCampaigns.map(c => ({
    ...c,
    deadlineDays: getCampaignDaysLeft(c),
    progress: getCampaignProgress(c),
    raised: Number(donationStats[c?.key]?.total || 0)
  }));

  const urgent = enriched
    .filter(c => c.deadlineDays !== null && c.deadlineDays <= 3)
    .sort((a, b) => {
      if(a.deadlineDays !== b.deadlineDays) return a.deadlineDays - b.deadlineDays;
      return b.progress - a.progress;
    });

  if(urgent.length) {
    return { campaign: urgent[0], mode: 'EMERGENCY' };
  }

  if(enriched.length === 0) {
    return { campaign: null, mode: 'FEATURED' };
  }

  const featured = enriched.sort((a, b) => {
    if(b.progress !== a.progress) return b.progress - a.progress;
    const aDeadline = a.deadlineDays === null ? 9999 : a.deadlineDays;
    const bDeadline = b.deadlineDays === null ? 9999 : b.deadlineDays;
    return aDeadline - bDeadline;
  })[0];

  return { campaign: featured, mode: 'FEATURED' };
}

function renderEmergencyCard() {
  const card = document.getElementById('emergency-card');
  if(!card) return;
  const { campaign, mode } = selectEmergencyOrFeaturedCampaign();
  if(!campaign) {
    card.style.display = 'none';
    return;
  }

  const title = campaign.title || 'Untitled campaign';
  const raised = Number(donationStats[campaign.key]?.total || 0);
  const goal = Number(campaign.goal || 0);
  const percentage = goal > 0 ? Math.min(Math.max(Math.round((raised / goal) * 100), 0), 100) : 0;
  const daysLeft = getCampaignDaysLeft(campaign);
  const deadlineText = daysLeft === null ? 'No deadline' : `${daysLeft} day${daysLeft === 1 ? '' : 's'} left`;
  const category = campaign.cat || 'General';

  card.style.display = '';
  card.innerHTML = `
    <div class="card-header">
      <div class="card-title">${mode === 'EMERGENCY' ? 'EMERGENCY' : 'FEATURED'}</div>
    </div>
    <div class="card-body" style="padding:22px 20px 20px">
      <div style="font-size:15px;color:var(--muted);font-weight:600;margin-bottom:6px">${mode === 'EMERGENCY' ? '🚨 Urgent deadline' : '⭐ Campaign spotlight'}</div>
      <div style="font-size:20px;font-weight:700;line-height:1.2;margin-bottom:16px;min-height:56px">${title}</div>
      <div style="display:flex;justify-content:space-between;gap:14px;margin-bottom:14px;flex-wrap:wrap">
        <div style="font-size:13px;color:var(--muted)">Raised ${amountToString(raised)}</div>
        <div style="font-size:13px;color:var(--muted)">${deadlineText}</div>
      </div>
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
        <div style="font-size:24px;font-weight:700">${percentage}%</div>
        <div style="font-size:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px">${category}</div>
      </div>
      <div style="height:10px;background:var(--border2);border-radius:999px;overflow:hidden;margin-bottom:12px">
        <div style="width:${percentage}%;height:100%;background:linear-gradient(90deg,var(--accent),#4ea7ff);border-radius:999px"></div>
      </div>
      <div style="font-size:12px;color:var(--muted)">Goal: ${amountToString(goal)}</div>
    </div>
  `;
}

function amountToString(value) {
  return formatCurrency(value);
}

function currentRole(){
  return window.currentUserProfile?.role || 'Viewer';
}

function canDeleteCampaigns(){
  return ['Super Admin','Admin'].includes(currentRole());
}

function canManageCampaigns(){
  return ['Super Admin','Admin','Moderator'].includes(currentRole());
}

function canToggleCampaignVisibility(){
  return ['Super Admin','Admin','Moderator'].includes(currentRole());
}

function canInviteUsers(){
  return ['Super Admin','Admin'].includes(currentRole());
}

function canManageFeedSettings(){
  return ['Super Admin','Admin'].includes(currentRole());
}

function isAdminRoleForFirestore(role){
  const key = String(role || '').trim().toLowerCase().replace(/[\s-]+/g, '_');
  return key === 'admin' || key === 'super_admin' || key === 'superadmin';
}

async function readCurrentFirestoreRoleState(user){
  const state = {
    userPath: user?.uid ? `users/${user.uid}` : '',
    exists: false,
    role: '',
    roleKey: '',
    email: '',
    readErrorCode: '',
    readErrorMessage: ''
  };

  if(!user || !window.firebaseDoc || !window.firebaseGetDoc || !window.firebaseFirestore){
    state.readErrorMessage = 'Firestore SDK is not ready.';
    return state;
  }

  try {
    const userRef = window.firebaseDoc(window.firebaseFirestore, 'users', user.uid);
    const snapshot = await window.firebaseGetDoc(userRef);
    state.exists = snapshot.exists();
    if(snapshot.exists()){
      const data = snapshot.data() || {};
      state.role = data.role || '';
      state.roleKey = data.roleKey || '';
      state.email = data.email || '';
    }
  } catch(err){
    state.readErrorCode = err?.code || '';
    state.readErrorMessage = err?.message || String(err);
  }

  return state;
}

async function buildCampaignFeedPermissionContext(user){
  const tokenState = {
    role: '',
    readErrorCode: '',
    readErrorMessage: ''
  };

  try {
    const token = window.firebaseGetIdTokenResult
      ? await window.firebaseGetIdTokenResult(user, false)
      : null;
    tokenState.role = token?.claims?.role || '';
  } catch(err){
    tokenState.readErrorCode = err?.code || '';
    tokenState.readErrorMessage = err?.message || String(err);
  }

  const firestoreRole = await readCurrentFirestoreRoleState(user);
  const context = {
    uid: user.uid,
    email: user.email || '',
    uiRole: currentRole(),
    tokenRole: tokenState.role,
    tokenRoleErrorCode: tokenState.readErrorCode,
    tokenRoleErrorMessage: tokenState.readErrorMessage,
    firestoreUserPath: firestoreRole.userPath,
    firestoreUserExists: firestoreRole.exists,
    firestoreRole: firestoreRole.role,
    firestoreRoleKey: firestoreRole.roleKey,
    firestoreRoleReadErrorCode: firestoreRole.readErrorCode,
    firestoreRoleReadErrorMessage: firestoreRole.readErrorMessage,
    settingsPath: 'app_settings/campaign_feed'
  };
  context.hasFirestoreAdminRole =
    isAdminRoleForFirestore(context.firestoreRole) ||
    isAdminRoleForFirestore(context.firestoreRoleKey) ||
    isAdminRoleForFirestore(context.tokenRole);
  return context;
}

function canViewTeam(){
  return ['Super Admin','Admin'].includes(currentRole());
}

function switchCampaignsTab(tab){
  document.querySelectorAll('[data-campaign-tab]').forEach(el => {
    el.classList.toggle('active', el.dataset.campaignTab === tab);
  });
  document.querySelectorAll('.campaign-tab-pane').forEach(el => {
    el.classList.toggle('active', el.id === 'campaigns-tab-' + tab);
  });

  const newCampaignButton = document.getElementById('btn-new-campaign-campaigns');
  if(newCampaignButton){
    newCampaignButton.style.display = tab === 'campaigns' && canManageCampaigns() ? '' : 'none';
  }

  const sub = document.getElementById('campaigns-sub');
  if(sub){
    if(tab === 'settings'){
      sub.textContent = canManageFeedSettings()
        ? 'Control who can create campaign feed posts'
        : 'Feed settings are read only for your role';
    } else if(tab === 'official'){
      const count = campaignFeedPosts.filter(isOfficialFeedPost).length;
      sub.textContent = `${count} official post${count === 1 ? '' : 's'}`;
    } else if(tab === 'community'){
      const count = campaignFeedPosts.filter(post => !isOfficialFeedPost(post)).length;
      sub.textContent = `${count} community post${count === 1 ? '' : 's'}`;
    } else {
      const visibleCampaigns = getAccessibleCampaigns();
      sub.textContent = visibleCampaigns.length === 0
        ? (campaigns.length === 0 ? 'No campaigns yet' : 'No visible campaigns')
        : visibleCampaigns.length+' campaign'+(visibleCampaigns.length>1?'s':'');
    }
  }
}

function normalizeCampaignFeedSettings(data){
  const source = data || {};
  return {
    allowUserPosts: typeof source.allowUserPosts === 'boolean' ? source.allowUserPosts : campaignFeedSettingsDefaults.allowUserPosts,
    allowGuestPosts: typeof source.allowGuestPosts === 'boolean' ? source.allowGuestPosts : campaignFeedSettingsDefaults.allowGuestPosts,
    allowGuestReactions: typeof source.allowGuestReactions === 'boolean' ? source.allowGuestReactions : campaignFeedSettingsDefaults.allowGuestReactions,
    allowGuestComments: typeof source.allowGuestComments === 'boolean' ? source.allowGuestComments : campaignFeedSettingsDefaults.allowGuestComments
  };
}

function bindFeedSettingToggle(id, enabled, editable){
  const toggle = document.getElementById(id);
  if(!toggle) return;
  toggle.classList.toggle('on', !!enabled);
  toggle.classList.toggle('disabled', !editable);
  toggle.setAttribute('aria-checked', enabled ? 'true' : 'false');
  toggle.setAttribute('aria-disabled', editable ? 'false' : 'true');
}

function renderCampaignFeedSettings(){
  const editable = canManageFeedSettings();
  bindFeedSettingToggle('feed-allow-user-posts', campaignFeedSettings.allowUserPosts, editable);
  bindFeedSettingToggle('feed-allow-guest-posts', campaignFeedSettings.allowGuestPosts, editable);
  bindFeedSettingToggle('feed-allow-guest-reactions', campaignFeedSettings.allowGuestReactions, editable);
  bindFeedSettingToggle('feed-allow-guest-comments', campaignFeedSettings.allowGuestComments, editable);

  const status = document.getElementById('feed-settings-status');
  if(status){
    const updatedText = campaignFeedSettingsExists
      ? 'Settings are synced from Firestore app_settings/campaign_feed.'
      : 'Using default settings until Firestore app_settings/campaign_feed is readable.';
    status.textContent = editable
      ? updatedText
      : updatedText + ' Only Admin and Super Admin can change these switches.';
  }
}

async function ensureAdminFirestoreAccess(){
  const user = await window.firebaseAuthReady;
  if(!user){
    console.error('Feed settings save blocked: no authenticated Firebase user.');
    return { ok: false, user: null, context: null };
  }

  const context = await buildCampaignFeedPermissionContext(user);
  console.log('Campaign feed Firestore permission context:', context);

  if(!canManageFeedSettings()){
    console.error(
      'Feed settings save blocked: current Web Admin profile is not Admin/Super Admin.',
      context,
      window.currentUserProfile
    );
    return { ok: false, user, context };
  }

  if(!context.hasFirestoreAdminRole){
    console.warn(
      'Firestore rules do not currently see an admin/super_admin role for this user. ' +
      'The write will still be attempted so the exact Firebase permission error is logged.',
      context
    );
  }

  return { ok: true, user, context };
}

async function ensureDefaultCampaignFeedSettings(){
  if(!campaignFeedSettingsExists){
    console.warn(
      'Campaign feed settings document is not currently readable at app_settings/campaign_feed. ' +
      'It will not be auto-created from the Web Admin.'
    );
  }
}

async function toggleCampaignFeedSetting(field){
  if(!Object.prototype.hasOwnProperty.call(campaignFeedSettingsDefaults, field)){
    return;
  }
  if(!canManageFeedSettings()){
    showToast('Only Admin and Super Admin can change feed settings.', 'error');
    return;
  }
  if(!window.firebaseSetDoc || !window.firebaseCampaignFeedSettingsRef || !window.firebaseServerTimestamp){
    showToast('Feed settings are still loading. Please try again.', 'error');
    return;
  }

  const nextValue = !campaignFeedSettings[field];
  const previous = {...campaignFeedSettings};
  campaignFeedSettings = {...campaignFeedSettings, [field]: nextValue};
  renderCampaignFeedSettings();

  try {
    const access = await ensureAdminFirestoreAccess();
    if(!access.ok || !access.user){
      throw new Error('Current user cannot manage campaign feed settings.');
    }
    console.log('Saving campaign feed setting:', {
      path: 'app_settings/campaign_feed',
      settingKey: field,
      value: nextValue,
      uid: access.user.uid,
      email: access.user.email || '',
      uiRole: access.context?.uiRole || '',
      firestoreRole: access.context?.firestoreRole || '',
      firestoreRoleKey: access.context?.firestoreRoleKey || '',
      tokenRole: access.context?.tokenRole || ''
    });
    await window.firebaseSetDoc(window.firebaseCampaignFeedSettingsRef, {
      [field]: nextValue,
      updatedAt: window.firebaseServerTimestamp(),
      updatedBy: access.user.uid
    }, { merge: true });
    showToast('Feed setting saved.', 'success');
  } catch(err){
    console.error('Unable to save feed setting', {
      path: 'app_settings/campaign_feed',
      settingKey: field,
      attemptedValue: nextValue,
      code: err?.code || '',
      message: err?.message || String(err),
      error: err
    });
    campaignFeedSettings = previous;
    renderCampaignFeedSettings();
    showToast('Unable to save feed setting. Please check your permissions.', 'error');
  }
}

function watchCampaignFeedSettings(){
  if(!window.firebaseCampaignFeedSettingsRef || !window.firebaseOnFirestoreSnapshot){
    console.warn('Firestore feed settings ref not initialized');
    return;
  }

  window.firebaseOnFirestoreSnapshot(window.firebaseCampaignFeedSettingsRef, snapshot => {
    campaignFeedSettingsExists = snapshot.exists();
    campaignFeedSettings = normalizeCampaignFeedSettings(snapshot.exists() ? snapshot.data() : null);
    renderCampaignFeedSettings();
    ensureDefaultCampaignFeedSettings();
  }, error => {
    console.error('Campaign feed settings listener error', error);
    campaignFeedSettings = {...campaignFeedSettingsDefaults};
    renderCampaignFeedSettings();
    showToast('Unable to load campaign feed settings.', 'error');
  });
}

function isOfficialFeedPost(post){
  const category = String(post?.category || '').toLowerCase();
  const role = String(post?.authorRole || '').toLowerCase();
  const type = String(post?.postType || '').toLowerCase();
  return category === 'official' || role === 'admin' || role === 'super_admin' || type === 'live_campaign';
}

function feedPostTimestamp(value){
  if(value && typeof value.toDate === 'function'){
    return value.toDate().getTime();
  }
  if(typeof value === 'number') return value;
  return 0;
}

function feedPostInitials(name){
  return String(name || 'HG')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map(part => part.charAt(0).toUpperCase())
    .join('') || 'HG';
}

function escapeHtml(value){
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function renderCampaignFeedPostList(targetId, posts, emptyText){
  const target = document.getElementById(targetId);
  if(!target) return;
  if(posts.length === 0){
    target.innerHTML = `<div style="text-align:center;padding:28px;color:var(--hint);font-size:13px">${emptyText}</div>`;
    return;
  }

  target.innerHTML = posts.map(post => {
    const author = post.authorName || 'HopeGive';
    const createdAt = feedPostTimestamp(post.createdAt);
    const dateText = createdAt ? new Date(createdAt).toLocaleString() : 'Recently';
    const text = post.text || (post.postType === 'live_campaign' ? post.campaignTitle || 'Live campaign update' : 'Photo update');
    return `
      <div class="feed-post-row">
        <div class="feed-post-avatar">${escapeHtml(feedPostInitials(author))}</div>
        <div class="feed-post-content">
          <div class="feed-post-title">${escapeHtml(author)}</div>
          <div class="feed-post-meta">${escapeHtml(post.category || 'community')} · ${escapeHtml(post.postType || 'note')} · ${escapeHtml(dateText)}</div>
          <div class="feed-post-text">${escapeHtml(text)}</div>
        </div>
      </div>`;
  }).join('');
}

function renderCampaignFeedPosts(){
  const officialPosts = campaignFeedPosts.filter(isOfficialFeedPost);
  const communityPosts = campaignFeedPosts.filter(post => !isOfficialFeedPost(post));
  renderCampaignFeedPostList('official-posts-body', officialPosts, 'No official posts yet');
  renderCampaignFeedPostList('community-posts-body', communityPosts, 'No community posts yet');

  const activeTab = document.querySelector('[data-campaign-tab].active')?.dataset?.campaignTab;
  if(activeTab === 'official' || activeTab === 'community'){
    switchCampaignsTab(activeTab);
  }
}

function watchCampaignFeedPosts(){
  if(!window.firebaseCampaignFeedPostsRef || !window.firebaseOnFirestoreSnapshot || !window.firebaseQuery || !window.firebaseOrderBy){
    console.warn('Firestore campaign feed posts ref not initialized');
    return;
  }

  const postsQuery = window.firebaseQuery(
    window.firebaseCampaignFeedPostsRef,
    window.firebaseOrderBy('createdAt', 'desc')
  );

  window.firebaseOnFirestoreSnapshot(postsQuery, snapshot => {
    campaignFeedPosts = snapshot.docs.map(docSnap => ({ id: docSnap.id, ...docSnap.data() }));
    renderCampaignFeedPosts();
  }, error => {
    console.error('Campaign feed posts listener error', error);
    campaignFeedPosts = [];
    renderCampaignFeedPosts();
  });
}

function openCampaignModal(key){
  if(!canManageCampaigns()){
    showToast('Only admins and moderators can create or edit campaigns.');
    return;
  }
  editingCampaignKey = key || null;
  const titleField = document.getElementById('c-title');
  const descField = document.getElementById('c-desc');
  const goalField = document.getElementById('c-goal');
  const dateField = document.getElementById('c-date');
  const catField = document.getElementById('c-cat');
  const emojiField = document.getElementById('c-emoji');
  const saveBtn = document.getElementById('campaign-save-btn');
  if(key){
    const campaign = campaigns.find(c => c.key === key);
    if(campaign){
      titleField.value = campaign.title || '';
      descField.value = campaign.description || '';
      goalField.value = campaign.goal || '';
      dateField.value = campaign.date && campaign.date !== 'No end date' ? campaign.date : '';
      catField.value = campaign.cat || '';
      if(emojiField) emojiField.value = campaign.emoji || getCampaignEmoji(campaign);
      saveBtn.textContent = 'Save Campaign';
      document.querySelector('#modal-campaign .modal-title').textContent = 'Edit campaign';
    }
  } else {
    titleField.value = '';
    descField.value = '';
    goalField.value = '';
    dateField.value = '';
    catField.value = '';
    if(emojiField) emojiField.value = '';
    saveBtn.textContent = 'Create Campaign →';
    document.querySelector('#modal-campaign .modal-title').textContent = 'Create first campaign';
  }
  openModal('modal-campaign');
}

function normalizeNotificationRole(role){
  return String(role || '').trim().toLowerCase().replace(/\s+/g, '_') || 'user';
}

function isAdminTeamNotificationRole(role){
  return ['admin', 'super_admin', 'moderator'].includes(normalizeNotificationRole(role));
}

function notificationUserName(profile, fallback){
  return profile?.fullName || profile?.displayName || profile?.name || profile?.email || fallback || 'HopeGive User';
}

async function loadFirestoreNotificationUsers(){
  if(!window.firebaseFirestore || !window.firebaseCollection || !window.firebaseGetDocs) return [];
  const snapshot = await window.firebaseGetDocs(window.firebaseCollection(window.firebaseFirestore, 'users'));
  return snapshot.docs.map(docSnap => {
    const data = docSnap.data() || {};
    return {
      id: data.uid || docSnap.id,
      role: data.role || data.roleKey || '',
      fullName: notificationUserName(data),
      campaignId: data.campaign?.campaignId || data.campaignId || ''
    };
  }).filter(user => user.id);
}

async function createFirestoreNotification(recipient, payload){
  const authUser = window.firebaseAuth?.currentUser;
  if(!authUser || !window.firebaseFirestore || !window.firebaseCollection || !window.firebaseDoc || !window.firebaseSetDoc || !window.firebaseServerTimestamp) return;
  if(!recipient?.id || recipient.id === authUser.uid) return;

  const notificationRef = window.firebaseDoc(window.firebaseCollection(window.firebaseFirestore, 'notifications'));
  const senderName = notificationUserName(window.currentUserProfile, authUser.email || 'HopeGive Admin');
  await window.firebaseSetDoc(notificationRef, {
    id: notificationRef.id,
    recipientId: recipient.id,
    recipientRole: recipient.role || '',
    senderId: authUser.uid,
    senderName,
    senderRole: normalizeNotificationRole(window.currentUserProfile?.role || window.currentUserProfile?.roleKey || ''),
    type: payload.type || 'campaign_added',
    title: payload.title || 'HopeGive notification',
    message: payload.message || '',
    relatedUserId: authUser.uid,
    campaignId: payload.campaignId || '',
    campaignTitle: payload.campaignTitle || '',
    donationId: payload.donationId || '',
    postId: payload.postId || '',
    commentId: payload.commentId || '',
    replyId: payload.replyId || '',
    amount: Number(payload.amount || 0),
    isRead: false,
    createdAt: window.firebaseServerTimestamp()
  });
}

async function notifyCampaignSaved({campaignId, campaignTitle, isEditing}){
  try{
    const users = await loadFirestoreNotificationUsers();
    const authUser = window.firebaseAuth?.currentUser;
    if(!authUser) return;

    const normalUsers = users.filter(user => !isAdminTeamNotificationRole(user.role));
    const adminTeam = users.filter(user => isAdminTeamNotificationRole(user.role));
    const recipients = isEditing
      ? normalUsers.filter(user => user.campaignId === campaignId)
      : normalUsers;

    const userType = isEditing ? 'campaign_updated' : 'campaign_added';
    const userTitle = isEditing ? 'Campaign updated' : 'New campaign';
    const userMessage = isEditing
      ? `${campaignTitle} was updated.`
      : `A new campaign was added: ${campaignTitle}.`;

    await Promise.all(recipients.map(user => createFirestoreNotification(user, {
      type: userType,
      title: userTitle,
      message: userMessage,
      campaignId,
      campaignTitle
    })));

    const senderName = notificationUserName(window.currentUserProfile, authUser.email || 'An admin');
    await Promise.all(adminTeam.map(user => createFirestoreNotification(user, {
      type: userType,
      title: userTitle,
      message: isEditing
        ? `${senderName} updated ${campaignTitle}.`
        : `${senderName} added a new campaign.`,
      campaignId,
      campaignTitle
    })));
  }catch(error){
    console.error('Unable to create campaign notifications', error);
  }
}

async function saveCampaign(){
  console.log('💾 saveCampaign() called');
  
  // Check permissions
  if(!canManageCampaigns()){
    console.warn('❌ User lacks permissions to manage campaigns');
    showToast('Only admins and moderators can create or edit campaigns.');
    return;
  }
  
  // Get form values
  const title=document.getElementById('c-title').value.trim();
  const desc=document.getElementById('c-desc').value.trim();
  const goal=document.getElementById('c-goal').value;
  const date=document.getElementById('c-date').value;
  const cat=document.getElementById('c-cat').value;
  const emojiInput=(document.getElementById('c-emoji')?.value || '').trim();
  
  console.log('📝 Campaign data:', {title, desc, goal, date, cat});
  
  // Validate title
  if(!title){
    console.warn('⚠️ Campaign title is empty');
    document.getElementById('c-title').focus();
    showToast('Campaign title is required');
    return;
  }
  
  // Check Firebase readiness
  if(!window.firebaseCampaignsRef || !window.firebaseAuthReady || !window.firebaseRef || !window.firebaseSet || !window.firebasePush){
    console.error('❌ Firebase not ready:', {
      campaignsRef: !!window.firebaseCampaignsRef,
      authReady: !!window.firebaseAuthReady,
      ref: !!window.firebaseRef,
      set: !!window.firebaseSet,
      push: !!window.firebasePush
    });
    showToast('Firebase is loading. Please wait a moment and try again.');
    return;
  }
  
  console.log('✓ Firebase functions available');
  
  const isEditing = !!editingCampaignKey;
  const existingCampaign = isEditing ? campaigns.find(c => c.key === editingCampaignKey) || {} : {};
  
  const campaignData={
    title,
    description: desc || '',
    goal: Number(goal) || 0,
    date: date || 'No end date',
    cat: cat || 'General',
    emoji: emojiInput || existingCampaign.emoji || getCampaignEmoji({ cat, title }),
    createdAt: isEditing ? existingCampaign.createdAt || Date.now() : Date.now(),
    status: 'Active',
    hidden: false,
    published: true,
    raised: isEditing ? existingCampaign.raised || 0 : 0
  };
  
  try {
    console.log('⏳ Waiting for Firebase auth...');
    await window.firebaseAuthReady;
    console.log('✓ Auth ready, proceeding with save');
    
    if(editingCampaignKey){
      console.log('📝 Updating campaign:', editingCampaignKey);
      const campaignRef = window.firebaseRef(window.firebaseDb, 'campaigns/' + editingCampaignKey);
      await window.firebaseSet(campaignRef, {
        ...existingCampaign,
        ...campaignData,
        updatedAt: Date.now(),
        campaignId: existingCampaign.campaignId || editingCampaignKey || existingCampaign.key,
        createdAt: existingCampaign.createdAt || Date.now() // Preserve original creation time
      });
      console.log('✅ Campaign updated successfully');
      await notifyCampaignSaved({
        campaignId: existingCampaign.campaignId || editingCampaignKey,
        campaignTitle: title,
        isEditing: true
      });
      showToast('Campaign "'+title+'" updated!');
      editingCampaignKey = null;
    } else {
      console.log('➕ Creating new campaign');
      const campaignsRef = window.firebaseCampaignsRef;
      const newCampaignRef = window.firebasePush(campaignsRef);
      const newKey = newCampaignRef.key;
      console.log('  New campaign key:', newKey);
      
      await window.firebaseSet(newCampaignRef, {
        ...campaignData,
        campaignId: newKey
      });
      
      console.log('✅ Campaign created successfully:', newKey);
      await notifyCampaignSaved({
        campaignId: newKey,
        campaignTitle: title,
        isEditing: false
      });
      setCheckState(1, 'done');
      const dotEl = document.getElementById('dot-campaign');
      if(dotEl) dotEl.classList.add('active');
      showToast('Campaign "'+title+'" created!');
    }
  } catch(err){
    console.error('❌ Firebase save error:', err);
    const message = err && err.message ? err.message : 'Failed to save campaign. Check console for details.';
    showToast(message);
    if(!editingCampaignKey) setCheckState(1, 'failed');
    return;
  }
  
  // Clear form and close modal
  closeModal('modal-campaign');
  document.getElementById('c-title').value='';
  document.getElementById('c-desc').value='';
  document.getElementById('c-goal').value='';
  document.getElementById('c-date').value='';
  document.getElementById('c-cat').value='';
  if(document.getElementById('c-emoji')) document.getElementById('c-emoji').value='';
  console.log('✓ saveCampaign() completed');
}

function editCampaign(key){
  if(!canManageCampaigns()){
    showToast('Only admins can edit campaigns.');
    return;
  }
  openCampaignModal(key);
}

async function toggleCampaignVisibility(key){
  if(!canManageCampaigns()){
    showToast('Only admins can change visibility.');
    return;
  }
  try {
    await window.firebaseAuthReady;
    if(!canToggleCampaignVisibility()){
      showToast('Only admins and moderators can update campaign visibility.');
      return;
    }
    const campaign = campaigns.find(c => c.key === key) || {};
    const campaignRef = window.firebaseRef(window.firebaseDb, 'campaigns/' + key);
    await window.firebaseSet(campaignRef, {
      ...campaign,
      hidden: !campaign.hidden,
      published: campaign.hidden,
      createdAt: campaign.createdAt || Date.now(),
      updatedAt: Date.now(),
      raised: campaign.raised || 0,
      status: campaign.status || 'Active'
    });
    showToast(campaign.hidden ? 'Campaign is now visible' : 'Campaign hidden');
  } catch(err){
    console.error('Firebase visibility error', err);
    showToast('Unable to update campaign visibility');
  }
}

function viewCampaign(key){
  const campaign = campaigns.find(c => c.key === key);
  if(!campaign){
    showToast('Campaign not found.');
    return;
  }
  showToast('Viewing campaign: ' + campaign.title);
}

async function deleteCampaign(key){
  if(!canDeleteCampaigns()){
    showToast('Only admins can delete campaigns.');
    return;
  }
  window.campaignToDelete = key;
  openModal('modal-delete');
  startDeleteTimer();
}

function startDeleteTimer(){
  if(window.deleteTimer) clearInterval(window.deleteTimer);
  let timeLeft = 5;
  const btn = document.getElementById('delete-confirm-btn');
  btn.disabled = true;
  btn.textContent = `Yes (${timeLeft})`;
  window.deleteTimer = setInterval(() => {
    timeLeft--;
    btn.textContent = `Yes (${timeLeft})`;
    if(timeLeft <= 0){
      clearInterval(window.deleteTimer);
      window.deleteTimer = null;
      btn.disabled = false;
      btn.textContent = 'Yes';
    }
  }, 1000);
}

async function confirmDelete(){
  const key = window.campaignToDelete;
  closeModal('modal-delete');
  try {
    await window.firebaseAuthReady;
    const campaignRef = window.firebaseRef(window.firebaseDb, 'campaigns/' + key);
    await window.firebaseRemove(campaignRef);
    showToast('Campaign deleted successfully!');
  } catch(err){
    console.error('Firebase delete error', err);
    const message = err && err.message ? err.message : 'Failed to delete campaign';
    showToast(message);
  }
}

async function deleteDonation(donationId){
  if(!confirm('Are you sure you want to delete this donation? This action cannot be undone.')){
    return;
  }
  try {
    await window.firebaseAuthReady;
    const donationRef = window.firebaseRef(window.firebaseDb, 'donations/' + donationId);
    await window.firebaseRemove(donationRef);
    showToast('Donation deleted successfully!');
  } catch(err){
    console.error('Firebase delete donation error', err);
    const message = err && err.message ? err.message : 'Failed to delete donation';
    showToast(message);
  }
}
 
function renderCampaigns(){
  console.log('🎨 renderCampaigns() called with', campaigns.length, 'total campaigns');
  
  const empty=document.getElementById('campaigns-empty');
  const list=document.getElementById('campaigns-list');
  const sub=document.getElementById('campaigns-sub');
  const body=document.getElementById('campaigns-table-body');
  const activeCampaignTab = document.querySelector('[data-campaign-tab].active')?.dataset?.campaignTab || 'campaigns';
  
  // Safe DOM check
  if(!empty || !list || !sub || !body){
    console.error('❌ Missing required DOM elements:', {empty: !!empty, list: !!list, sub: !!sub, body: !!body});
    return;
  }
  console.log('✓ All required DOM containers found');
  
  const role = window.currentUserProfile?.role || 'Viewer';
  console.log(`  Role: ${role}`);
  
  const visibleCampaigns = ['Super Admin','Admin','Moderator'].includes(role) ? campaigns : campaigns.filter(c => !c.hidden);
  console.log(`  Visible campaigns: ${visibleCampaigns.length} (after role filtering)`);
  
  if(visibleCampaigns.length===0){
    console.log('  → Showing empty state');
    empty.style.display='';
    list.style.display='none';
    sub.textContent = campaigns.length === 0 ? 'No campaigns yet' : 'No visible campaigns';
    if(activeCampaignTab !== 'campaigns') switchCampaignsTab(activeCampaignTab);
    return;
  }
  
  console.log('  → Rendering campaign list');
  empty.style.display='none';
  list.style.display='';
  sub.textContent=visibleCampaigns.length+' campaign'+(visibleCampaigns.length>1?'s':'');
  body.innerHTML='<div class="card-header"><div class="card-title">All campaigns</div></div>'+
    visibleCampaigns.map((c,i)=>{
      let actionHtml = '<div style="display:flex;gap:6px">';
      if(['Super Admin','Admin','Moderator'].includes(role)){
        actionHtml += `<div class="icon-btn" onclick="editCampaign('${c.key}')">✏️</div>`;
        actionHtml += `<div class="icon-btn" onclick="toggleCampaignVisibility('${c.key}')">${c.hidden? '🚫' : '👁'}</div>`;
        if(['Super Admin','Admin'].includes(role)){
          actionHtml += `<div class="icon-btn" onclick="deleteCampaign('${c.key}')">🗑️</div>`;
        }
      } else {
        actionHtml += `<div class="icon-btn" onclick="viewCampaign('${c.key}')">👁</div>`;
      }
      actionHtml += '</div>';
      const stats = donationStats[c.key] || { total: 0, count: 0 };
      const goal = Number(c.goal || 0);
      const raised = stats.total;
      const percentage = goal > 0 ? Math.round((raised / goal) * 100) : 0;
      return `
      <div style="display:flex;align-items:center;gap:14px;padding:16px 20px;border-bottom:1px solid var(--border)">
        <div style="width:38px;height:38px;border-radius:8px;background:var(--navy3);display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0">${getCampaignEmoji(c)}</div>
        <div style="flex:1;min-width:0">
          <div style="font-size:14px;font-weight:500;margin-bottom:2px">${c.title}</div>
          <div style="font-size:12px;color:var(--muted)">${c.cat} · Goal: ₱${Number(goal).toLocaleString()||'—'} · ${c.date}</div>
          <div style="margin-top:6px;height:3px;background:var(--border2);border-radius:2px;overflow:hidden"><div style="width:${Math.min(percentage, 100)}%;height:100%;background:var(--accent);border-radius:2px"></div></div>
          <div style="font-size:11px;color:var(--hint);margin-top:3px">₱${Number(raised).toLocaleString()} raised · ${percentage}% · ${stats.count} donation${stats.count !== 1 ? 's' : ''}</div>
        </div>
        <span class="badge neutral">${c.hidden? 'Hidden' : (c.status || 'Draft')}</span>
        ${actionHtml}
      </div>`;
    }).join('')+
    `<div style="padding:14px 20px">
      <button class="btn btn-primary btn-sm" id="campaigns-add-another" onclick="openCampaignModal()">+ Add another campaign</button>
    </div>`;
 
  renderDashboardOverview();
  renderEmergencyCard();
  if(activeCampaignTab !== 'campaigns') switchCampaignsTab(activeCampaignTab);
  console.log(`✓ Rendered ${visibleCampaigns.length} campaigns to UI`);
}
 
function watchCampaigns(){
  console.log('🔄 watchCampaigns() starting...');
  
  // Retry logic if ref not ready
  const campaignsRef = window.firebaseCampaignsRef;
  if(!campaignsRef){
    console.error('❌ Firebase campaigns ref NOT initialized. Window refs:', {
      firebaseCampaignsRef: window.firebaseCampaignsRef,
      firebaseOnValue: !!window.firebaseOnValue,
      firebaseDb: !!window.firebaseDb
    });
    // Retry after a brief delay
    console.log('⏳ Retrying watchCampaigns in 1 second...');
    setTimeout(() => watchCampaigns(), 1000);
    return;
  }
  
  console.log('✓ campaignsRef ready');
  
  try {
    window.firebaseOnValue(campaignsRef, snapshot => {
      try {
        const rawData = snapshot.val();
        console.log('📦 Raw Firebase data from campaigns node:', rawData);
        
        if(rawData === null){
          console.warn('⚠️ Firebase campaigns node is empty (null)');
          campaigns = [];
        } else if(typeof rawData === 'object'){
          campaigns = Object.entries(rawData).map(([key, value]) => {
            console.log(`  • Campaign ID: ${key}`, value);
            return { key, ...value };
          }).sort((a,b)=> (b.createdAt||0)-(a.createdAt||0));
          console.log(`✓ Parsed ${campaigns.length} total campaign(s)`);
        } else {
          console.warn('⚠️ Unexpected data type from Firebase:', typeof rawData);
          campaigns = [];
        }
        
        console.log('📐 Calling renderCampaigns()...');
        renderCampaigns();
      } catch(err){
        console.error('❌ Error processing campaign snapshot:', err);
      }
    }, error => {
      console.error('❌ Firebase campaigns listener error:', error);
    });
  } catch(err){
    console.error('❌ Error setting Firebase listener:', err);
  }
}

function watchDonations(){
  const donationsRef = window.firebaseRef(window.firebaseDb, 'donations');
  if(!donationsRef){
    console.warn('Firebase donations ref not initialized');
    return;
  }
  window.firebaseOnValue(donationsRef, snapshot => {
    const data = snapshot.val();
    donations = data ? Object.entries(data).map(([key, value]) => ({ id: key, ...value })).sort((a,b)=> (b.createdAt||0)-(a.createdAt||0)) : [];
    
    // Calculate stats per campaign
    donationStats = {};
    donations.forEach(d => {
      const cid = d.campaignId;
      if(!donationStats[cid]){
        donationStats[cid] = { total: 0, count: 0 };
      }
      donationStats[cid].total += Number(d.amount || 0);
      donationStats[cid].count += 1;
    });
    
    renderCampaigns();
    renderDonationStats();
  }, error => {
    console.error('Firebase donations load error', error);
  });
}

function renderDonationStats(){
  const totalRaised = donations.reduce((sum, d) => sum + Number(d.amount || 0), 0);
  const avgDonation = donations.length > 0 ? Math.round(totalRaised / donations.length) : 0;
  
  setTextContent('[data-stat="donation-count"]', String(donations.length));
  if(document.querySelector('[data-stat="total-raised"]')){
    document.querySelector('[data-stat="total-raised"]').textContent = '₱' + totalRaised.toLocaleString();
  }
  if(document.querySelector('[data-stat="total-raised-donations"]')){
    document.querySelector('[data-stat="total-raised-donations"]').textContent = '₱' + totalRaised.toLocaleString();
  }
  if(document.querySelector('[data-stat="avg-donation"]')){
    document.querySelector('[data-stat="avg-donation"]').textContent = '₱' + avgDonation.toLocaleString();
  }
  
  const tbody = document.getElementById('donations-table-body');
  if(tbody){
    if(donations.length === 0){
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:28px;color:var(--hint);font-size:13px">No donations yet</td></tr>';
    } else {
      tbody.innerHTML = donations.map(d => {
        const campaign = campaigns.find(c => c.key === d.campaignId);
        const campaignName = campaign ? campaign.title : 'Unknown Campaign';
        const date = new Date(d.createdAt || 0).toLocaleDateString();
        return `
        <tr>
          <td><div class="row-cell"><span style="font-weight:500">${d.donorName || 'Anonymous'}</span></div></td>
          <td class="muted">${d.donorEmail || '—'}</td>
          <td>${campaignName}</td>
          <td><strong style="color:var(--green)">₱${Number(d.amount || 0).toLocaleString()}</strong></td>
          <td><span class="badge info">${d.paymentMethod || 'Unknown'}</span></td>
          <td class="muted">${date}</td>
          <td style="text-align:center"><button onclick="deleteDonation('${d.id}')" style="background:none;border:none;cursor:pointer;font-size:16px;padding:4px 8px;color:var(--red);hover:opacity:0.8" title="Delete donation">🗑</button></td>
        </tr>`;
      }).join('');
    }
  }
  renderDashboardOverview();
  renderEmergencyCard();
}

function watchUsers(){
  const usersRef = window.firebaseUsersRef;
  if(!usersRef){
    console.warn('Firebase users ref not initialized');
    return;
  }
  window.firebaseOnValue(usersRef, snapshot => {
    const data = snapshot.val();
    teamMembers = data ? Object.entries(data).map(([key, value]) => {
      const role = normalizeRole(value.role);
      return {
        uid: key,
        email: value.email || 'unknown@domain',
        username: value.username || (value.email ? value.email.split('@')[0] : 'User'),
        role,
        status: value.status || (value.invitedAt ? 'Invited' : 'Active'),
        invitedAt: value.invitedAt || null,
        createdAt: value.createdAt || null,
        initials: (value.username || value.email || 'US').substring(0,2).toUpperCase()
      };
    }) : [];
    renderTeam();
  }, error => {
    console.error('Firebase users load error', error);
  });
}

function setCurrentUserProfile(profile){
  const nameEl = document.getElementById('profile-name');
  const roleEl = document.getElementById('profile-role');
  const avaEl = document.getElementById('profile-ava');
  if(nameEl) nameEl.textContent = profile.username || profile.email || 'User';
  if(roleEl) {
    roleEl.textContent = profile.role || 'Viewer';
    if(profile.role === 'Super Admin'){
      roleEl.style.color = '#c084fc';
      roleEl.style.fontWeight = '600';
    }
  }
  if(avaEl) {
    avaEl.textContent = (profile.username || profile.email || 'US').substring(0,2).toUpperCase();
    if(profile.role === 'Super Admin'){
      avaEl.style.background = 'linear-gradient(135deg, #7C5CBF, #c084fc)';
    }
  }
  const titleBar = document.getElementById('topbar-title');
  if(titleBar && titleBar.textContent === 'Dashboard'){
    titleBar.textContent = titles['dashboard'];
  }
}

function applyRoleRestrictions(role){
  const isSuperAdmin = role === 'Super Admin';
  const isAdmin = role === 'Admin' || isSuperAdmin;
  const canManage = ['Super Admin','Admin','Moderator'].includes(role);
  const navUsers = document.getElementById('nav-users');
  const navSettings = document.getElementById('nav-settings');
  const btnTopbar = document.getElementById('btn-new-campaign-topbar');
  const btnCampaigns = document.getElementById('btn-new-campaign-campaigns');
  const btnWelcome = document.getElementById('btn-welcome-create');
  const btnConfigure = document.getElementById('btn-configure-settings');
  const btnInvite = document.getElementById('btn-invite-member');
  const btnInviteFirst = document.getElementById('btn-invite-first-member');

  if(navUsers) navUsers.style.display = isAdmin ? '' : 'none';
  if(navSettings) navSettings.style.display = isAdmin ? '' : 'none';
  if(btnTopbar) btnTopbar.style.display = canManage ? '' : 'none';
  if(btnCampaigns) btnCampaigns.style.display = canManage ? '' : 'none';
  if(btnWelcome) btnWelcome.style.display = canManage ? '' : 'none';
  if(btnConfigure) btnConfigure.style.display = isAdmin ? '' : 'none';
  if(btnInvite) btnInvite.style.display = isAdmin ? '' : 'none';
  if(btnInviteFirst) btnInviteFirst.style.display = isAdmin ? '' : 'none';
  renderCampaignFeedSettings();
  ensureDefaultCampaignFeedSettings();

  if(!isAdmin){
    const navToHide = [document.getElementById('nav-users'), document.getElementById('nav-settings')];
    navToHide.forEach(el => { if(el) el.style.display = 'none'; });
    if(document.getElementById('s-users').classList.contains('active') || document.getElementById('s-settings').classList.contains('active')){
      nav('dashboard', document.getElementById('nav-dashboard'));
    }
  }
}

function normalizeRole(value){
  const raw = String(value || 'Viewer').trim().toLowerCase();
  if(raw === 'super admin') return 'Super Admin';
  if(raw === 'admin') return 'Admin';
  if(raw === 'moderator') return 'Moderator';
  return 'Viewer';
}

function watchCurrentUserProfile(){
  console.log('👤 watchCurrentUserProfile() starting...');
  const authUser = window.firebaseAuth?.currentUser;
  console.log('  Current auth user:', authUser?.uid || 'null');
  
  if(!authUser){
    console.warn('⚠️ No authenticated user yet, retrying in 1 second...');
    setTimeout(() => watchCurrentUserProfile(), 1000);
    return;
  }
  
  console.log('✓ Auth user found:', authUser.uid);
  const profileRef = window.firebaseRef(window.firebaseDb, 'users/' + authUser.uid);
  console.log('📍 Profile ref path: users/' + authUser.uid);
  
  window.firebaseOnValue(profileRef, snapshot => {
    try {
      const rawData = snapshot.val();
      console.log('📦 Raw user profile data:', rawData);
      
      const data = rawData || {};
      const profile = {
        uid: authUser.uid,
        email: data.email || authUser.email || '',
        username: data.username || authUser.displayName || (authUser.email ? authUser.email.split('@')[0] : 'User'),
        role: normalizeRole(data.role),
        status: data.status || 'Active',
        createdAt: data.createdAt || Date.now()
      };
      
      console.log('✓ User profile loaded:', profile);
      window.currentUserProfile = profile;
      setCurrentUserProfile(profile);
      applyRoleRestrictions(profile.role);
    } catch(err){
      console.error('❌ Error processing user profile:', err);
    }
  }, error => {
    console.error('❌ Firebase user profile listener error:', error);
    // If user exists in Auth but not in DB, create default profile
    console.log('⚠️ Profile not found in database, creating default profile...');
    const defaultProfile = {
      uid: authUser.uid,
      email: authUser.email || '',
      username: authUser.displayName || (authUser.email ? authUser.email.split('@')[0] : 'User'),
      role: 'Viewer',
      status: 'Active',
      createdAt: Date.now()
    };
    window.currentUserProfile = defaultProfile;
    setCurrentUserProfile(defaultProfile);
    applyRoleRestrictions('Viewer');
  });
}

async function sendInvite(){
  if(!canInviteUsers()){
    showToast('Only admins can invite team members.');
    return;
  }
  const email=document.getElementById('invite-email').value.trim();
  const role=document.getElementById('invite-role').value;
  if(!email||!role){return;}
  if(!window.firebaseUsersRef || !window.firebasePush || !window.firebaseSet || !window.firebaseAuthReady){
    console.error('Firebase user invite not initialized');
    showToast('Unable to invite user right now.');
    return;
  }
  try {
    const newUserRef = window.firebasePush(window.firebaseUsersRef);
    await window.firebaseSet(newUserRef, {
      email,
      role,
      status:'Invited',
      invitedAt: Date.now(),
      createdAt: Date.now(),
      username: email.split('@')[0]
    });
  } catch(err){
    console.error('Firebase invite error', err);
    showToast('Failed to save invite.');
    return;
  }
  closeModal('modal-invite');
  checks[4]=true;
  const el=document.getElementById('chk-4');
  el.classList.add('done');
  el.querySelector('.check-circle').textContent='✓';
  el.querySelector('.check-badge').textContent='Done ✓';
  setSetupStepDone('dot-team', true);
  updateCheckProgress();
  showToast('Invite saved for '+email);
  document.getElementById('invite-email').value='';
  document.getElementById('invite-role').value='';
}
 
function renderTeam(){
  const tbody=document.getElementById('team-table');
  const currentUid = window.currentUserProfile?.uid || window.firebaseAuth?.currentUser?.uid || '';
  const additionalMembers = currentUid
    ? teamMembers.filter(member => member.uid !== currentUid)
    : teamMembers.slice(1);
  const extra=teamMembers.map(m=>`
    <tr>
      <td><div class="row-cell"><div class="row-ava" style="background:var(--purple)">${m.initials}</div>${m.username}</div></td>
      <td class="muted">${m.email}</td>
      <td><span class="role-pill ${['Super Admin','Admin'].includes(m.role) ? 'super' : ''}">${m.role}</span></td>
      <td><span class="badge ${m.status === 'Active' ? 'info' : 'neutral'}">${m.status}</span></td>
      <td><span class="icon-btn" style="font-size:11px;color:var(--red)" onclick="removeTeamMember('${m.uid}')">✕</span></td>
    </tr>`).join('');
  tbody.innerHTML=extra;
  const emptyCard = document.querySelector('#s-users .card:last-child');
  if(emptyCard){
    emptyCard.style.display = additionalMembers.length > 0 ? 'none' : '';
  }
  renderDashboardOverview();
}
 
function removeTeamMember(uid){
  if(!confirm('Remove this user from the team?')) return;
  try {
    const userRef = window.firebaseRef(window.firebaseDb, 'users/' + uid);
    window.firebaseRemove(userRef);
    showToast('User removed');
  } catch(err){
    console.error('Firebase remove user error', err);
    showToast('Unable to remove user');
  }
}

function saveSettings(){
  const orgName=document.getElementById('org-name').value.trim();
  if(orgName){
    checks[3]=true;
    const el=document.getElementById('chk-3');
    el.classList.add('done');
    el.querySelector('.check-circle').textContent='✓';
    el.querySelector('.check-badge').textContent='Done ✓';
    setSetupStepDone('dot-org', true);
    updateCheckProgress();
  }
  renderDashboardOverview();
  showToast('Settings saved');
}
 
function updatePaymentDots(){
  const on=['t-gcash','t-maya','t-stripe'].some(id=>document.getElementById(id).classList.contains('on'));
  if(on){
    checks[2]=true;
    const el=document.getElementById('chk-2');
    el.classList.add('done');
    el.querySelector('.check-circle').textContent='✓';
    el.querySelector('.check-badge').textContent='Done ✓';
  }
  setSetupStepDone('dot-payment', on);
  updateCheckProgress();
  renderDashboardOverview();
}
 
renderDashboardOverview();
 
async function logout(){
  try {
    await signOut(window.firebaseAuth);
  } catch(err){
    console.error('Logout failed', err);
  }
  window.location.href='login.html';
}

document.querySelectorAll('.toggle').forEach(t=>{
  if(!t.hasAttribute('onclick')){
    t.addEventListener('click',()=>t.classList.toggle('on'));
  }
});

document.addEventListener('DOMContentLoaded', async () => {
  try {
    console.log('🚀 DOMContentLoaded: Initializing Firebase watchers...');
    console.log('⏳ Waiting for Firebase auth to be ready...');
    await window.firebaseAuthReady;
    console.log('✓ Firebase auth ready!');
    
    console.log('👤 Watching current user profile...');
    watchCurrentUserProfile();
    
    // Wait a moment for user profile to load
    await new Promise(resolve => setTimeout(resolve, 500));
    console.log('\u2713 User profile loading initiated');
    
    console.log('📽️ Watching campaigns...');
    watchCampaigns();
    watchCampaignFeedSettings();
    watchCampaignFeedPosts();
    
    console.log('👥 Watching users...');
    watchUsers();
    
    console.log('💰 Watching donations...');
    watchDonations();
    
    console.log('✅ All Firebase watchers initialized successfully!');
    
    // Diagnostic check after a brief delay to allow data to load
    setTimeout(() => {
      console.log('\n📊 Checking Firebase data status after 3 seconds...');
      diagnoseCampaigns();
    }, 3000);
    loadUsers();

    
  } catch(err) {
    console.error('❌ Firebase initialization failed:', err);
    showToast('Firebase auth failed. Please check console');
  }
});


