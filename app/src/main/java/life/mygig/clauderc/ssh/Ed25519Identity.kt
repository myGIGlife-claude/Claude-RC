package life.mygig.clauderc.ssh

import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil

/** An Ed25519 key pair built from a 32-byte seed, in the formats JSch and OpenSSH use. */
class Ed25519Identity(seed: ByteArray) {

    private val private = Ed25519PrivateKeyParameters(seed, 0)

    /** The SSH wire-format public key blob. */
    val publicBlob: ByteArray = OpenSSHPublicKeyUtil.encodePublicKey(private.generatePublicKey())

    /** `ssh-ed25519 AAAA… comment`, ready for authorized_keys. */
    fun authorizedKey(comment: String): String =
        "ssh-ed25519 " + Base64.getEncoder().encodeToString(publicBlob) + " " + comment

    /** The private key as an unencrypted OpenSSH PEM, for JSch.addIdentity. */
    fun privatePem(): ByteArray {
        val body = Base64.getMimeEncoder(70, "\n".toByteArray())
            .encodeToString(OpenSSHPrivateKeyUtil.encodePrivateKey(private))
        return ("-----BEGIN OPENSSH PRIVATE KEY-----\n$body\n-----END OPENSSH PRIVATE KEY-----\n")
            .toByteArray()
    }
}
