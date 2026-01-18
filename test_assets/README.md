# Test Assets

This directory contains test files for Android Hot Update system.

## Files

### APK Files
- **app-v1.2.apk** - Base version APK (version 1.2)
- **app-v1.3.apk** - Updated version APK (version 1.3)

### Patch Files
- **patch_1768749380520.zip** - Standard patch file (unencrypted, signed)
  - Generated from v1.2 to v1.3
  - Contains signature verification (META-INF/)
  
- **test-patch-final.zip** - Test patch file (unencrypted, signed)
  - Alternative patch for testing
  - Contains signature verification
  
- **patch_1768745206791.zip.enc** - Encrypted patch file (AES-256)
  - Encrypted version of the patch
  - Requires password for decryption
  
- **patch_1768745206791.zip.enc.pwd** - Password file for encrypted patch
  - Contains the decryption password
  - Used by HotUpdateHelper to decrypt the patch

## Usage

### Testing Standard Patch
1. Install `app-v1.2.apk`
2. Push `patch_1768749380520.zip` or `test-patch-final.zip` to device
3. Apply patch using HotUpdateHelper
4. Verify app updates to v1.3 functionality

### Testing Encrypted Patch
1. Install `app-v1.2.apk`
2. Push both `patch_1768745206791.zip.enc` and `patch_1768745206791.zip.enc.pwd` to device
3. Apply encrypted patch using HotUpdateHelper
4. System will automatically decrypt using password file
5. Verify app updates to v1.3 functionality

## Security Features Tested

### Signature Verification
- All patches are signed with the same key as the APK
- Signature verification prevents tampering
- If signature is removed or modified, patch will be rejected

### Encryption
- Encrypted patch uses AES-256-GCM encryption
- Password is stored separately for security
- Decryption happens automatically during patch application

### Integrity Check
- SHA-256 hash verification for all patches
- Prevents corrupted or modified patches from being applied
- Hash is verified before and after decryption

## Test Scenarios

1. **Normal Patch Application**
   - Use unencrypted signed patch
   - Verify signature and integrity
   - Apply patch successfully

2. **Encrypted Patch Application**
   - Use encrypted patch with password file
   - Decrypt automatically
   - Verify and apply patch

3. **Tamper Detection**
   - Modify patch content
   - Signature verification should fail
   - Patch should be rejected and cleared

4. **Signature Removal Attack**
   - Remove META-INF/ directory from patch
   - System should detect signature removal
   - Patch should be rejected

5. **Wrong Password**
   - Use encrypted patch with wrong password
   - Decryption should fail
   - Patch should be rejected

## Notes

- All patches are generated using the patch-generator-android tool
- Patches are signed with the same keystore as the APK
- Encrypted patches use AES-256-GCM with random IV
- Password files contain base64-encoded passwords
