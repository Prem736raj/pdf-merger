# Privacy Policy for PDF Merger Pro

**Last Updated:** October 3, 2026

This Privacy Policy applies to the **PDF Merger Pro** mobile application (Package Name: `com.mergepdf.inone`) developed by Prem Raj ("we", "our", or "us"). We are committed to protecting your privacy and ensuring you have a secure experience when using our application.

---

## 1. Summary — 100% On-Device Processing

- **No File Uploads:** All PDF document processing, including merging, reordering, page deletion, and encryption, is performed **100% locally on your device**.
- **No Document Tracking:** We never upload, inspect, transmit, or store your documents, page contents, passwords, or filenames on any external server or cloud service.
- **Zero Account Required:** You do not need to register, log in, or provide any personal information to use the app.

---

## 2. Information We Access and How It Is Used

### A. PDF Documents and Files
- **Storage Access Framework (SAF):** The app only accesses PDF files that you explicitly choose through the Android system file picker (`OpenMultipleDocuments` / `CreateDocument`).
- **Temporary Cache:** During processing, temporary file fragments may be cached locally in your device's private app cache directory (`context.cacheDir`). These temporary files are strictly inaccessible to other apps and are automatically deleted immediately upon task completion or cancellation.

### B. Device & Network Information
- The app requests `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` solely to load advertisements and obtain user privacy consent via Google Mobile Ads SDK and Google User Messaging Platform (UMP).

---

## 3. Third-Party Services and Advertising

We integrate the following third-party services to support the application:

### Google AdMob & Google Mobile Ads SDK
- We display banner and interstitial advertisements provided by **Google AdMob** (Google LLC).
- AdMob may collect and process pseudonymous identifiers (such as the Google Advertising ID / `AD_ID`), general device information, and diagnostic metrics to deliver advertisements and detect fraud.
- For users in the European Economic Area (EEA), the UK, and Switzerland, we utilize Google's **User Messaging Platform (UMP)** to request GDPR-compliant consent before serving personalized or non-personalized ads.
- You can manage or revoke your consent at any time inside the app via **Settings → Privacy & Ad Consent Preferences**, or through your Android device settings (**Settings → Google → Ads**).
- For more information on how Google handles data in advertising, please visit: [How Google uses information from sites or apps that use our services](https://policies.google.com/technologies/partner-sites).

---

## 4. Permissions Used

| Permission | Purpose |
|------------|---------|
| `android.permission.INTERNET` | Required for Google AdMob advertising and UMP consent updates. |
| `android.permission.ACCESS_NETWORK_STATE` | Used by Google AdMob to verify network availability before making ad requests. |

The app does **not** request broad storage permissions (such as `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`). File access is strictly scoped via Android's native Storage Access Framework.

---

## 5. Data Security

Because all PDF merging and password encryption operations are executed locally on your device using on-device cryptography, your confidential files never leave your phone. Passwords entered for PDF encryption are held only in memory during the merge operation and are never logged, stored, or transmitted.

---

## 6. Children's Privacy

PDF Merger Pro does not knowingly collect any personally identifiable information from children under the age of 13. The app is a general utility tool intended for general audiences.

---

## 7. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Any changes will be posted in this document and updated in the application and Google Play Store listing.

---

## 8. Contact Us

If you have any questions, concerns, or feedback regarding this Privacy Policy, please contact us:

- **Developer:** Prem Raj
- **GitHub Repository:** [https://github.com/Prem736raj/pdf-merger](https://github.com/Prem736raj/pdf-merger)
- **Issues & Inquiries:** [https://github.com/Prem736raj/pdf-merger/issues](https://github.com/Prem736raj/pdf-merger/issues)
