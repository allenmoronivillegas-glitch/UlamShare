// Load users (left side)
function loadUsers() {
  const userList = document.getElementById("userList");

  window.firebaseOnValue(
  window.firebaseRef(window.firebaseDb, "supportChats"),
  (snapshot) => {

      userList.innerHTML = "";

      snapshot.forEach(userSnap => {
        const userId = userSnap.key;
        const userData = userSnap.val();

        const div = document.createElement("div");
        div.innerText = userData.email || userId;
        div.style.padding = "10px";
        div.style.cursor = "pointer";

        div.onclick = () => openChat(userId);

        userList.appendChild(div);
      });

    });
}
let selectedUserId = null;

function openChat(userId) {
  selectedUserId = userId;

  const chatBox = document.getElementById("chatMessages");

 window.firebaseOnValue(
  window.firebaseRef(window.firebaseDb, "supportChats/" + userId + "/messages"),
  (snapshot) => {

      chatBox.innerHTML = "";

      snapshot.forEach(msgSnap => {
        const msg = msgSnap.val();

        const div = document.createElement("div");
        div.innerText = msg.text;

        if (msg.sender === "admin") {
          div.style.textAlign = "right";
        } else {
          div.style.textAlign = "left";
        }

        chatBox.appendChild(div);``
      });

    });
}
function sendAdminMessage() {
  const input = document.getElementById("adminMessage");
  const text = input.value.trim();

  if (!text || !selectedUserId) return;

  const ref = window.firebaseRef(
    window.firebaseDb,
    "supportChats/" + selectedUserId + "/messages"
  );

  window.firebasePush(ref, {
    text: text,
    sender: "admin",
    time: Date.now()
  });

  input.value = "";
}
loadUsers();