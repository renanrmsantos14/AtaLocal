package br.com.betinhos.atalocal.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.betinhos.atalocal.domain.MeetingStatus

class MeetingConverters {
    @TypeConverter fun fromStatus(value: MeetingStatus): String = value.name
    @TypeConverter fun toStatus(value: String): MeetingStatus = MeetingStatus.valueOf(value)
}

@Database(
    entities = [MeetingEntity::class, ProcessingJobEntity::class, TranscriptSegmentEntity::class, ModelInstallEntity::class, ArtifactEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(MeetingConverters::class)
abstract class AtaLocalDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun processingJobDao(): ProcessingJobDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
    abstract fun modelInstallDao(): ModelInstallDao
    abstract fun artifactDao(): ArtifactDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE transcript_segments ADD COLUMN editedText TEXT")
    }
}
