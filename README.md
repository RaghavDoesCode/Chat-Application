# WeConnect — JavaFX Chat Application

A desktop chat application built in Java (JavaFX) using Firebase as the backend.
Recreated from the original Android WeConnect project for DAA coursework demonstration.

---

## Tech Stack
- **Java 17 + JavaFX 21** — UI
- **Firebase Admin SDK** — Auth, Realtime Database
- **Maven** — Build & dependency management

---

## DAA Concepts Demonstrated

| Concept | Where |
|---|---|
| HashMap — O(1) insert/lookup | Message body, user data storage |
| Tree traversal | Firebase JSON path addressing |
| Fan-out write (Space-Time Tradeoff) | Dual-write in `sendMessage()` |
| Directed Graph | Friend request system (nodes=users, edges=requests) |
| Linear Search O(n) | `searchUsers()` — and why it's a limitation |
| Greedy ordering | Firebase push() keys (timestamp-prefixed) |

---

## Project Structure

```
WeConnect-javafx/
├── pom.xml                          ← Maven config, all dependencies
├── src/main/java/com/WeConnect/
│   ├── Main.java                    ← Entry point, launch JavaFX
│   ├── controllers/
│   │   ├── LoginController.java     ← Login screen logic
│   │   ├── RegisterController.java  ← Registration logic
│   │   └── DashboardController.java ← Friends, chat, search
│   ├── models/
│   │   ├── User.java
│   │   └── Message.java
│   └── services/
│       └── FirebaseService.java     ← ALL Firebase operations (core file)
└── src/main/resources/com/WeConnect/
    ├── fxml/
    │   ├── login.fxml
    │   ├── register.fxml
    │   └── dashboard.fxml
    └── css/
        └── style.css
```

---

## Setup Instructions

### Step 1 — Firebase Project Setup (5 minutes)

1. Go to [https://console.firebase.google.com](https://console.firebase.google.com)
2. Click **"Add Project"** → name it `WeConnect`
3. In the left sidebar: **Build → Authentication**
   - Click **"Get started"**
   - Enable **Email/Password** provider
4. In the left sidebar: **Build → Realtime Database**
   - Click **"Create database"**
   - Choose any region → Start in **test mode**
   - Copy the database URL (looks like `https://WeConnect-xxxxx-default-rtdb.firebaseio.com`)
5. In the left sidebar: **Project Settings (gear icon) → Service Accounts**
   - Click **"Generate new private key"**
   - Save the downloaded JSON as `serviceAccountKey.json`

### Step 2 — Configure the Project

1. Copy `serviceAccountKey.json` into:
   ```
   src/main/resources/com/WeConnect/serviceAccountKey.json
   ```

2. Open `FirebaseService.java` and replace line ~47:
   ```java
   .setDatabaseUrl("https://YOUR-PROJECT-ID-default-rtdb.firebaseio.com")
   ```
   with your actual database URL from Step 1.

### Step 3 — Run

Make sure you have **Java 17+** and **Maven** installed.

```bash
cd WeConnect-javafx
mvn javafx:run
```

That's it. The login screen will appear.

---

## How to Demo (50% completion)

✅ Register two accounts (open app twice or use two machines)
✅ Login with each account
✅ Send a friend request from Account A → Account B
✅ Accept the request on Account B
✅ Open chat and send messages — they appear in real time on both sides

---

## How the Chat Works (for DAA explanation)

```
User A sends a message
        ↓
HashMap built: { message, from, to, time, seen }
        ↓
Fan-out write (Space-Time Tradeoff):
  messages/A_uid/B_uid/msgID  ← A's copy
  messages/B_uid/A_uid/msgID  ← B's copy  (2x storage, O(1) reads)
        ↓
Firebase notifies B's ChildEventListener
        ↓
B's UI updates in real-time (no polling)
```
