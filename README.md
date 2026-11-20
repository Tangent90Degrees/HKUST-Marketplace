# HKUST Campus Marketplace

Group 40's Java 17+ and JavaFX desktop marketplace has buyer, seller, and administrator portals. The Java service validates credentials, roles, and record ownership independently of the screens.

## Run and test on Windows

Install a full JDK 17 or newer. Run these commands from the directory containing `pom.xml`.

```powershell
java -version
javac -version
.\mvnw.cmd clean test
.\mvnw.cmd javafx:run
```

The Maven wrapper downloads Maven and dependencies on the first run. If Maven is already installed, use `mvn clean test` and `mvn javafx:run`. On macOS or Linux, use `./mvnw` in place of `.\mvnw.cmd`. JavaFX requires a desktop session.

To include the JavaFX portal smoke test, run `.\mvnw.cmd "-Dmarketplace.uiSmoke=true" test` on a desktop. The default test run exercises the service without opening a desktop session.

## Demo accounts

| Portal | Username | Demo password |
| --- | --- | --- |
| Admin | `admin` | `admin123` |
| Buyer | `alice` or `bob` | `password1` |
| Seller | `charlie` or `diana` | `password1` |

Seeded credentials are for the local demonstration. The password policy for registration and changes requires at least eight characters, an uppercase letter, a lowercase letter, a digit, and a special character that is neither a letter, digit, nor whitespace. Confirm passwords wherever the form asks. Passwords are stored as PBKDF2-HMAC-SHA256 hashes with individual random salts.

## Suggested grading walkthrough

1. Sign in as a seller, create a listing, and check that My Listings shows Pending Review. Sell uses a positive HK-dollar price. Lend asks for seconds and accepts a minimum of five seconds. Up to six images are supported, each up to 5 MB.
2. Sign in as admin and approve it. For a second listing, reject it with a reason and verify the seller receives the reason.
3. Sign in as a buyer. Search for the approved listing and open it to count a view. Open chat, send text, and make an offer or request, or claim a giveaway. Sign back in as the seller to respond. Reopen the same thread as the buyer to verify retained messages.
4. Mark an accepted purchase or giveaway complete in My Deals, or return an active borrowing. The app applies automatic returns at expiry while running and catches up after restart. Date and time inputs use Hong Kong local time. Borrowing reminders appear once within one day of the due time.
5. Submit a review from a completed deal. Sign in as the seller to reply or flag it. Verify the reply in the buyer's notifications. As admin, filter deals and ratings, then hide the review and verify the public average reflects visible ratings only.

## Storage and source layout

The desktop app uses a local snapshot file at `.hkust-marketplace/marketplace.data` in the operating-system user profile. For a separate demonstration dataset, specify `marketplace.data` as a JVM system property pointing to a new path. Accounts, photos, proposals, deals, chat, reviews, reports, and notices are kept in the snapshot. The app uses a single running Java service instance for all portals.

- `src/main/java/com/hkust/marketplace` contains models, services, and UI controllers.
- `src/main/resources` contains JavaFX views and shared styles.
- `src/test/java` contains the JUnit tests.
