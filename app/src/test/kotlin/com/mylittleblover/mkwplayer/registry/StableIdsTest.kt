package com.mylittleblover.mkwplayer.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StableIdsTest {

    private val archive = ArchiveId("a".repeat(64))

    @Test
    fun `archive id rejects short hash`() {
        assertFailsWith<IllegalArgumentException> { ArchiveId("abc") }
    }

    @Test
    fun `archive id rejects uppercase hex`() {
        assertFailsWith<IllegalArgumentException> { ArchiveId("A".repeat(64)) }
    }

    @Test
    fun `entry id rejects negative ordinal`() {
        assertFailsWith<IllegalArgumentException> { EntryId(archive, -1, 100) }
    }

    @Test
    fun `entry id rejects zero offset`() {
        assertFailsWith<IllegalArgumentException> { EntryId(archive, 0, 0) }
    }

    @Test
    fun `entry keys differ by ordinal`() {
        val a = EntryId(archive, 1, 200)
        val b = EntryId(archive, 2, 200)
        assertNotEquals(a.stableKey(), b.stableKey())
    }

    @Test
    fun `entry keys are stable across construction`() {
        val a = EntryId(archive, 464, 868512)
        val b = EntryId(archive, 464, 868512)
        assertEquals(a.stableKey(), b.stableKey())
    }

    /**
     * The whole point of positional identity: two entries sharing one asset name
     * must stay two identities. Real archives do this 24 times in M_Bgm alone.
     */
    @Test
    fun `same name on different entries never collapses`() {
        val first = EntryId(archive, 1, 232)
        val second = EntryId(archive, 8, 6544)
        assertNotEquals(first.stableKey(), second.stableKey())
    }

    @Test
    fun `marker key is unique per index within an entry`() {
        val entry = EntryId(archive, 464, 868512)
        val m0 = MarkerId(entry, 0, 2005, 637714)
        val m1 = MarkerId(entry, 1, 2004, 884571)
        assertNotEquals(m0.stableKey(), m1.stableKey())
    }

    @Test
    fun `marker key does not embed an unverified outro name`() {
        val entry = EntryId(archive, 464, 868512)
        val marker = MarkerId(entry, 0, 2005, 637714)
        val key = marker.stableKey()
        assertTrue("Outro" !in key, "identity must not depend on an unconfirmed name: $key")
    }

    @Test
    fun `marker rejects negative position`() {
        val entry = EntryId(archive, 0, 100)
        assertFailsWith<IllegalArgumentException> { MarkerId(entry, 0, 1, -5) }
    }

    @Test
    fun `resource slug must be lowercase ascii`() {
        assertFailsWith<IllegalArgumentException> {
            ResourceId(ResourceKind.COURSE, "Coaster")
        }
    }

    @Test
    fun `resource key includes kind`() {
        val a = ResourceId(ResourceKind.COURSE, "coaster")
        val b = ResourceId(ResourceKind.JUKEBOX, "coaster")
        assertNotEquals(a.stableKey(), b.stableKey())
    }

    @Test
    fun `distinct kinds never collide on one slug`() {
        val keys = ResourceKind.entries.map { ResourceId(it, "same") }.map { it.stableKey() }
        assertEquals(keys.size, keys.toSet().size)
    }
}
