(ns cheffy.util.crypto
  "Encryption and hashing utilities."
  (:import
   (java.util Base64)
   (javax.crypto Mac)
   (javax.crypto.spec SecretKeySpec)))

(defn hmac-sha256-base64
  "Signs given string using HMACSHA256 algorithm initializing it with `secret`.
  and returning the result as Base64 encoded string (URL safe variant with no padding ??).
  Needed for example for AWS Cognito to compute SecretHash.
  See https://docs.aws.amazon.com/cognito/latest/developerguide/signing-up-users-in-your-app.html#cognito-user-pools-computing-secret-hash"
  [secret s]
  (let [alg "HmacSHA256"
        signing-key (SecretKeySpec. (.getBytes secret) alg)
        mac (doto (Mac/getInstance alg)
               (.init signing-key))
        raw-hmac (.doFinal mac (.getBytes s))]
    (.encodeToString (Base64/getEncoder) raw-hmac)))

(comment
  (hmac-sha256-base64 "mysecret" "mystring")
  ;; => "7K9NIoCpt56WlhVKiWSeAyAVwoyjulqQLG66FFrhEAE="
  .)
