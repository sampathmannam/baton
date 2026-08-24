package com.baton.app.data.backup

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.1.0 (PM rating): the [DriveRestApi] tests. The
 * real Drive REST API is mocked via Ktor's
 * [MockEngine]; the test pins the happy path
 * (upload + list + download + delete) and the
 * error surfaces (401 / 403 / 404 / 500).
 *
 * **Why MockEngine, not a fake HTTP server.** A fake
 * HTTP server (e.g. MockWebServer) would round-trip
 * through the Ktor engine, exercising the real Ktor
 * HttpClient. MockEngine bypasses the engine and
 * serves canned responses directly — that's the
 * right shape for unit-testing the wrapper, not the
 * Ktor engine itself (Ktor has its own test suite).
 */
class DriveRestApiTest {

    private fun mockClient(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient =
        HttpClient(MockEngine(handler))

    @Test
    fun `uploadToAppFolder returns the file id from the response`() = runTest {
        val client = mockClient { request ->
            // The wrapper posts a multipart body. We
            // just acknowledge with a Drive-shaped
            // JSON response.
            respond(
                content = """{"id":"file-abc-123","name":"baton-backup.json.enc"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val api = DriveRestApi(client)
        val id = api.uploadToAppFolder(
            accessToken = "fake-token",
            fileName = "baton-backup.json.enc",
            content = "encrypted-blob".toByteArray(),
        )
        assertEquals("file-abc-123", id)
    }

    @Test
    fun `uploadToAppFolder throws on 401 unauthorized`() = runTest {
        val client = mockClient {
            respond(
                content = """{"error":{"code":401,"message":"Invalid token"}}""",
                status = HttpStatusCode.Unauthorized,
            )
        }
        val api = DriveRestApi(client)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                api.uploadToAppFolder(
                    accessToken = "stale-token",
                    fileName = "x.json.enc",
                    content = byteArrayOf(),
                )
            }
        }
        assertTrue(
            "the exception should mention the 401 status",
            (ex.message ?: "").contains("401"),
        )
    }

    @Test
    fun `listBackups parses the Drive v3 file list`() = runTest {
        val client = mockClient {
            respond(
                content = """
                {
                  "files": [
                    {
                      "id": "f-1",
                      "name": "baton-backup-20260824-150000.json.enc",
                      "size": "1024",
                      "createdTime": "2026-08-24T15:00:00.000Z"
                    },
                    {
                      "id": "f-2",
                      "name": "baton-backup-20260823-150000.json.enc",
                      "size": "2048",
                      "createdTime": "2026-08-23T15:00:00.000Z"
                    }
                  ]
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val api = DriveRestApi(client)
        val files = api.listBackups(accessToken = "fake-token")
        assertEquals(2, files.size)
        assertEquals("f-1", files[0].id)
        assertEquals("baton-backup-20260824-150000.json.enc", files[0].name)
        assertEquals(1024L, files[0].sizeBytes)
        assertTrue("createdTime should be parsed into epoch millis", files[0].createdTimeMs > 0L)
    }

    @Test
    fun `listBackups returns an empty list when there are no files`() = runTest {
        val client = mockClient {
            respond(
                content = """{"files":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val api = DriveRestApi(client)
        val files = api.listBackups(accessToken = "fake-token")
        assertEquals(emptyList<DriveRestApi.DriveFile>(), files)
    }

    @Test
    fun `downloadFile returns the response body as bytes`() = runTest {
        val expected = "the encrypted blob bytes".toByteArray()
        val client = mockClient { _ ->
            respond(
                content = ByteReadChannel(expected),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/octet-stream"),
            )
        }
        val api = DriveRestApi(client)
        val bytes = api.downloadFile(accessToken = "fake-token", fileId = "f-1")
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `deleteFile succeeds on 204 NoContent`() = runTest {
        val client = mockClient {
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
            )
        }
        val api = DriveRestApi(client)
        // No throw = success.
        api.deleteFile(accessToken = "fake-token", fileId = "f-1")
    }

    @Test
    fun `deleteFile is idempotent on 404 NotFound`() = runTest {
        // The wrapper treats 404 as success so a
        // re-delete of a file that was already gone
        // (e.g. from another device) is safe.
        val client = mockClient {
            respond(
                content = """{"error":{"code":404,"message":"File not found"}}""",
                status = HttpStatusCode.NotFound,
            )
        }
        val api = DriveRestApi(client)
        api.deleteFile(accessToken = "fake-token", fileId = "already-gone")
    }

    @Test
    fun `deleteFile throws on 500 InternalServerError`() = runTest {
        val client = mockEngine {
            respond(
                content = """{"error":{"code":500,"message":"Internal error"}}""",
                status = HttpStatusCode.InternalServerError,
            )
        }
        val api = DriveRestApi(client)
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                api.deleteFile(accessToken = "fake-token", fileId = "f-1")
            }
        }
    }

    private fun mockEngine(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient =
        HttpClient(MockEngine(handler))

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("byte $i", expected[i], actual[i])
        }
    }
}
