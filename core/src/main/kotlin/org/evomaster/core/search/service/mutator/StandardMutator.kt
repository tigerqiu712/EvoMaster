package org.evomaster.core.search.service.mutator

import org.evomaster.core.EMConfig.GeneMutationStrategy.ONE_OVER_N
import org.evomaster.core.EMConfig.GeneMutationStrategy.ONE_OVER_N_BIASED_SQL
import org.evomaster.core.Lazy
import org.evomaster.core.database.DbAction
import org.evomaster.core.database.DbActionUtils
import org.evomaster.core.search.Individual
import org.evomaster.core.search.Individual.GeneFilter.ALL
import org.evomaster.core.search.Individual.GeneFilter.NO_SQL
import org.evomaster.core.search.gene.*
import java.math.BigDecimal
import java.math.RoundingMode


class StandardMutator<T> : Mutator<T>() where T : Individual {

    /**
     * List where each element at position "i" has value "2^i"
     */
    private val intpow2 = (0..30).map { Math.pow(2.0, it.toDouble()).toInt() }

    private fun innerMutate(individual: T): T {

        val copy = individual.copy() as T

        if (individual.canMutateStructure() &&
                randomness.nextBoolean(config.structureMutationProbability) && config.maxTestSize > 1) {
            //usually, either delete an action, or add a new random one
            structureMutator.mutateStructure(copy)
            return copy
        }

        val allGenes = copy.seeGenes().flatMap { it.flatView() }

        val filterMutate = if (config.generateSqlDataWithSearch) ALL else NO_SQL
        val genesToMutate = copy.seeGenes(filterMutate).filter { it.isMutable() }

        if (genesToMutate.isEmpty()) {
            return copy
        }

        val filterN = when (config.geneMutationStrategy) {
            ONE_OVER_N -> ALL
            ONE_OVER_N_BIASED_SQL -> NO_SQL
        }

        val n = Math.max(1, copy.seeGenes(filterN).filter { it.isMutable() }.count())

        val p = 1.0 / n

        var mutated = false

        while (!mutated) { //no point in returning a copy that is not mutated

            for (gene in genesToMutate) {

                if (!randomness.nextBoolean(p)) {
                    continue
                }

                if (gene is DisruptiveGene<*> && !randomness.nextBoolean(gene.probability)) {
                    continue
                }

                mutateGene(gene, allGenes)

                mutated = true
            }
        }

        Lazy.assert {
            DbActionUtils.verifyForeignKeys(
                    individual.seeInitializingActions().filterIsInstance<DbAction>())
        }

        return copy

    }

    override fun mutate(individual: T): T {

        // First mutate the individual
        val mutatedIndividual = innerMutate(individual)

        // Second repair the initialization actions (if needed)
        mutatedIndividual.repairInitializationActions(randomness)

        // Check that the repair was successful
        Lazy.assert { mutatedIndividual.verifyInitializationActions() }

        return mutatedIndividual
    }

    private fun mutateGene(gene: Gene, all: List<Gene>) {

        when (gene) {
            is SqlForeignKeyGene -> handleSqlForeignKeyGene(gene, all)
            is DisruptiveGene<*> -> mutateGene(gene.gene, all)
            is OptionalGene -> handleOptionalGene(gene, all)
            is IntegerGene -> handleIntegerGene(gene)
            is DoubleGene -> handleDoubleGene(gene)
            is StringGene -> handleStringGene(gene, all)
            else -> gene.randomize(randomness, true, all)
        }
    }

    private fun handleSqlForeignKeyGene(gene: SqlForeignKeyGene, all: List<Gene>) {
        gene.randomize(randomness, true, all)
    }

    private fun handleStringGene(gene: StringGene, all: List<Gene>) {

        val p = randomness.nextDouble()
        val s = gene.value

        /*
            What type of mutations we do on Strings is strongly
            correlated on how we define the fitness functions.
            When dealing with equality, as we do left alignment,
            then it makes sense to prefer insertion/deletion at the
            end of the strings, and reward more "change" over delete/add
         */

        val others = all.flatMap { g -> g.flatView() }
                .filterIsInstance<StringGene>()
                .map { g -> g.value }
                .filter { it != gene.value }

        gene.value = when {
            //seeding: replace
            p < 0.02 && !others.isEmpty() -> {
                randomness.choose(others)
            }
            //change
            p < 0.8 && s.isNotEmpty() -> {
                val delta = getDelta(start = 6, end = 3)
                val sign = randomness.choose(listOf(-1, +1))
                val i = randomness.nextInt(s.length)
                val array = s.toCharArray()
                array[i] = s[i] + (sign * delta)
                String(array)
            }
            //delete last
            p < 0.9 && s.isNotEmpty() && s.length > gene.minLength -> {
                s.dropLast(1)
            }
            //append new
            s.length < gene.maxLength -> {
                if (s.isEmpty() || randomness.nextBoolean(0.8)) {
                    s + randomness.nextWordChar()
                } else {
                    val i = randomness.nextInt(s.length)
                    if (i == 0) {
                        randomness.nextWordChar() + s
                    } else {
                        s.substring(0, i) + randomness.nextWordChar() + s.substring(i, s.length)
                    }
                }
            }
            else -> {
                //do nothing
                s
            }
        }
    }


    private fun handleOptionalGene(gene: OptionalGene, all: List<Gene>) {
        if (!gene.isActive) {
            gene.isActive = true
        } else {

            if (randomness.nextBoolean(0.01)) {
                gene.isActive = false
            } else {
                mutateGene(gene.gene, all)
            }
        }
    }

    private fun handleDoubleGene(gene: DoubleGene) {
        //TODO min/max for Double

        gene.value = when (randomness.choose(listOf(0, 1, 2))) {
            //for small changes
            0 -> gene.value + randomness.nextGaussian()
            //for large jumps
            1 -> gene.value + (getDelta() * randomness.nextGaussian())
            //to reduce precision, ie chop off digits after the "."
            2 -> BigDecimal(gene.value).setScale(randomness.nextInt(15), RoundingMode.HALF_EVEN).toDouble()
            else -> throw IllegalStateException("Regression bug")
        }

    }


    private fun getDelta(
            range: Long = Long.MAX_VALUE,
            start: Int = intpow2.size,
            end: Int = 10
    ): Int {
        val maxIndex = apc.getExploratoryValue(start, end)

        var n = 0
        for (i in 0 until maxIndex) {
            n = i + 1
            if (intpow2[i] > range) {
                break
            }
        }

        //choose an i for 2^i modification
        val delta = randomness.chooseUpTo(intpow2, n)

        return delta
    }


    private fun handleIntegerGene(gene: IntegerGene) {
        Lazy.assert { gene.min < gene.max && gene.isMutable() }

        //check maximum range. no point in having a delta greater than such range
        val range: Long = gene.max.toLong() - gene.min.toLong()

        //choose an i for 2^i modification
        val delta = getDelta(range)

        val sign = when (gene.value) {
            gene.max -> -1
            gene.min -> +1
            else -> randomness.choose(listOf(-1, +1))
        }

        val res: Long = (gene.value.toLong()) + (sign * delta)

        gene.value = when {
            res > gene.max -> gene.max
            res < gene.min -> gene.min
            else -> res.toInt()
        }
    }
}