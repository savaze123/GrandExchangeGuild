package com.osrs.careerguild.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
import com.osrs.careerguild.BuildConfig
import com.osrs.careerguild.data.local.CareerGuildDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Creates the Room + SQLCipher LOCAL_VAULT database.
 *
 * Security model:
 * 1. A 256-bit AES/GCM key is generated once in Android Keystore (never leaves hardware).
 * 2. A random 32-byte passphrase is generated on first launch, encrypted with that key,
 *    and stored as two SharedPreferences fields (ciphertext + IV).
 * 3. On every subsequent launch the passphrase is decrypted from SharedPreferences using
 *    the Keystore key and passed to SQLCipher's [SupportFactory].
 *
 * This ensures:
 *  - The passphrase is never hard-coded or logged.
 *  - The database cannot be opened on a different device.
 *  - The Keystore key is hardware-backed on Tensor G5.
 *
 * Node: LOCAL_VAULT
 */
object DatabaseModule {

    init {
        try {
            System.loadLibrary("sqlcipher")
        } catch (t: Throwable) {
            android.util.Log.w("DatabaseModule", "SQLCipher loadLibrary warning: ${t.message}")
        }
    }

    private const val KEY_ALIAS      = "career_guild_db_v1"
    private const val PREFS_NAME     = "career_guild_vault"
    private const val PREF_CIPHER    = "db_ct"
    private const val PREF_IV        = "db_iv"
    private const val AES_MODE       = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS   = 128

    fun createDatabase(context: Context): CareerGuildDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = getOrCreatePassphrase(context)
        val factory    = SupportOpenHelperFactory(passphrase)
        passphrase.fill(0) // zero out after handing to SQLCipher

        return Room.databaseBuilder(
            context.applicationContext,
            CareerGuildDatabase::class.java,
            "career_guild.db",
        )
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE jobs ADD COLUMN location TEXT NOT NULL DEFAULT 'New York, NY'")
            database.execSQL("ALTER TABLE jobs ADD COLUMN work_mode TEXT NOT NULL DEFAULT 'Hybrid'")
        }
    }

    val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE jobs ADD COLUMN apply_url TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE jobs ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE jobs ADD COLUMN lat REAL")
            database.execSQL("ALTER TABLE jobs ADD COLUMN lng REAL")
            database.execSQL("ALTER TABLE jobs ADD COLUMN link_status TEXT NOT NULL DEFAULT 'VERIFIED'")
            database.execSQL("ALTER TABLE jobs ADD COLUMN last_verified_at INTEGER")

            database.execSQL("""
                CREATE TABLE IF NOT EXISTS base_resumes (
                    id TEXT NOT NULL PRIMARY KEY,
                    raw_text TEXT NOT NULL,
                    confirmed_json TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())

            database.execSQL("""
                CREATE TABLE IF NOT EXISTS github_repos (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    html_url TEXT NOT NULL,
                    topics TEXT NOT NULL,
                    languages TEXT NOT NULL,
                    pushed_at TEXT,
                    readme_excerpt TEXT,
                    etag TEXT,
                    cached_at INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    // ---------------------------------------------------------------------------
    // Passphrase management via Android Keystore + AES/GCM
    // ---------------------------------------------------------------------------

    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingCt = prefs.getString(PREF_CIPHER, null)
        val existingIv = prefs.getString(PREF_IV, null)

        if (existingCt != null && existingIv != null) {
            return decrypt(
                ciphertextB64 = existingCt,
                ivB64         = existingIv,
            )
        }

        // First launch: generate + persist
        val newPassphrase = java.security.SecureRandom().generateSeed(32)
        val (ciphertextB64, ivB64) = encrypt(newPassphrase)

        prefs.edit()
            .putString(PREF_CIPHER, ciphertextB64)
            .putString(PREF_IV, ivB64)
            .apply()

        return newPassphrase
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // background decryption on launch
                .build()
        )
        return keyGen.generateKey()
    }

    private fun encrypt(plaintext: ByteArray): Pair<String, String> {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val ct = cipher.doFinal(plaintext)
        return android.util.Base64.encodeToString(ct, android.util.Base64.NO_WRAP) to
               android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP)
    }

    private fun decrypt(ciphertextB64: String, ivB64: String): ByteArray {
        val iv         = android.util.Base64.decode(ivB64, android.util.Base64.NO_WRAP)
        val ciphertext = android.util.Base64.decode(ciphertextB64, android.util.Base64.NO_WRAP)
        val cipher     = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
