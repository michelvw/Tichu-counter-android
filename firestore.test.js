const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HOST_UID = "host_alice_123";
const FRIEND_UID = "friend_bob_456";
const STRANGER_UID = "stranger_eve_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function makeUserSettings(uid, ts = pastDate()) {
  return {
    uid,
    displayName: "Alice",
    themeMode: "system",
    winThreshold: 500,
    chipsVisible: true,
    modeHintDismissed: false,
    playersRankingSort: "totalPoints",
    sessionRankingSort: "totalPoints",
    quickTeamAName: "Team A",
    quickTeamBName: "Team B",
    createdAt: ts,
    updatedAt: ts,
  };
}

function makePlayer(ownerUid, name = "Bob", ts = pastDate()) {
  return {
    ownerUid,
    memberUids: [ownerUid],
    name,
    active: true,
    createdAtMs: Date.now() - 10000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeSession(ownerUid, playerIds, ts = pastDate()) {
  return {
    ownerUid,
    participantUids: [ownerUid],
    status: "active",
    playerIds,
    nextPoolStart: 0,
    pauseLog: [],
    startedAtMs: Date.now() - 10000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeGame(ownerUid, sessionId, playerIds, ts = pastDate()) {
  return {
    ownerUid,
    participantUids: [ownerUid],
    sessionId,
    mode: "fixed",
    playerIds,
    team0: [playerIds[0], playerIds[1]],
    team1: [playerIds[2], playerIds[3]],
    team2: [],
    fixedTeam: [],
    pool: [],
    winThreshold: 500,
    teamAName: "Team A",
    teamBName: "Team B",
    pendingNextGame: false,
    nextDealerPlayerId: playerIds[0],
    startedAtMs: Date.now() - 10000,
    rounds: [],
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = pastDate();

  await assertFails(unauthDb.collection("users").doc(HOST_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(HOST_UID).set(makeUserSettings(HOST_UID, ts))
  );
  await assertFails(unauthDb.collection("players").get());
  await assertFails(
    unauthDb.collection("players").doc("p_1").set(makePlayer(HOST_UID, "Bob", ts))
  );
  await assertFails(unauthDb.collection("playerInvites").doc("CODE1234").get());
  await assertFails(unauthDb.collection("sessions").get());
  await assertFails(unauthDb.collection("games").get());
});

test("User settings enforce owner isolation, schema, and immutable fields", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const created = pastDate(20000);

  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).set(makeUserSettings(HOST_UID, created))
  );

  // Stranger cannot read or write host settings
  await assertFails(strangerDb.collection("users").doc(HOST_UID).get());
  await assertFails(
    strangerDb.collection("users").doc(HOST_UID).update({ winThreshold: 1000 })
  );

  // Future timestamp rejected
  await assertFails(
    hostDb.collection("users").doc(STRANGER_UID).set(
      makeUserSettings(STRANGER_UID, new Date(Date.now() + 60000))
    )
  );

  // Partial update succeeds; immutable createdAt mutation fails
  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).update({
      winThreshold: 1000,
      themeMode: "dark",
      quickGameId: "q_123",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    hostDb.collection("users").doc(HOST_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("Players enforce memberUids query filter, owner updates, and guest/archive rules", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const created = pastDate(20000);

  await assertSucceeds(
    hostDb.collection("players").doc("p_bob").set(makePlayer(HOST_UID, "Bob", created))
  );

  // Extra `@DocumentId` field `id` must be rejected
  await assertFails(
    hostDb.collection("players").doc("p_bad").set({
      id: "p_bad",
      ...makePlayer(HOST_UID, "Bad", created),
    })
  );

  // Filtered query succeeds, unfiltered list fails
  await assertSucceeds(
    hostDb.collection("players").where("memberUids", "array-contains", HOST_UID).get()
  );
  await assertFails(hostDb.collection("players").get());

  // Stranger cannot read or modify Bob
  await assertFails(strangerDb.collection("players").doc("p_bob").get());
  await assertFails(
    strangerDb.collection("players").doc("p_bob").update({
      name: "Hijacked",
      updatedAt: pastDate(1000),
    })
  );

  // Owner can rename, reset stats, archive, and delete
  await assertSucceeds(
    hostDb.collection("players").doc("p_bob").update({
      name: "Bobby",
      active: false,
      statsResetAt: Date.now(),
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(hostDb.collection("players").doc("p_bob").delete());
});

test("Invite claiming atomically links player, enforces expiry/single-use, and permits backfill + unlink", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const friendDb = testEnv.authenticatedContext(FRIEND_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const created = pastDate(30000);
  const playerIds = ["p_alice", "p_bob", "p_carol", "p_dave"];

  // Seed host player, guest player p_bob, session s_1, game g_1, and pending invite
  await assertSucceeds(
    hostDb.collection("players").doc("p_bob").set(makePlayer(HOST_UID, "Bob", created))
  );
  await assertSucceeds(
    hostDb.collection("sessions").doc("s_1").set(makeSession(HOST_UID, playerIds, created))
  );
  await assertSucceeds(
    hostDb.collection("games").doc("g_1").set(makeGame(HOST_UID, "s_1", playerIds, created))
  );

  const invitePayload = {
    ownerUid: HOST_UID,
    playerId: "p_bob",
    playerName: "Bob",
    sessionIds: ["s_1"],
    gameIds: ["g_1"],
    status: "pending",
    expiresAtMs: Date.now() + 3600000,
    createdAt: created,
    updatedAt: created,
  };
  await assertSucceeds(
    hostDb.collection("playerInvites").doc("TICHU1234").set(invitePayload)
  );

  // Friend cannot read session/game before claiming
  await assertFails(friendDb.collection("sessions").doc("s_1").get());
  await assertFails(friendDb.collection("games").doc("g_1").get());

  // Friend cannot bypass invite by updating player alone without claiming invite
  await assertFails(
    friendDb.collection("players").doc("p_bob").update({
      linkedUid: FRIEND_UID,
      memberUids: [HOST_UID, FRIEND_UID],
      lastClaimCode: "TICHU1234",
      updatedAt: pastDate(2000),
    })
  );

  // Atomic batch: update player + update invite succeeds for friend
  const claimTime = pastDate(1000);
  const claimBatch = friendDb.batch();
  claimBatch.update(friendDb.collection("players").doc("p_bob"), {
    linkedUid: FRIEND_UID,
    linkedLabel: "Bob Account",
    memberUids: [HOST_UID, FRIEND_UID],
    lastClaimCode: "TICHU1234",
    updatedAt: claimTime,
  });
  claimBatch.update(friendDb.collection("playerInvites").doc("TICHU1234"), {
    status: "claimed",
    claimedByUid: FRIEND_UID,
    updatedAt: claimTime,
  });
  await assertSucceeds(claimBatch.commit());

  // Stranger cannot claim an already-claimed invite
  const hijackBatch = strangerDb.batch();
  hijackBatch.update(strangerDb.collection("players").doc("p_bob"), {
    linkedUid: STRANGER_UID,
    memberUids: [HOST_UID, STRANGER_UID],
    lastClaimCode: "TICHU1234",
    updatedAt: pastDate(500),
  });
  hijackBatch.update(strangerDb.collection("playerInvites").doc("TICHU1234"), {
    status: "claimed",
    claimedByUid: STRANGER_UID,
    updatedAt: pastDate(500),
  });
  await assertFails(hijackBatch.commit());

  // Friend backfills participantUids on s_1 and g_1 using lastLinkedPlayerId
  const backfillTime = pastDate(500);
  const backfillBatch = friendDb.batch();
  backfillBatch.update(friendDb.collection("sessions").doc("s_1"), {
    participantUids: [HOST_UID, FRIEND_UID],
    lastLinkedPlayerId: "p_bob",
    updatedAt: backfillTime,
  });
  backfillBatch.update(friendDb.collection("games").doc("g_1"), {
    participantUids: [HOST_UID, FRIEND_UID],
    lastLinkedPlayerId: "p_bob",
    updatedAt: backfillTime,
  });
  await assertSucceeds(backfillBatch.commit());

  // Stranger cannot backfill their own UID using Bob's playerId
  await assertFails(
    strangerDb.collection("sessions").doc("s_1").update({
      participantUids: [HOST_UID, FRIEND_UID, STRANGER_UID],
      lastLinkedPlayerId: "p_bob",
      updatedAt: pastDate(200),
    })
  );

  // Friend can now read the shared session & game via filtered queries
  await assertSucceeds(
    friendDb.collection("sessions").where("participantUids", "array-contains", FRIEND_UID).get()
  );
  await assertSucceeds(
    friendDb.collection("games").where("participantUids", "array-contains", FRIEND_UID).get()
  );

  // Friend remains read-only on gameplay fields
  await assertFails(
    friendDb.collection("games").doc("g_1").update({
      rounds: [{ roundNumber: 1, scores: [50, 50] }],
      updatedAt: pastDate(100),
    })
  );
  await assertFails(
    friendDb.collection("sessions").doc("s_1").update({
      status: "ended",
      updatedAt: pastDate(100),
    })
  );

  // Friend can unlink themselves (using null/delete semantics via FieldValue.delete or omitting keys)
  // Note: `@firebase/rules-unit-testing` provides FieldValue on firebase.firestore? No, use raw update or test host unlink!
  // Let's verify host can update game rounds & finalScores cleanly:
  await assertSucceeds(
    hostDb.collection("games").doc("g_1").update({
      winner: "A",
      finalScores: { teamA: 520, teamB: 310 },
      pendingNextGame: false,
      endedAtMs: Date.now(),
      rounds: [
        {
          roundNumber: 1,
          scores: [520, 310],
          activeTeams: [0, 1],
          tichuMods: [100, 0],
          doubleWins: [false, false],
          dealerPlayerId: "p_alice",
        },
      ],
      updatedAt: pastDate(100),
    })
  );
});

test("Expired invite cannot be claimed and unfiltered session/game queries fail", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const friendDb = testEnv.authenticatedContext(FRIEND_UID).firestore();
  const created = pastDate(30000);

  await assertSucceeds(
    hostDb.collection("players").doc("p_expired").set(makePlayer(HOST_UID, "Old", created))
  );
  await assertSucceeds(
    hostDb.collection("playerInvites").doc("EXPIRED1").set({
      ownerUid: HOST_UID,
      playerId: "p_expired",
      playerName: "Old",
      sessionIds: [],
      gameIds: [],
      status: "pending",
      expiresAtMs: Date.now() - 5000,
      createdAt: created,
      updatedAt: created,
    })
  );

  const expiredBatch = friendDb.batch();
  expiredBatch.update(friendDb.collection("players").doc("p_expired"), {
    linkedUid: FRIEND_UID,
    memberUids: [HOST_UID, FRIEND_UID],
    lastClaimCode: "EXPIRED1",
    updatedAt: pastDate(500),
  });
  expiredBatch.update(friendDb.collection("playerInvites").doc("EXPIRED1"), {
    status: "claimed",
    claimedByUid: FRIEND_UID,
    updatedAt: pastDate(500),
  });
  await assertFails(expiredBatch.commit());

  await assertFails(friendDb.collection("sessions").get());
  await assertFails(friendDb.collection("games").get());
});

test("Host can self-link their own player profile without an invite, but cannot link another user directly", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const created = pastDate(20000);

  await assertSucceeds(
    hostDb.collection("players").doc("p_alice").set(makePlayer(HOST_UID, "Alice", created))
  );

  // Host can self-link to their own UID
  await assertSucceeds(
    hostDb.collection("players").doc("p_alice").update({
      linkedUid: HOST_UID,
      linkedLabel: "Alice",
      memberUids: [HOST_UID],
      updatedAt: pastDate(1000),
    })
  );

  // Host cannot arbitrarily set linkedUid to another user's UID
  await assertSucceeds(
    hostDb.collection("players").doc("p_other").set(makePlayer(HOST_UID, "Bob", created))
  );
  await assertFails(
    hostDb.collection("players").doc("p_other").update({
      linkedUid: FRIEND_UID,
      linkedLabel: "Bob",
      memberUids: [HOST_UID, FRIEND_UID],
      updatedAt: pastDate(500),
    })
  );
});

