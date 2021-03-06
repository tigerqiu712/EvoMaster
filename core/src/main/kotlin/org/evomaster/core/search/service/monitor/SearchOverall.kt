package org.evomaster.core.search.service

import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual


/**
 *  @decription This is used to save final archive of search and last evaluated individual
 *
 */
open class SearchOverall<T> where T : Individual{

    val stoppingCriterion:String
    val idMapper : IdMapper

    //val actionCluster: MutableList<Action> = mutableListOf()
    val numOfEvaluations: Int
    val finalPopulations = mutableMapOf<Int, MutableList<EvaluatedIndividual<T>>>()

    val startTime : Long
    val evalIndividual : EvaluatedIndividual<T>

    constructor(_stoppingCriterion:String, num : Int, _ind : T, _eval : EvaluatedIndividual<*>, _archive : Archive<*>, _idMaper : IdMapper, _startTime : Long){
        this.startTime = _startTime
        this.stoppingCriterion = _stoppingCriterion
        this.evalIndividual = _eval as EvaluatedIndividual<T>

        this.numOfEvaluations = num
        for(entries in _archive.getSnapshotOfBestIndividuals()){
            finalPopulations.put(entries.key, mutableListOf())
            for(v in entries.value){
                finalPopulations.getValue(entries.key).add((v as EvaluatedIndividual<T>).copy())
            }

        }
        this.idMapper = _idMaper
    }

}