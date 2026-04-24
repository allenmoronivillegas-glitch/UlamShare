
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
let sEmojiPickerTarget = null;

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
const S_CHAT_SOURCES = [
  { path:'supportChats', defaultType:'support', label:'Support' },
  { path:'userUserChats', defaultType:'user-user', label:'User-User' },
  { path:'adminTeamChats', defaultType:'admin-team', label:'Admin Team' },
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

function sAvaHtml(label,size=34,isOnline=true){
  const c = sGetColor(label);
  const dotColor = isOnline ? '#22c55e' : '#93c5fd';
  return `<div class="s-ava" style="width:${size}px;height:${size}px;background:${c.bg};color:${c.fg}">${sInitials(label)}<div class="s-ava-dot" style="background:${dotColor}"></div></div>`;
}

function sFormatTime(value){
  if(!value) return '';
  return new Date(value).toLocaleTimeString([], { hour:'2-digit', minute:'2-digit' });
}

function sFormatDateLabel(value){
  if(!value) return 'Today';
  return new Date(value).toLocaleDateString([], { month:'short', day:'numeric', year:'numeric' });
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

function sGetMessageRole(msg){
  const direct = sNormalizeRole(msg?.senderRole || msg?.role || msg?.senderType);
  if(direct) return direct;
  const senderRaw = String(msg?.sender || '').trim().toLowerCase();
  if(senderRaw === 'superadmin' || senderRaw === 'super admin') return 'superadmin';
  if(senderRaw === 'moderator' || senderRaw === 'mod') return 'moderator';
  if(senderRaw === 'admin') return 'admin';
  if(senderRaw === 'support') return 'support';
  if(senderRaw === 'system') return 'system';
  return 'user';
}

function sGetSenderName(msg){
  const known = msg?.senderName || msg?.name || msg?.senderEmail || '';
  if(known) return String(known);
  return sRoleLabel(sGetMessageRole(msg));
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
  selectedUserId = null;
  selectedUserEmail = '';
  selectedConversationType = 'support';
  selectedChatSource = 'supportChats';
  selectedConversationKey = '';
  if(typeof detachChatListener === 'function'){ detachChatListener(); detachChatListener = null; }
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
  sUpdateComposerState();
}

function openChat(userId, email, forcedType, sourcePath = 'supportChats') {
  const lookupKey = `${sourcePath}:${userId}`;
  const conversation = sAllUsers.find(item => item.conversationKey === lookupKey) ||
    sAllUsers.find(item => item.uid === userId && item.sourcePath === sourcePath);
  selectedUserId = userId;
  selectedUserEmail = conversation?.email || email || userId;
  selectedConversationType = forcedType || conversation?.type || 'support';
  selectedChatSource = conversation?.sourcePath || sourcePath || 'supportChats';
  selectedConversationKey = conversation?.conversationKey || lookupKey;
  document.getElementById('s-header-ava').innerHTML = sAvaHtml(selectedUserEmail, 30);
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

  const chatRef = window.firebaseRef(window.firebaseDb, `${selectedChatSource}/${userId}/messages`);
  detachChatListener = window.firebaseOnValue(chatRef, (snapshot) => {
    const data = snapshot.val() || {};
    const box = document.getElementById('chatMessages');
    const msgs = Object.entries(data)
      .map(([key, val]) => ({ key, ...(val || {}) }))
      .sort((a, b) => (a.time || 0) - (b.time || 0));

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
      const isOut = sIsSupportRole(role);
      const senderName = sGetSenderName(msg);
      const senderColor = sGetColor(senderName);
      const time = sFormatTime(msg.time);
      const roleLabel = sRoleLabel(role);
      const roleClass = sRoleClass(role);

      const row = document.createElement('div');
      row.className = 's-msg-row ' + (isOut ? 's-out' : 's-in');
      row.dataset.msgKey = msg.key;
      row.dataset.msgText = msg.text || '';

      let quoteHtml = '';
      if (msg.replyTo) {
        const quoted = msgs.find(m => m.key === msg.replyTo);
        if (quoted && !quoted.deleted) {
          quoteHtml = `<div class="s-reply-quote">Reply: ${escHtml(sShortText(quoted.text || '', 70))}</div>`;
        }
      }

      const reactionValues = msg.reactions ? Object.values(msg.reactions) : [];
      const reactionMap = {};
      reactionValues.forEach(val => {
        const token = typeof val === 'string' ? val : val?.emoji;
        if(!token) return;
        reactionMap[token] = (reactionMap[token] || 0) + 1;
      });
      const reactionHtml = Object.keys(reactionMap).length
        ? `<div class="s-reaction-bar">${Object.entries(reactionMap).map(([emoji, count]) =>
            `<div class="s-reaction" onclick="sToggleReaction('${msg.key}','${sEscapeAttr(emoji)}',true)">${escHtml(emoji)}<span class="s-reaction-count">${count}</span></div>`
          ).join('')}</div>`
        : '';

      const encodedReplyText = sEscapeAttr(msg.text || '');
      const actionsHtml = `
        <div class="s-msg-actions">
          <button class="s-msg-act-btn" title="React" onclick="sShowEmojiPicker(event,'${msg.key}')">R</button>
          <button class="s-msg-act-btn" title="Reply" onclick="sSetReply('${msg.key}','${encodedReplyText}',true)">Re</button>
          ${!msg.deleted ? `<button class="s-msg-act-btn danger" title="Delete" onclick="sDeleteMsg('${userId}','${msg.key}')">Del</button>` : ''}
        </div>`;

      const bubbleContent = msg.deleted
        ? '<span class="s-deleted-msg">Message deleted</span>'
        : escHtml(msg.text || '');
      const metaHtml = `<div class="s-msg-meta"><span class="s-msg-sender">${escHtml(senderName)}</span><span class="s-msg-role ${roleClass}">${roleLabel}</span></div>`;

      if (!isOut) {
        row.innerHTML =
          `<div class="s-msg-ava" style="background:${senderColor.bg};color:${senderColor.fg}">${sInitials(senderName)}</div>` +
          `<div class="s-bubble-wrap">` +
            `${metaHtml}` +
            `${quoteHtml}` +
            `<div class="s-bubble">${bubbleContent}</div>` +
            `${reactionHtml}` +
            `<div class="s-msg-time">${time}</div>` +
            `${actionsHtml}` +
          `</div>`;
      } else {
        row.innerHTML =
          `<div class="s-bubble-wrap">` +
            `${metaHtml}` +
            `${quoteHtml}` +
            `<div class="s-bubble">${bubbleContent}</div>` +
            `${reactionHtml}` +
            `<div class="s-msg-time">${time}</div>` +
            `${actionsHtml}` +
          `</div>`;
      }
      box.appendChild(row);
    });

    box.scrollTop = box.scrollHeight;
    sCloseEmojiPicker();
  });
}

