const { GoogleAuth } = require("google-auth-library");

const PROJECT_ID = "voice-note-c1f8e";

exports.handler = async (event) => {
  if (event.httpMethod !== "POST") {
    return { statusCode: 405, body: "Method not allowed" };
  }

  try {
    const { token, message, from } = JSON.parse(event.body);
    if (!token || !message) {
      return { statusCode: 400, body: JSON.stringify({ error: "token aur message chahiye" }) };
    }

    // Service account Netlify environment variable se lao
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
      return { statusCode: 500, body: JSON.stringify({ error: err }) };
    }

    return {
      statusCode: 200,
      headers: { "Access-Control-Allow-Origin": "*" },
      body: JSON.stringify({ success: true })
    };

  } catch (e) {
    return { statusCode: 500, body: JSON.stringify({ error: e.message }) };
  }
};
