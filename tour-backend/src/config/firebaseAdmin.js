const { initializeApp, applicationDefault, getApps } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");

// Firebase project "tour-moment" (Auth only - this backend still uses MongoDB for everything
// else, not Firestore). Deliberately NOT the same GCP project this backend runs in
// (Cloud Run's project is "tour-momen") - projectId is passed explicitly so ID token
// audience/issuer checks are validated against the right project regardless of which project's
// ambient credentials Cloud Run hands us.
//
// applicationDefault() picks up Cloud Run's ambient service account automatically (no key file
// needed) - verifyIdToken() with the default checkRevoked=false only validates the token's
// signature against Google's public certs + its aud/iss claims against projectId below, it does
// not call any Firebase API under that credential's identity, so the mismatched-project
// credential is fine for this use case.
//
// firebase-admin v14 uses the modular API (require("firebase-admin/app") /
// require("firebase-admin/auth")) - the old `admin.initializeApp()`/`admin.apps`/
// `admin.credential` top-level shape from earlier major versions no longer exists.
if (!getApps().length) {
  initializeApp({
    credential: applicationDefault(),
    projectId: "tour-moment",
  });
}

module.exports = getAuth();