function handleSend(){sSendAdminMessage();}

function sSetReply(key, text, encoded = false) {
  const decoded = encoded ? sReadAttr(text) : String(text || '');
  sReplyToKey = key;
  sReplyToText = decoded;
  let preview = document.getElementById('s-reply-preview');
  if (!preview) {
    preview = document.createElement('div');
    preview.id = 's-reply-preview';
    preview.className = 's-reply-preview';
    const inputBar = document.querySelector('.s-input-bar');
    if(!inputBar) return;
    inputBar.insertBefore(preview, inputBar.firstChild);
  }
  preview.innerHTML = `<span class="s-reply-preview-text">Replying to: ${escHtml(sShortText(decoded, 80))}</span><button class="s-reply-preview-close" onclick="sClearReply()">x</button>`;
  document.getElementById('adminMessage').focus();
}

function sClearReply() {
  sReplyToKey = null;
  sReplyToText = '';
  const preview = document.getElementById('s-reply-preview');
  if (preview) preview.remove();
}

async function sDeleteMsg(userId, msgKey) {
  if (!confirm('Delete this message?')) return;
  try {
    const role = sGetCurrentSupportRole();
    const senderName = window.currentUserProfile?.username || window.currentUserProfile?.email || sRoleLabel(role);
    const root = selectedChatSource || 'supportChats';
    const ref = window.firebaseRef(window.firebaseDb, `${root}/${userId}/messages/${msgKey}`);
    await window.firebaseSet(ref, {
      deleted: true,
      sender: 'admin',
      senderRole: role,
      senderName,
      senderId: window.currentUserProfile?.uid || 'admin-web',
      time: Date.now(),
      text: ''
    });
    showToast('Message deleted');
  } catch (err) {
    console.error('Delete msg error:', err);
    showToast('Failed to delete message');
  }
}

