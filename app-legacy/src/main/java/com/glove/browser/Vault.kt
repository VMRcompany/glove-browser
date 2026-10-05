package com.glove.browser

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Vault {
    private const val ALIAS = "glove.vault"
    private const val FILE = "vault.bin"
    private const val NEVER = "vault-never"
    private const val SOFT_KEY = "vault-soft-key"

    data class Entry(val origin: String, val username: String, val password: String)

    private fun key(context: Context): SecretKey {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey()
        }
        val prefs = context.getSharedPreferences(SOFT_KEY, Context.MODE_PRIVATE)
        val encoded = prefs.getString("key", null)
        if (encoded != null) {
            return SecretKeySpec(Base64.decode(encoded, Base64.DEFAULT), "AES")
        }
        val raw = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("key", Base64.encodeToString(raw, Base64.NO_WRAP)).apply()
        return SecretKeySpec(raw, "AES")
    }

    private fun read(context: Context): MutableList<Entry> {
        val file = context.filesDir.resolve(FILE)
        if (!file.exists()) return mutableListOf()
        return try {
            val bytes = file.readBytes()
            val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (bytes.size < 13) return mutableListOf()
                val iv = bytes.copyOfRange(0, 12)
                val body = bytes.copyOfRange(12, bytes.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(context), GCMParameterSpec(128, iv))
                String(cipher.doFinal(body), Charsets.UTF_8)
            } else {
                if (bytes.size < 17) return mutableListOf()
                val iv = bytes.copyOfRange(0, 16)
                val body = bytes.copyOfRange(16, bytes.size)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, key(context), IvParameterSpec(iv))
                String(cipher.doFinal(body), Charsets.UTF_8)
            }
            val array = JSONArray(text)
            val list = mutableListOf<Entry>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(Entry(item.getString("origin"), item.optString("username"), item.getString("password")))
            }
            list
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun write(context: Context, list: List<Entry>) {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(JSONObject().put("origin", entry.origin).put("username", entry.username).put("password", entry.password))
        }
        val plain = array.toString().toByteArray(Charsets.UTF_8)
        val payload = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(context))
            cipher.iv + cipher.doFinal(plain)
        } else {
            val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key(context), IvParameterSpec(iv))
            iv + cipher.doFinal(plain)
        }
        context.filesDir.resolve(FILE).writeBytes(payload)
    }

    fun find(context: Context, origin: String) = read(context).filter { it.origin == origin }

    fun save(context: Context, origin: String, username: String, password: String) {
        if (origin.isBlank() || password.isBlank()) return
        val list = read(context).filterNot { it.origin == origin && it.username == username }.toMutableList()
        list.add(Entry(origin, username, password))
        write(context, list)
        val never = neverSet(context).toMutableSet()
        never.remove(origin)
        context.getSharedPreferences(NEVER, Context.MODE_PRIVATE).edit().putStringSet("origins", never).apply()
    }

    fun delete(context: Context, origin: String, username: String) {
        write(context, read(context).filterNot { it.origin == origin && it.username == username })
    }

    fun all(context: Context) = read(context)

    fun never(context: Context, origin: String) {
        val set = neverSet(context).toMutableSet()
        set.add(origin)
        context.getSharedPreferences(NEVER, Context.MODE_PRIVATE).edit().putStringSet("origins", set).apply()
    }

    fun blocked(context: Context, origin: String) = neverSet(context).contains(origin)

    private fun neverSet(context: Context) =
        context.getSharedPreferences(NEVER, Context.MODE_PRIVATE).getStringSet("origins", emptySet()) ?: emptySet()

    const val PAGE = """
      (function () {
        if (window.__gloveVault) return;
        window.__gloveVault = true;
        function username(form, pass) {
          var inputs = Array.prototype.slice.call(form.querySelectorAll("input"));
          var named = inputs.filter(function (input) {
            var type = (input.type || "").toLowerCase();
            return (type === "email" || type === "text" || type === "tel") && (input.compareDocumentPosition(pass) & 4);
          });
          return named.length ? named[named.length - 1] : null;
        }
        function report(form) {
          var pass = form.querySelector("input[type=password]");
          if (!pass || !pass.value || !window.GloveVault) return;
          var user = username(form, pass);
          window.GloveVault.offer(location.origin, user ? user.value : "", pass.value);
        }
        document.addEventListener("submit", function (event) {
          if (event.target && event.target.querySelector) report(event.target);
        }, true);
        document.addEventListener("change", function (event) {
          var input = event.target;
          if (!input || (input.type || "").toLowerCase() !== "password" || !input.form) return;
          report(input.form);
        }, true);
        function fill() {
          if (!window.GloveVault || !window.GloveVault.lookup) return;
          var raw = window.GloveVault.lookup(location.origin);
          if (!raw) return;
          var saved = JSON.parse(raw);
          if (!saved.length) return;
          var entry = saved[0];
          var pass = document.querySelector("input[type=password]");
          if (!pass || !pass.form) return;
          var user = username(pass.form, pass);
          if (user && !user.value && entry.username) user.value = entry.username;
          if (!pass.value && entry.password) pass.value = entry.password;
        }
        if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", fill);
        else fill();
      })();
    """
}
