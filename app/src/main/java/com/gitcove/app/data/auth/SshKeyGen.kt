package com.gitcove.app.data.auth

import com.gitcove.app.data.store.AuthStore
import com.gitcove.app.data.store.SshKey
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.File
import java.util.Base64

/**
 * SSH 密钥生成（功能 45）：
 * 支持生成 RSA 4096 与 ECDSA 256；Ed25519 支持通过导入 OpenSSH 私钥实现。
 */
object SshKeyGen {

    enum class KeyType(val label: String) {
        RSA4096("RSA 4096"),
        ECDSA256("ECDSA 256")
    }

    fun generate(auth: AuthStore, name: String, type: KeyType, comment: String): Result<SshKey> = try {
        require(name.isNotBlank()) { "密钥名称不能为空" }
        val jsch = JSch()
        val kp = when (type) {
            KeyType.RSA4096 -> KeyPair.genKeyPair(jsch, KeyPair.RSA, 4096)
            KeyType.ECDSA256 -> KeyPair.genKeyPair(jsch, KeyPair.ECDSA, 256)
        }
        val safeName = name.trim().replace(Regex("\\s+"), "_")
        val privFile = File(auth.sshDir, safeName)
        privFile.outputStream().use { kp.writePrivateKey(it) }
        val typeStr = when (type) {
            KeyType.RSA4096 -> "ssh-rsa"
            KeyType.ECDSA256 -> "ecdsa-sha2-nistp256"
        }
        val b64 = Base64.getEncoder().encodeToString(kp.getPublicKeyBlob())
        val publicKey = "$typeStr $b64 ${comment.trim()}"
        val key = auth.saveSshKey(safeName, privFile.readText(), publicKey)
        kp.dispose()
        Result.success(key)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