function sShowEmojiPicker(event, msgKey) {
  event.stopPropagation();
  sCloseEmojiPicker();
  sEmojiPickerTarget = msgKey;

  const picker = document.createElement('div');
  picker.className = 's-emoji-picker';
  picker.id = 's-emoji-picker';
  picker.style.position = 'fixed';
  picker.style.zIndex = '9999';

  S_QUICK_REACTIONS.forEach(emoji => {
    const btn = document.createElement('button');
    btn.textContent = emoji;
    btn.onclick = (e) => { e.stopPropagation(); sToggleReaction(msgKey, emoji); sCloseEmojiPicker(); };
    picker.appendChild(btn);
  });

  document.body.appendChild(picker);

  // Position AFTER appending so offsetHeight is known
  const rect = event.target.getBoundingClientRect();
  const pickerH = picker.offsetHeight || 80;
  const pickerW = picker.offsetWidth || 180;
  const top = rect.top - pickerH - 8;
  const left = Math.min(rect.left, window.innerWidth - pickerW - 8);
  picker.style.top = Math.max(8, top) + 'px';
  picker.style.left = Math.max(8, left) + 'px';

  setTimeout(() => document.addEventListener('click', sCloseEmojiPicker, { once: true }), 0);
}

function sCloseEmojiPicker() {
  const p = document.getElementById('s-emoji-picker');
  if (p) p.remove();
}

async function sToggleReaction(msgKey, emoji, encoded = false) {
  if (!selectedUserId) return;
  try {
    const token = encoded ? sReadAttr(emoji) : emoji;
    const actorRole = sGetCurrentSupportRole();
    const actorKey = window.currentUserProfile?.uid ? `staff_${window.currentUserProfile.uid}` : `staff_${actorRole}`;
    const root = selectedChatSource || 'supportChats';
    const reactionRef = window.firebaseRef(window.firebaseDb, `${root}/${selectedUserId}/messages/${msgKey}/reactions/${actorKey}`);
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
  if (sReplyToKey) { payload.replyTo = sReplyToKey; }
  window.firebaseSet(newMsg, payload)
    .then(() => { input.value = ''; input.focus(); sClearReply(); sUpdateComposerState(); })
    .catch(err => { console.error('Support send failed:', err); showToast('Failed to send message.'); });
}

sSetTypeFilter('all');
sRefreshRoleBadges();
sUpdateComposerState();

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
 
function showToast(msg){
  const t=document.getElementById('toast');
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

function canViewTeam(){
  return ['Super Admin','Admin'].includes(currentRole());
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
  const saveBtn = document.getElementById('campaign-save-btn');
  if(key){
    const campaign = campaigns.find(c => c.key === key);
    if(campaign){
      titleField.value = campaign.title || '';
      descField.value = campaign.description || '';
      goalField.value = campaign.goal || '';
      dateField.value = campaign.date && campaign.date !== 'No end date' ? campaign.date : '';
      catField.value = campaign.cat || '';
      saveBtn.textContent = 'Save Campaign';
      document.querySelector('#modal-campaign .modal-title').textContent = 'Edit campaign';
    }
  } else {
    titleField.value = '';
    descField.value = '';
    goalField.value = '';
    dateField.value = '';
    catField.value = '';
    saveBtn.textContent = 'Create Campaign →';
    document.querySelector('#modal-campaign .modal-title').textContent = 'Create first campaign';
  }
  openModal('modal-campaign');
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
        <div style="width:38px;height:38px;border-radius:8px;background:var(--navy3);display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0">${getCategoryIcon(c.cat)}</div>
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


