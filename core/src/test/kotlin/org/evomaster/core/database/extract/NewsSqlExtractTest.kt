package org.evomaster.core.database.extract

import org.evomaster.client.java.controller.internal.db.SchemaExtractor
import org.evomaster.client.java.controller.api.dto.database.schema.DatabaseType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class NewsSqlExtractTest : ExtractTestBase() {

    override fun getSchemaLocation() = "/sql_schema/news.sql"


    @Test
    fun testCreateAndExtract() {

        val schema = SchemaExtractor.extract(connection)

        assertNotNull(schema)

        assertAll(Executable { assertEquals("public", schema.name.toLowerCase()) },
                Executable { assertEquals(DatabaseType.H2, schema.databaseType) },
                Executable { assertEquals(1, schema.tables.size) },
                Executable { assertTrue(schema.tables.any { it.name == "NEWS_ENTITY" }) }
        )

    }


}