package com.example.gaitguardian.data.roomDatabase.tug

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow

class TUGAssessmentRepository(private val tugDao: TugDao, private val tugAnalysisDao: TugAnalysisDao) {
    val allTUGAssessments: Flow<List<TUGAssessment>> = tugDao.getAllTUGVideos()
    val allTUGAnalysis: Flow<List<TUGAnalysis>> = tugAnalysisDao.getAllAnalysis()

    @WorkerThread
    suspend fun insert(tugAssessment: TUGAssessment) {
        tugDao.insertNewTUGAssessment(tugAssessment)
    }

    @WorkerThread
    suspend fun getAssessmentById(id: String): TUGAssessment? {
        return tugDao.getAssessmentById(id)
    }

    @WorkerThread
    suspend fun updateClinicianReview(id: String, watchStatus: Boolean, notes: String) {
        tugDao.updateClinicianReview(id, watchStatus, notes)
    }

    @WorkerThread
    suspend fun multiSelectMarkAsReviewed(id: String, watchStatus: Boolean) {
        tugDao.multiSelectMarkAsReviewed(id, watchStatus)
    }

    @WorkerThread
    suspend fun updateVideoDuration(id: String, videoDuration: Float) {
        tugDao.updateVideoDuration(id, videoDuration)
    }

    @WorkerThread
    suspend fun getLatestTwoDuration(): List<Float> {
        return tugDao.getLatestTwoDurations()
    }

    @WorkerThread
    suspend fun updateOnMedicationStatus(medication: Boolean) {
        tugDao.updateOnMedicationStatus(medication)
    }

    @WorkerThread
    suspend fun getLatestAssessment(): TUGAssessment? {
        return tugDao.getLatestAssessment()
    }

    @WorkerThread
    suspend fun removeLastInserted() {
        return tugDao.removeLastInsertedAssessment()
    }

    @WorkerThread
    suspend fun removeAllAssessments() {
//        return tugDao.removeAllAssessment()
        tugDao.removeAllAssessment()
        tugDao.removeAllAssessmentId()
    }

    @WorkerThread
    suspend fun removeAllTUGAnalysis() {
//        return tugAnalysisDao.removeAllTugAnalysis()
        tugAnalysisDao.removeAllTugAnalysis()
        tugAnalysisDao.removeAllTugAnalysisId()
    }
    // ML Analysis
//    @WorkerThread
//    suspend fun insertTugAnalysis(tugAnalysis: TUGAnalysis) {
//        tugAnalysisDao.insertNewTUGAnalysis(tugAnalysis)
//    }
    @WorkerThread
    suspend fun insertTugAnalysis(tugAnalysis: TUGAnalysis) {
        return tugAnalysisDao.insertNewTUGAnalysis(tugAnalysis)
    }
    @WorkerThread
    suspend fun getSubtaskById(id: String): subtaskDuration? {
        return tugAnalysisDao.getSubtaskById(id)
    }
    @WorkerThread
    suspend fun getLatestTugAnalysis(): TUGAnalysis? {
        return tugAnalysisDao.getLatestTugAnalysis()
    }

    @WorkerThread
    fun getLatestTugAnalysisFlow(): Flow<TUGAnalysis?> = tugAnalysisDao.getLatestTugAnalysisFlow()

    
    @WorkerThread
    suspend fun getTugAnalysisById(analysisId: String): TUGAnalysis? {
        return tugAnalysisDao.getTugAnalysisById(analysisId)
    }
    
    @WorkerThread
    suspend fun getLatestTwoTimes(): List<Double> {
        return tugAnalysisDao.getLatestTwoTimes()
    }


}