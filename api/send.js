const { GoogleAuth } = require("google-auth-library");

const PROJECT_ID = "voice-note-c1f8e";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(200).end();
  if (req.method !== "POST") return res.status(405).end();

  try {
    const { token, message, from } = req.body;
    if (!token || !message)
      return res.status(400).json({ error: "token aur message chahiye" });

    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    const auth = new GoogleAuth({
      credentials: serviceAccount,
      scopes: ["https://www.googleapis.com/auth/firebase.messaging"]
    });

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
            token,
            data: { message, from: from || "Kisi ne" },
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
      return res.status(500).json({ error: err });
    }

    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
}
