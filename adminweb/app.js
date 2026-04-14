// Listen to all users (chat list)
db.collection("support_chats")
  .onSnapshot(snapshot => {
    console.clear();

    snapshot.forEach(doc => {
      const userId = doc.id;
      const user = doc.data();

      console.log("👤 User:", user.email);

      // Load messages ONCE per user (no nested spam)
      loadMessages(userId);
    });
  });

// Separate function (VERY IMPORTANT)
function loadMessages(userId) {
  db.collection("support_chats")
    .doc(userId)
    .collection("messages")
    .orderBy("timestamp")
    .onSnapshot(msgSnap => {

      console.log("💬 Messages for:", userId);

      msgSnap.docChanges().forEach(change => {
        if (change.type === "added") {
          const data = change.doc.data();
          console.log(data.sender + ":", data.text);
        }
      });

    });
}

// Admin reply
function sendReply(userId, messageText) {
  db.collection("support_chats")
    .doc(userId)
    .collection("messages")
    .add({
      text: messageText,
      sender: "admin",
      timestamp: Date.now()
    });
}
function openChat(userId) {

  db.collection("support_chats")
    .doc(userId)
    .collection("messages")
    .orderBy("timestamp")
    .onSnapshot(snapshot => {

      const chatBox = document.getElementById("chatBox");
      chatBox.innerHTML = "";

      snapshot.forEach(doc => {
        const msg = doc.data();

        const div = document.createElement("div");
        div.innerText = msg.text;

        if (msg.sender === "admin") {
          div.style.textAlign = "right";
        } else {
          div.style.textAlign = "left";
        }

        chatBox.appendChild(div);
      });

    });
}