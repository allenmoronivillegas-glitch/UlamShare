import { initializeApp } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-app.js";
import { getDatabase } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-database.js";

const firebaseConfig = {
  apiKey: "AIzaSyAlFuluvqONN0GfRfkv1CF85rGdm75dOoU",
  authDomain: "ulamshare-4f2b9.firebaseapp.com",
  databaseURL: "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "ulamshare-4f2b9",
  storageBucket: "ulamshare-4f2b9.firebasestorage.app",
  messagingSenderId: "521750995424",
  appId: "1:521750995424:web:3f26ee1e971685409ecb85",
  measurementId: "G-67NLB4FRVF"
};

const app = initializeApp(firebaseConfig);
const db = getDatabase(app);

export { db };