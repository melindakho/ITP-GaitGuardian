package com.example.gaitguardian.data.roomDatabase.tug

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TugAnalysisDao {
// DAO will store all the SQL queries, referenced from Chen Kan jetpackArchTest repo

    @Query("SELECT * FROM tug_analysis_table")
    fun getAllAnalysis(): Flow<List<TUGAnalysis>>  // Room does not support StateFlow
    @Insert
    suspend fun insertNewTUGAnalysis(tugAnalysis: TUGAnalysis)

    // For Updating Result Card
    @Query(
        """
        SELECT a.* FROM tug_analysis_table a
        INNER JOIN tug_assessment_table t ON t.testId = a.testId
        ORDER BY t.dateTime DESC
        LIMIT 1
        """
    )
    suspend fun getLatestTugAnalysis(): TUGAnalysis?

    @Query(
        """
        SELECT a.* FROM tug_analysis_table a
        INNER JOIN tug_assessment_table t ON t.testId = a.testId
        ORDER BY t.dateTime DESC
        LIMIT 1
        """
    )
    fun getLatestTugAnalysisFlow(): Flow<TUGAnalysis?>

    // Get specific analysis by ID
    @Query("SELECT * FROM tug_analysis_table WHERE testId = :analysisId")
    suspend fun getTugAnalysisById(analysisId: String): TUGAnalysis?

    //TODO: Update Result Card with this
    @Query(
        """
        SELECT a.timeTaken FROM tug_analysis_table a
        INNER JOIN tug_assessment_table t ON t.testId = a.testId
        ORDER BY t.dateTime DESC
        LIMIT 2
        """
    )
    suspend fun getLatestTwoTimes(): List<Double>

    @Query("SELECT sitToStand, walkFromChair, turnFirst, walkToChair, turnSecond, standToSit FROM tug_analysis_table WHERE testId = :id ")
    suspend fun getSubtaskById(id: String): subtaskDuration

    // REMOVE ALL
    @Query("DELETE FROM tug_analysis_table")
    suspend fun removeAllTugAnalysis()
    @Query("DELETE FROM sqlite_sequence WHERE name= 'tug_analysis_table'")
    suspend fun removeAllTugAnalysisId()
}