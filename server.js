const express = require("express");
const fetch = require("node-fetch");
const { GoogleAuth } = require("google-auth-library");

const app = express();
app.use(express.json());
app.use((req, res, next) => {
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Headers", "Content-Type");
  next();
});

const PROJECT_ID = "voice-note-c1f8e";

const auth = new GoogleAuth({
  keyFile: "service-account.json",
  scopes: ["https://www.googleapis.com/auth/firebase.messaging"]
});

app.post("/send", async (req, res) => {
  try {
    const { token, message, from } = req.body;
    if (!token || !message) {
      return res.status(400).json({ error: "token aur message chahiye" });
    }

    const accessToken = await auth.getAccessToken();

    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${PROJECT_ID}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          message: {
            token: token,
            data: { message: message, from: from || "Kisi ne" },
            notification: {
              title: (from || "Kisi ne") + " bula rahe hain 🔔",
              body: message
            },
            android: {
              priority: "high",
              notification: { sound: "default", channel_id: "voice_ping" }
            }
          }
        })
      }
    );

    if (!response.ok) {
      const err = await response.text();
      console.error("FCM error:", err);
      return res.status(500).json({ error: err });
    }

    console.log("Sent to:", token.substring(0, 20) + "...");
    res.json({ success: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message });
  }
});

app.listen(3000, () => console.log("Server chal raha hai port 3000 pe"));
