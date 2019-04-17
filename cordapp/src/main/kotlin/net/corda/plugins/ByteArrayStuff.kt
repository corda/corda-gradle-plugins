package net.corda.plugins

import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*
import javax.xml.bind.DatatypeConverter

open class OpaqueBytes(bytes: ByteArray) : ByteSequence(bytes, 0, bytes.size) {
    companion object {
        /**
         * Create [OpaqueBytes] from a sequence of [Byte] values.
         */
        @JvmStatic
        fun of(vararg b: Byte) = OpaqueBytes(byteArrayOf(*b))
    }

    init {
        require(bytes.isNotEmpty()) { "Byte Array must not be empty" }
    }

    /**
     * The bytes are always cloned so that this object becomes immutable. This has been done
     * to prevent tampering with entities such as [net.corda.core.crypto.SecureHash] and [net.corda.core.contracts.PrivacySalt], as well as
     * preserve the integrity of our hash constants [net.corda.core.crypto.SecureHash.zeroHash] and [net.corda.core.crypto.SecureHash.allOnesHash].
     *
     * Cloning like this may become a performance issue, depending on whether or not the JIT
     * compiler is ever able to optimise away the clone. In which case we may need to revisit
     * this later.
     */
    final override val bytes: ByteArray = bytes
        get() = field.clone()
}

sealed class ByteSequence(private val _bytes: ByteArray, val offset: Int, val size: Int) : Comparable<ByteSequence> {
    /**
     * The underlying bytes.  Some implementations may choose to make a copy of the underlying [ByteArray] for
     * security reasons.  For example, [OpaqueBytes].
     */
    abstract val bytes: ByteArray

    /** Returns a [ByteArrayInputStream] of the bytes. */
    fun open() = ByteArrayInputStream(_bytes, offset, size)

