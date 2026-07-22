package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `userDto parses roles and page_grants`() {
        val payload = """
            {"id":"u1","nik":"123","email":"a@b.c","name":"Test","role":"kasir",
             "roles":["kasir","pdi"],
             "page_grants":[{"prefix":"/dashboard/discount-approval","label":"Approval Diskon"}]}
        """.trimIndent()
        val user = json.decodeFromString(UserDto.serializer(), payload)
        assertEquals(listOf("kasir", "pdi"), user.roles)
        assertEquals("/dashboard/discount-approval", user.pageGrants.single().prefix)
    }

    @Test
    fun `userDto tolerates missing roles`() {
        val user = json.decodeFromString(UserDto.serializer(),
            """{"id":"u1","nik":"1","email":"a@b.c","name":"T","role":"sales"}""")
        assertEquals(emptyList<String>(), user.roles)
        assertEquals(emptyList<PageGrantDto>(), user.pageGrants)
    }
}
