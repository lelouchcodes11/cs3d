package android.security.keystore;

import java.security.spec.AlgorithmParameterSpec;

public final class KeyGenParameterSpec implements AlgorithmParameterSpec {
    private final String mKeystoreAlias;
    private final int mPurposes;
    private final String[] mBlockModes;
    private final String[] mEncryptionPaddings;
    private final int mKeySize;

    private KeyGenParameterSpec(Builder b) {
        mKeystoreAlias = b.mKeystoreAlias;
        mPurposes = b.mPurposes;
        mBlockModes = b.mBlockModes;
        mEncryptionPaddings = b.mEncryptionPaddings;
        mKeySize = b.mKeySize;
    }

    public String getKeystoreAlias() { return mKeystoreAlias; }
    public int getPurposes() { return mPurposes; }
    public String[] getBlockModes() { return mBlockModes; }
    public String[] getEncryptionPaddings() { return mEncryptionPaddings; }
    public int getKeySize() { return mKeySize; }

    public static final class Builder {
        private final String mKeystoreAlias;
        private final int mPurposes;
        private String[] mBlockModes = new String[0];
        private String[] mEncryptionPaddings = new String[0];
        private int mKeySize = -1;

        public Builder(String keystoreAlias, int purposes) {
            mKeystoreAlias = keystoreAlias;
            mPurposes = purposes;
        }

        public Builder setBlockModes(String... blockModes) { mBlockModes = blockModes; return this; }
        public Builder setEncryptionPaddings(String... paddings) { mEncryptionPaddings = paddings; return this; }
        public Builder setSignaturePaddings(String... paddings) { return this; }
        public Builder setDigests(String... digests) { return this; }
        public Builder setKeySize(int keySize) { mKeySize = keySize; return this; }
        public Builder setUserAuthenticationRequired(boolean required) { return this; }
        public Builder setRandomizedEncryptionRequired(boolean required) { return this; }
        public Builder setInvalidatedByBiometricEnrollment(boolean invalidateKey) { return this; }
        public Builder setUnlockedDeviceRequired(boolean unlockedDeviceRequired) { return this; }
        public Builder setIsStrongBoxBacked(boolean isStrongBoxBacked) { return this; }
        public Builder setAlgorithmParameterSpec(AlgorithmParameterSpec spec) { return this; }
        public Builder setAttestationChallenge(byte[] attestationChallenge) { return this; }
        public Builder setUserAuthenticationValidityDurationSeconds(int seconds) { return this; }
        public Builder setKeyValidityStart(java.util.Date startDate) { return this; }
        public Builder setKeyValidityEnd(java.util.Date endDate) { return this; }
        public Builder setCertificateSubject(javax.security.auth.x500.X500Principal subject) { return this; }
        public Builder setCertificateSerialNumber(java.math.BigInteger serialNumber) { return this; }
        public Builder setCertificateNotBefore(java.util.Date notBefore) { return this; }
        public Builder setCertificateNotAfter(java.util.Date notAfter) { return this; }

        public KeyGenParameterSpec build() {
            return new KeyGenParameterSpec(this);
        }
    }
}
