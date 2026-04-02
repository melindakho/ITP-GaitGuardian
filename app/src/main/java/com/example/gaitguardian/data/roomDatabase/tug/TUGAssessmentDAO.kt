package com.example.gaitguardian.data.roomDatabase.tug

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TugDao {
// DAO will store all the SQL queries, referenced from Chen Kan jetpackArchTest repo

    @Query("SELECT * FROM tug_assessment_table")
    fun getAllTUGVideos(): Flow<List<TUGAssessment>>  // Room does not support StateFlow
    @Insert
    suspend fun insertNewTUGAssessment(tugAssessment: TUGAssessment)

    @Query("SELECT * FROM tug_assessment_table WHERE testId = :id")
    suspend fun getAssessmentById(id: String): TUGAssessment?

    @Query("UPDATE tug_assessment_table SET watchStatus = :watchStatus, notes = :notes WHERE testId = :id")
    suspend fun updateClinicianReview(id: String, watchStatus: Boolean, notes: String)

    @Query("UPDATE tug_assessment_table SET watchStatus = :watchStatus WHERE testId = :id")
    suspend fun multiSelectMarkAsReviewed(id: String, watchStatus: Boolean)

    @Query("UPDATE tug_assessment_table SET videoDuration = :videoDuration WHERE testId = :id")
    suspend fun updateVideoDuration(id: String, videoDuration: Float)

    // To be used in PatientViewModel
    @Query(" SELECT videoDuration FROM tug_assessment_table ORDER BY dateTime DESC LIMIT 2")
    suspend fun getLatestTwoDurations(): List<Float>

    @Query("UPDATE tug_assessment_table SET updateMedication = :medication WHERE testId = (SELECT testId FROM tug_assessment_table ORDER BY dateTime DESC LIMIT 1)")
    suspend fun updateOnMedicationStatus(medication: Boolean)

    @Query("SELECT * FROM tug_assessment_table ORDER BY dateTime DESC LIMIT 1")
    suspend fun getLatestAssessment(): TUGAssessment?

    @Query("DELETE FROM tug_assessment_table WHERE testId = (SELECT testId FROM tug_assessment_table ORDER BY dateTime DESC LIMIT 1)")
    suspend fun removeLastInsertedAssessment()

    @Query("DELETE FROM tug_assessment_table")
    suspend fun removeAllAssessment()
    @Query("DELETE FROM sqlite_sequence WHERE name= 'tug_assessment_table'")
    suspend fun removeAllAssessmentId()
}