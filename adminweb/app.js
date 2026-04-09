import { db } from "./firebase.js";
import { ref, set, onValue } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-database.js";

// 🔁 reference to database
const messageRef = ref(db, "test/message");

// ✅ AUTO LISTEN (REALTIME)
onValue(messageRef, (snapshot) => {
  const data = snapshot.val();
  console.log("Updated data:", data);
  const outputElement = document.getElementById("output");

  if (outputElement && data && data.text) {
    outputElement.innerText = data.text;
  }
});

// ✅ AUTO SEND (optional test)
set(messageRef, {
  text: "Hello automatic!",
  time: Date.now()
});