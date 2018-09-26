package org.evomaster.core.database

import org.evomaster.clientJava.controllerApi.dto.database.operations.DatabaseCommandDto
import org.evomaster.clientJava.controllerApi.dto.database.operations.InsertionDto
import org.evomaster.clientJava.controllerApi.dto.database.operations.InsertionEntryDto
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.SqlForeignKeyGene
import org.evomaster.core.search.gene.SqlPrimaryKeyGene


object DbActionTransformer {

    fun transform(insertions: List<DbAction>) : DatabaseCommandDto{

        val list = mutableListOf<InsertionDto>()
        val previous = mutableListOf<Gene>()

        for (i in 0 until insertions.size) {

            val action = insertions[i]
            val insertion = InsertionDto().apply { targetTable = action.table.name }

            for (g in action.seeGenes()) {
                if (g is SqlPrimaryKeyGene) {
                    /*
                        If there is more than one primary key field, this
                        will be overridden.
                        But, as we need it only for automatically generated ones,
                        this shouldn't matter, as in that case there should be just 1.
                     */
                    insertion.id = g.uniqueId
                }

                if (!g.isPrintable()) {
                    continue
                }

                val entry = InsertionEntryDto()

                if (g is SqlForeignKeyGene) {
                    handleSqlForeignKey(g, previous, entry)
                } else if (g is SqlPrimaryKeyGene) {
                    val k = g.gene
                    if (k is SqlForeignKeyGene) {
                        handleSqlForeignKey(k, previous, entry)
                    } else {
                        entry.printableValue = g.getValueAsPrintableString()
                    }
                } else {
                    entry.printableValue = g.getValueAsPrintableString()
                }

                entry.variableName = g.getVariableName()

                insertion.data.add(entry)
            }

            list.add(insertion)
            previous.addAll(action.seeGenes())
        }

        val dto = DatabaseCommandDto().apply { this.insertions = list }

        return dto
    }

    private fun handleSqlForeignKey(
            g: SqlForeignKeyGene,
            previous: List<Gene>,
            entry: InsertionEntryDto
    ) {
        if (g.isReferenceToNonPrintable(previous)) {
            entry.foreignKeyToPreviouslyGeneratedRow = g.uniqueIdOfPrimaryKey
        } else {
            entry.printableValue = g.getValueAsPrintableString(previous)
        }
    }
}