    /**
     * Create a sub-sequence of this sequence. A copy of the underlying array may be made, if a subclass overrides
     * [bytes] to do so, as [OpaqueBytes] does.
     *
     * @param offset The offset within this sequence to start the new sequence. Note: not the offset within the backing array.
     * @param size The size of the intended sub sequence.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun subSequence(offset: Int, size: Int): ByteSequence {
        // Intentionally use bytes rather than _bytes, to mirror the copy-or-not behaviour of that property.
        return if (offset == 0 && size == this.size) this else of(bytes, this.offset + offset, size)
    }

    companion object {
        /**
         * Construct a [ByteSequence] given a [ByteArray] and optional offset and size, that represents that potentially
         * sub-sequence of bytes.
         */
        @JvmStatic
        @JvmOverloads
        fun of(bytes: ByteArray, offset: Int = 0, size: Int = bytes.size): ByteSequence {
            return OpaqueBytesSubSequence(bytes, offset, size)
        }
    }

    /**
     * Take the first n bytes of this sequence as a sub-sequence.  See [subSequence] for further semantics.
     */
    fun take(n: Int): ByteSequence = subSequence(0, n)

    /**
     * A new read-only [ByteBuffer] view of this sequence or part of it.
     * If [start] or [end] are negative then [IllegalArgumentException] is thrown, otherwise they are clamped if necessary.
     * This method cannot be used to get bytes before [offset] or after [offset]+[size], and never makes a new array.
     */
    fun slice(start: Int = 0, end: Int = size): ByteBuffer {
        require(start >= 0) { "Starting index must be greater than or equal to 0" }
        require(end >= 0) { "End index must be greater or equal to 0" }
        val clampedStart = Math.min(start, size)
        val clampedEnd = Math.min(end, size)
        return ByteBuffer.wrap(_bytes, offset + clampedStart, Math.max(0, clampedEnd - clampedStart)).asReadOnlyBuffer()
    }

    /** Write this sequence to an [OutputStream]. */
    fun writeTo(output: OutputStream) = output.write(_bytes, offset, size)

    /** Write this sequence to a [ByteBuffer]. */
    fun putTo(buffer: ByteBuffer): ByteBuffer = buffer.put(_bytes, offset, size)

    /**
     * Copy this sequence, complete with new backing array.  This can be helpful to break references to potentially
     * large backing arrays from small sub-sequences.
     */
    fun copy(): ByteSequence = of(copyBytes())

    /** Same as [copy] but returns just the new byte array. */
    fun copyBytes(): ByteArray = _bytes.copyOfRange(offset, offset + size)

    /**
     * Compare byte arrays byte by byte.  Arrays that are shorter are deemed less than longer arrays if all the bytes
     * of the shorter array equal those in the same position of the longer array.
     */
    override fun compareTo(other: ByteSequence): Int {
        val min = minOf(this.size, other.size)
        val thisBytes = this._bytes
        val otherBytes = other._bytes
        // Compare min bytes
        for (index in 0 until min) {
            val unsignedThis = java.lang.Byte.toUnsignedInt(thisBytes[this.offset + index])
            val unsignedOther = java.lang.Byte.toUnsignedInt(otherBytes[other.offset + index])
            if (unsignedThis != unsignedOther) {
                return Integer.signum(unsignedThis - unsignedOther)
            }
        }
        // First min bytes is the same, so now resort to size.
        return Integer.signum(this.size - other.size)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteSequence) return false
        if (this.size != other.size) return false
        return subArraysEqual(this._bytes, this.offset, this.size, other._bytes, other.offset)
    }

    private fun subArraysEqual(a: ByteArray, aOffset: Int, length: Int, b: ByteArray, bOffset: Int): Boolean {
        var bytesRemaining = length
        var aPos = aOffset
        var bPos = bOffset
        while (bytesRemaining-- > 0) {
            if (a[aPos++] != b[bPos++]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        val thisBytes = _bytes
        var result = 1
        for (index in offset until (offset + size)) {
            result = 31 * result + thisBytes[index]
        }
        return result
    }

    override fun toString(): String = "[${DatatypeConverter.printHexBinary(copyBytes())}]"
}

class OpaqueBytesSubSequence(override val bytes: ByteArray, offset: Int, size: Int) : ByteSequence(bytes, offset, size) {
    init {
        require(offset >= 0 && offset < bytes.size) { "Offset must be greater than or equal to 0, and less than the size of the backing array" }
        require(size >= 0 && offset + size <= bytes.size) { "Sub-sequence size must be greater than or equal to 0, and less than the size of the backing array" }
    }
}

sealed class SecureHash(bytes: ByteArray) : OpaqueBytes(bytes) {
    /** SHA-256 is part of the SHA-2 hash function family. Generated hash is fixed size, 256-bits (32-bytes). */
    class SHA256(bytes: ByteArray) : SecureHash(bytes) {
        init {
            require(bytes.size == 32) { "Invalid hash size, must be 32 bytes" }
        }
    }

    /**
     * Convert the hash value to an uppercase hexadecimal [String].
     */
    override fun toString(): String = bytes.toHexString()

    /**
     * Returns the first [prefixLen] hexadecimal digits of the [SecureHash] value.
     * @param prefixLen The number of characters in the prefix.
     */
    fun prefixChars(prefixLen: Int = 6) = toString().substring(0, prefixLen)

    /**
     * Append a second hash value to this hash value, and then compute the SHA-256 hash of the result.
     * @param other The hash to append to this one.
     */
    fun hashConcat(other: SecureHash) = (this.bytes + other.bytes).sha256()

    // Like static methods in Java, except the 'companion' is a singleton that can have state.
    companion object {
        /**
         * Converts a SHA-256 hash value represented as a hexadecimal [String] into a [SecureHash].
         * @param str A sequence of 64 hexadecimal digits that represents a SHA-256 hash value.
         * @throws IllegalArgumentException The input string does not contain 64 hexadecimal digits, or it contains incorrectly-encoded characters.
         */
        @JvmStatic
        fun parse(str: String?): SHA256 {
            return str?.toUpperCase()?.parseAsHex()?.let {
                when (it.size) {
                    32 -> SHA256(it)
                    else -> throw IllegalArgumentException("Provided string is ${it.size} bytes not 32 bytes in hex: $str")
                }
            } ?: throw IllegalArgumentException("Provided string is null")
        }

        /**
         * Computes the SHA-256 hash value of the [ByteArray].
         * @param bytes The [ByteArray] to hash.
         */
        @JvmStatic
        fun sha256(bytes: ByteArray) = SHA256(MessageDigest.getInstance("SHA-256").digest(bytes))

        /**
         * Computes the SHA-256 hash of the [ByteArray], and then computes the SHA-256 hash of the hash.
         * @param bytes The [ByteArray] to hash.
         */
        @JvmStatic
        fun sha256Twice(bytes: ByteArray) = sha256(sha256(bytes).bytes)

        /**
         * Computes the SHA-256 hash of the [String]'s UTF-8 byte contents.
         * @param str [String] whose UTF-8 contents will be hashed.
         */
        @JvmStatic
        fun sha256(str: String) = sha256(str.toByteArray())

        /**
         * Generates a random SHA-256 value.
         */
        @JvmStatic
        fun randomSHA256() = {
            UUID.randomUUID().toString().toByteArray().sha256()
        }

        /**
         * A SHA-256 hash value consisting of 32 0x00 bytes.
         * This field provides more intuitive access from Java.
         */
        @JvmField
        val zeroHash: SHA256 = SecureHash.SHA256(ByteArray(32) { 0.toByte() })

        /**
         * A SHA-256 hash value consisting of 32 0x00 bytes.
         * This function is provided for API stability.
         */
        @Suppress("Unused")
        fun getZeroHash(): SHA256 = zeroHash

        /**
         * A SHA-256 hash value consisting of 32 0xFF bytes.
         * This field provides more intuitive access from Java.
         */
        @JvmField
        val allOnesHash: SHA256 = SecureHash.SHA256(ByteArray(32) { 255.toByte() })

        /**
         * A SHA-256 hash value consisting of 32 0xFF bytes.
         * This function is provided for API stability.
         */
        @Suppress("Unused")
        fun getAllOnesHash(): SHA256 = allOnesHash
    }

    // In future, maybe SHA3, truncated hashes etc.
}

/**
 * Compute the SHA-256 hash for the contents of the [ByteArray].
 */
fun ByteArray.sha256(): SecureHash.SHA256 = SecureHash.sha256(this)

/**
 * Compute the SHA-256 hash for the contents of the [OpaqueBytes].
 */
fun OpaqueBytes.sha256(): SecureHash.SHA256 = SecureHash.sha256(this.bytes)

fun ByteArray.sequence(offset: Int = 0, size: Int = this.size) = ByteSequence.of(this, offset, size)

/**
 * Converts this [ByteArray] into a [String] of hexadecimal digits.
 */
fun ByteArray.toHexString(): String = DatatypeConverter.printHexBinary(this)

/**
 * Converts this [String] of hexadecimal digits into a [ByteArray].
 * @throws IllegalArgumentException if the [String] contains incorrectly-encoded characters.
 */
fun String.parseAsHex(): ByteArray = DatatypeConverter.parseHexBinary(this)